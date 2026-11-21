package com.meowmorize.app

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meowmorize.core.designsystem.motion.MeowMotion
import com.meowmorize.core.designsystem.motion.motionMs
import com.meowmorize.core.designsystem.component.MeowBottomBar
import com.meowmorize.core.designsystem.component.MeowNavItem
import com.meowmorize.core.designsystem.theme.LocalAppStyle
import com.meowmorize.core.model.AppStyle
import com.meowmorize.feature.calendar.CalendarRoute
import com.meowmorize.feature.editor.EditorRoute
import com.meowmorize.feature.library.LibraryRoute
import com.meowmorize.feature.settings.SettingsRoute
import com.meowmorize.feature.today.ReviewRoute
import com.meowmorize.feature.today.TodayRoute

/** 路由表集中定义，避免字符串散落在各处。 */
object Routes {
    const val TODAY = "today"
    const val CALENDAR = "calendar"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"

    const val EDITOR = "editor?itemId={itemId}"
    const val REVIEW = "review/{itemId}"

    fun editor(itemId: Long? = null): String = "editor?itemId=${itemId ?: -1L}"
    fun review(itemId: Long): String = "review/$itemId"
}

private val bottomNavItems = listOf(
    MeowNavItem(Routes.TODAY, "今日", Icons.Rounded.WbSunny),
    MeowNavItem(Routes.CALENDAR, "日历", Icons.Rounded.CalendarMonth),
    MeowNavItem(Routes.LIBRARY, "任务库", Icons.AutoMirrored.Rounded.MenuBook),
    MeowNavItem(Routes.SETTINGS, "设置", Icons.Rounded.Tune),
)

private fun isDetailRoute(route: String?): Boolean =
    route == Routes.REVIEW || route == Routes.EDITOR

@Composable
fun MeowmorizeApp(modifier: Modifier = Modifier) {
    val terminalStyle = LocalAppStyle.current == AppStyle.TERMINAL
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 时长统一走令牌；系统关闭动画时（倍数 0）这里会退化成 0ms
    val enterMs = motionMs(MeowMotion.ENTER)
    val exitMs = motionMs(MeowMotion.EXIT)
    val tabMs = motionMs(if (terminalStyle) MeowMotion.PRESS else MeowMotion.FAST)
    val showBottomBar = currentRoute == Routes.TODAY ||
        currentRoute == Routes.CALENDAR ||
        currentRoute == Routes.LIBRARY ||
        currentRoute == Routes.SETTINGS

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            // 分两类动作：
            // · 底部栏切页（一天几十次）→ 只做极短交叉淡化，不加位移，否则显得慢
            // · 进入详情/编辑（偶尔）→ 从右侧轻推入，建立「它是从当前页展开的」空间关系
            enterTransition = {
                if (isDetailRoute(targetState.destination.route)) {
                    slideInHorizontally(
                        animationSpec = tween(enterMs, easing = MeowMotion.easeOut),
                    ) { it / 5 } + fadeIn(tween(enterMs))
                } else {
                    fadeIn(tween(tabMs))
                }
            },
            exitTransition = {
                // 推进详情时，下层保持不动：两层同时位移会像在互相推挤
                if (isDetailRoute(targetState.destination.route)) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(tabMs))
                }
            },
            popEnterTransition = {
                fadeIn(tween(exitMs))
            },
            popExitTransition = {
                if (isDetailRoute(initialState.destination.route)) {
                    slideOutHorizontally(
                        animationSpec = tween(exitMs, easing = MeowMotion.easeOut),
                    ) { it / 5 } + fadeOut(tween(exitMs))
                } else {
                    fadeOut(tween(tabMs))
                }
            },
        ) {
            composable(Routes.TODAY) {
                TodayRoute(
                    onOpenReview = { navController.navigate(Routes.review(it)) },
                    onEditTask = { navController.navigate(Routes.editor(it)) },
                    onCreateTask = { navController.navigate(Routes.editor()) },
                )
            }
            composable(Routes.CALENDAR) {
                CalendarRoute(onOpenTask = { navController.navigate(Routes.review(it)) })
            }
            composable(Routes.LIBRARY) {
                LibraryRoute(
                    onEditTask = { navController.navigate(Routes.editor(it)) },
                    onCreateTask = { navController.navigate(Routes.editor()) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRoute()
            }
            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.StringType
                        defaultValue = "-1"
                    },
                ),
            ) {
                EditorRoute(onDone = { navController.popBackStack() })
            }
            composable(
                route = Routes.REVIEW,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) {
                ReviewRoute(onBack = { navController.popBackStack() })
            }
        }

        if (showBottomBar) {
            MeowBottomBar(
                items = bottomNavItems,
                currentRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
            )
        }
    }
}
