package com.meowmorize.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 启动阶段记录。
 *
 * 用来定位「点开就闪退」：崩溃往往发生在首帧之前，应用内来不及弹任何界面。
 * 这里在每个关键阶段追加一行，并**立刻同步一份到「下载」目录**——
 * 手机不接电脑也能看到上次走到哪一步。
 *
 * 只写几百字节，不做日志框架：它不是运行时日志，是事故现场标记。
 */
object StartupTrace {

    const val FILE_NAME = "startup.log"

    /** 「下载」目录里那份可读副本的名字。 */
    private const val PUBLIC_NAME = "Meowmorize-启动记录.txt"

    private const val MAX_BYTES = 32 * 1024

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)

    @Volatile
    private var enabled = true

    fun mark(context: Context, stage: String) {
        if (!enabled) return
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (file.length() > MAX_BYTES) file.delete()
            val line = buildString {
                append(timeFormat.format(Date()))
                append("  ")
                append(stage)
                if (file.length() == 0L) {
                    append("  [")
                    append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append(", Android ").append(Build.VERSION.RELEASE)
                    append(", API ").append(Build.VERSION.SDK_INT)
                    append(", v").append(appVersion(context))
                    append(']')
                }
                append('\n')
            }
            file.appendText(line)
            // 正式包不做「每次启动都往下载目录写一份」：那是排障手段，不是产品行为。
            // 崩溃时 CrashLog 会把这份 trace 整段附进报告，该有的线索不会丢。
            if (isDebuggable(context)) PublicLogFile.publish(context, PUBLIC_NAME, file.readText())
        }.onFailure { enabled = false }
    }

    /** 记录一次异常，但不改变控制流。 */
    fun markError(context: Context, stage: String, error: Throwable) {
        if (!enabled) return
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            file.appendText(
                "${timeFormat.format(Date())}  !$stage: ${error.javaClass.name}: ${error.message}\n",
            )
            PublicLogFile.publish(context, PUBLIC_NAME, file.readText())
        }.onFailure { enabled = false }
    }

    private fun appVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
