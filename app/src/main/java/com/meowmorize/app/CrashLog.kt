package com.meowmorize.app

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 未捕获异常落盘。
 *
 * 安装点必须在 [android.app.Application.attachBaseContext] 里，而且要在 super 之前：
 * 要排查的正是「Application 都没起来就闪退」，放在 onCreate 里那类崩溃永远抓不到。
 *
 * ⚠️ 这里踩过一个坑，务必保留注释：
 * attachBaseContext 阶段 `context.applicationContext` 仍然是 **null**
 * （LoadedApk 要等 attach 返回后才给 mApplication 赋值）。早期版本存的就是
 * `applicationContext`，于是 appContext 恒为 null、写文件时直接 return，
 * 处理器装得整整齐齐却一个文件都没产出——现场因此被误判成「连应用代码都没跑」。
 * 现在改为「拿不到 applicationContext 就用传入的 Context 本身」。
 */
object CrashLog {

    @Volatile
    private var installed = false

    @Volatile
    private var appContext: Context? = null

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
            appContext = context.applicationContext ?: context
        }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread.name, error) }
            // 交回系统：这里绝不能吞异常，否则「应用停止运行」的提示也会被一起吞掉，
            // 用户只看到一闪而过，开发者什么线索都没有。
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(threadName: String, error: Throwable) {
        val context = appContext ?: return
        val stamp = System.currentTimeMillis()
        val name = "Meowmorize-crash-$stamp.txt"
        val report = buildString {
            appendLine("time=${timeFormat.format(Date(stamp))}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("version=${versionName(context)}")
            appendLine("thread=$threadName")
            appendLine()
            appendLine("--- startup trace ---")
            appendLine(readStartupTrace(context))
            appendLine("--- stack ---")
            append(error.stackTraceToString())
        }

        // 私有目录给 run-as，公共下载目录给「手机不接电脑」的现场
        runCatching { File(context.filesDir, name).writeText(report) }
        PublicLogFile.publish(context, name, report)
    }

    private fun readStartupTrace(context: Context): String =
        runCatching { File(context.filesDir, StartupTrace.FILE_NAME).readText() }
            .getOrDefault("(no trace)")

    private fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
}
