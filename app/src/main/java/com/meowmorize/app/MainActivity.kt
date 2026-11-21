package com.meowmorize.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meowmorize.core.designsystem.theme.MeowAccents
import com.meowmorize.core.model.GlassTone
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
            val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
            val updatePrompt by appViewModel.updatePrompt.collectAsStateWithLifecycle()

            // 启动时按设置的开关检查一次更新（内部有 6 小时节流，关掉开关则完全不查）
            androidx.compose.runtime.LaunchedEffect(Unit) { appViewModel.checkUpdateOnStart() }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            val glassDark = when (appearance.glassTone) {
                GlassTone.SYSTEM -> isSystemInDarkTheme()
                GlassTone.LIGHT -> false
                GlassTone.DARK -> true
            }

            // 状态栏/导航栏图标颜色跟随主题，避免深色模式下的「白底白字」。
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    // 不要直接 `as Activity`：部分 ROM 会给 View 套 ContextThemeWrapper，
                    // 强转会在首帧抛 ClassCastException，表现为「点开就闪退」。
                    val window = view.context.findActivity()?.window ?: return@SideEffect
                    val controller = WindowCompat.getInsetsController(window, view)
                    val lightBars = if (appearance.style == AppStyle.GLASS) {
                        !glassDark
                    } else {
                        !darkTheme && appearance.style != AppStyle.TERMINAL
                    }
                    controller.isAppearanceLightStatusBars = lightBars
                    controller.isAppearanceLightNavigationBars = lightBars
                }
            }

            // 系统「移除动画」/动画时长倍数：0 时所有动效退化为瞬间完成，
            // 1 以下按比例缩短。读一次即可，运行中改动会在下次启动生效。
            val motionScale = androidx.compose.runtime.remember {
                runCatching {
                    android.provider.Settings.Global.getFloat(
                        contentResolver,
                        "animator_duration_scale",
                        1f,
                    )
                }.getOrDefault(1f)
            }

            androidx.compose.runtime.CompositionLocalProvider(
                com.meowmorize.core.designsystem.motion.LocalMotionScale provides motionScale,
            ) {
                val accent = MeowAccents.resolve(appearance.accentChoice, appearance.accentCustomArgb)

                MeowTheme(
                    style = appearance.style,
                    darkTheme = darkTheme,
                    accent = accent,
                    glassDark = glassDark,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    ) {
                        // 壁纸只在磨砂风格下出现；其它风格的 background 是不透明色。
                        // 先铺一层中性底色：壁纸还在下载时也不会闪白。
                        if (appearance.style == AppStyle.GLASS && appearance.wallpaperEnabled) {
                            androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (glassDark) {
                                            androidx.compose.ui.graphics.Color(0xFF101216)
                                        } else {
                                            androidx.compose.ui.graphics.Color(0xFFB9C0C9)
                                        },
                                    ),
                            )
                            WallpaperBackdrop(
                                file = appearance.wallpaperFile,
                                scale = appearance.wallpaperScale,
                                blurDp = appearance.wallpaperBlur,
                                scrim = appearance.wallpaperScrim.toFloat(),
                            )
                        }
                        MeowmorizeApp()
                    }

                    updatePrompt?.let { info ->
                        UpdateAvailableDialog(
                            info = info,
                            onDismiss = appViewModel::dismissUpdatePrompt,
                            onOpen = { url ->
                                runCatching {
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )
                    }
                }
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
