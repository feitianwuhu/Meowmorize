package com.meowmorize.app

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

@Composable
fun MeowmorizeApp(modifier: Modifier = Modifier) {
    val terminalStyle = LocalAppStyle.current == AppStyle.TERMINAL
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
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
            // 终端风格只保留极短的 dissolves（约 150ms），HyperOS 稍长一些
            enterTransition = {
                androidx.compose.animation.fadeIn(
                    androidx.compose.animation.core.tween(if (terminalStyle) 150 else 240),
                )
            },
            exitTransition = {
                androidx.compose.animation.fadeOut(
                    androidx.compose.animation.core.tween(if (terminalStyle) 120 else 200),
                )
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
