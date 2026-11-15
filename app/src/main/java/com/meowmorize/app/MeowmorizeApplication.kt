package com.meowmorize.app

import android.app.Application
import android.os.Build
import java.io.File
import com.meowmorize.core.data.seed.DemoDataInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application 入口。
 *
 * 只做两件事：建立 Hilt 依赖图、触发首次启动的示例数据与今日计划生成。
 * 业务逻辑一律放在 domain / data 层，Application 保持「装配 + 启动」的单一职责。
 */
@HiltAndroidApp
class MeowmorizeApplication : Application() {

    @Inject
    lateinit var demoDataInitializer: DemoDataInitializer

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        appScope.launch { demoDataInitializer.initialize() }
    }

    /**
     * 把未捕获异常写进私有目录。
     *
     * 起因：有用户反馈在部分 ROM 上「点开即闪退」，而开发机没有对应设备。
     * 写盘比什么都实在——用户装一次、复现一次，就能把完整堆栈发回来，
     * 不必教人抓 logcat。异常仍然照常抛出，不改变崩溃行为。
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stamp = System.currentTimeMillis()
                val file = File(filesDir, "crash-$stamp.txt")
                file.writeText(
                    buildString {
                        appendLine("time=${java.util.Date(stamp)}")
                        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                        appendLine("android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("version=${packageManager.getPackageInfo(packageName, 0).versionName}")
                        appendLine("thread=${thread.name}")
                        appendLine()
                        append(error.stackTraceToString())
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
