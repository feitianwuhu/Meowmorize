package com.meowmorize.app

import android.app.Application
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
        appScope.launch { demoDataInitializer.initialize() }
    }
}
