package com.meowmorize.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meowmorize.core.designsystem.theme.MeowTheme
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartupTrace.mark(this, "MainActivity.onCreate")

        // 个别 ROM 的 WindowInsets 实现会在 enableEdgeToEdge 里抛异常；
        // 沉浸式是「锦上添花」，不该成为启动失败的理由。
        runCatching { enableEdgeToEdge() }
            .onFailure { StartupTrace.markError(this, "enableEdgeToEdge", it) }

        setContent {
            // 首次组合成功 = 主题/依赖注入/资源都过了，这一笔很有价值
            androidx.compose.runtime.LaunchedEffect(Unit) {
                StartupTrace.mark(this@MainActivity, "first-composition")
            }
            val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
            val appStyle by appViewModel.appStyle.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // 状态栏/导航栏图标颜色跟随主题，避免深色模式下的「白底白字」。
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    // 不要直接 `as Activity`：部分 ROM 会给 View 套 ContextThemeWrapper，
                    // 强转会在首帧抛 ClassCastException，表现为「点开就闪退」。
                    val window = view.context.findActivity()?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    val lightBars = !darkTheme && appStyle != AppStyle.TERMINAL
                    controller.isAppearanceLightStatusBars = lightBars
                    controller.isAppearanceLightNavigationBars = lightBars
                }
            }

            MeowTheme(style = appStyle, darkTheme = darkTheme) {
                MeowmorizeApp()
            }
        }
    }
}

/** 沿 ContextWrapper 逐层解包，取出真正的 Activity；取不到就返回 null。 */
private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
