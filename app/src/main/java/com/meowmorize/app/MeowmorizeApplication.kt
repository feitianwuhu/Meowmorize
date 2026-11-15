package com.meowmorize.app

import android.app.Application
import android.content.Context
import com.meowmorize.core.data.seed.DemoDataInitializer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineExceptionHandler
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

    /**
     * 启动期的协程异常必须自己接住。
     *
     * 之前的写法只有 SupervisorJob：它只隔离「兄弟协程」，并不会吞掉异常——
     * 未捕获的异常仍会交给线程默认处理器，直接把进程干掉。
     * 于是「首次数据初始化失败」会被用户看成「应用一点就闪退」，实际上界面本可以正常打开。
     */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            StartupTrace.markError(this, "seed", error)
        },
    )

    /**
     * 应用代码里最早能执行的位置：Context 已挂上，能写文件。
     * 这一笔决定了「应用代码到底有没有开始执行」。
     *
     * 装处理器必须在 super 之前：super 内部（Hilt 基类、BaseContext 绑定）也可能抛，
     * 放在后面就等于给最想抓的那段崩溃留了个空档。
     */
    override fun attachBaseContext(base: Context?) {
        if (base != null) {
            CrashLog.install(base)
            StartupTrace.mark(base, "attachBaseContext")
        }
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        // Hilt 的依赖图在 super.onCreate 里构建，这一步抛异常会直接带走进程
        super.onCreate()
        StartupTrace.mark(this, "Application.onCreate(hilt ready)")
        appScope.launch {
            runCatching { demoDataInitializer.initialize() }
                .onFailure { StartupTrace.markError(this@MeowmorizeApplication, "seed", it) }
            StartupTrace.mark(this@MeowmorizeApplication, "seed.done")
        }
    }

}
