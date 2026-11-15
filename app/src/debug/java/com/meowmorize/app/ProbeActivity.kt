package com.meowmorize.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 排障专用的「裸 Activity」。
 *
 * 刻意只继承 [Activity]、只用系统控件：不经过 Compose、不经过 Hilt、
 * 不碰导航。它要回答的是一个二选一的问题——
 *
 *   · 这个界面能打开 → 进程、Application、依赖图都是好的，问题在我们的 UI 层
 *   · 这个界面也闪退 → 问题发生在第一个 Activity 之前，跟我们画了什么无关
 *
 * 同时它把启动记录和崩溃报告直接显示在屏幕上，手机不接电脑也能读。
 * 只存在于 debug 变体，不会进正式包。
 */
class ProbeActivity : Activity() {

    private lateinit var output: TextView

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        output = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#E8EDF2"))
            setPadding(28, 28, 28, 28)
            setText(buildReport())
        }

        val refresh = Button(this).apply {
            text = "刷新"
            setOnClickListener {
                StartupTrace.mark(this@ProbeActivity, "probe.refresh")
                output.text = buildReport()
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#16202E"))
            addView(refresh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(
                ScrollView(this@ProbeActivity).apply { addView(output) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(root)

        StartupTrace.mark(this, "probe.activity")
        output.text = buildReport()
    }

    private fun buildReport(): String = buildString {
        appendLine("喵记 · 启动探针")
        appendLine("=".repeat(28))
        appendLine("机型 ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("系统 Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("包名 $packageName")
        appendLine("时间 ${timeFormat.format(Date())}")
        appendLine()

        val log = File(filesDir, StartupTrace.FILE_NAME)
        appendLine("── 启动记录 ──")
        appendLine(
            if (log.exists()) {
                runCatching { log.readText() }.getOrDefault("(读取失败)")
            } else {
                "(没有启动记录：应用代码可能根本没被执行到)"
            },
        )

        val crashes = filesDir.listFiles { f -> f.name.startsWith("Meowmorize-crash-") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
        appendLine()
        appendLine("── 崩溃报告 (${crashes.size}) ──")
        if (crashes.isEmpty()) {
            appendLine("(无)")
        } else {
            crashes.take(3).forEach { file ->
                appendLine()
                appendLine("### ${file.name}")
                appendLine(runCatching { file.readText() }.getOrDefault("(读取失败)"))
            }
        }
    }
}
