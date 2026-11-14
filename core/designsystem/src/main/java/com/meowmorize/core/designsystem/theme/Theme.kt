package com.meowmorize.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meowmorize.core.model.AppStyle

private val LightScheme = lightColorScheme(
    primary = AccentOrange,
    onPrimary = Color.White,
    primaryContainer = AccentOrangeContainer,
    onPrimaryContainer = Color(0xFF3D1400),
    secondary = AccentBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECFF),
    onSecondaryContainer = Color(0xFF0B2B63),
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = DangerRed,
    onError = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary = AccentOrangeDark,
    onPrimary = Color(0xFF2B1000),
    primaryContainer = AccentOrangeContainerDark,
    onPrimaryContainer = Color(0xFFFFD7C2),
    secondary = AccentBlueDark,
    onSecondary = Color(0xFF061A3D),
    secondaryContainer = Color(0xFF182848),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = SuccessGreenDark,
    onTertiary = Color(0xFF04291A),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DangerRedDark,
    onError = Color(0xFF2B0004),
)

/** 主色板之外的语义色，Material3 没有对应槽位，单独通过 CompositionLocal 提供。 */
@Immutable
data class MeowExtraColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val danger: Color,
    val containerLow: Color,
    val containerHigh: Color,
    val isDark: Boolean,
    val chart: ChartPalette = ChartPalette(),
)

val LocalMeowColors = staticCompositionLocalOf {
    MeowExtraColors(
        success = SuccessGreen,
        onSuccess = Color.White,
        warning = WarningAmber,
        danger = DangerRed,
        containerLow = LightSurfaceHigh,
        containerHigh = LightSurfaceVariant,
        isDark = false,
        chart = ChartPalette(
            seriesA = AccentOrange,
            seriesB = AccentBlue,
            positive = SuccessGreen,
            neutral = Color(0xFFB4B2A9),
            grid = Color(0xFFE8EAEE),
            axis = Color(0xFFD6D9DF),
        ),
    )
}

/** 当前界面风格，组件据此决定「渐变还是纯色」「圆角还是直角」。 */
val LocalAppStyle = staticCompositionLocalOf { AppStyle.SOFT_CARD }

/** 大圆角是 HyperOS 最直观的视觉特征之一。 */
val MeowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun MeowTheme(
    style: AppStyle = AppStyle.SOFT_CARD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val terminal = style == AppStyle.TERMINAL
    // 终端风格以近黑为底，本身就是深色界面，不再跟随浅色模式
    val dark = terminal || darkTheme

    val colorScheme = when {
        terminal -> TerminalScheme
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val extras = when {
        terminal -> MeowExtraColors(
            success = Terminal.Green,
            onSuccess = Color(0xFF00160C),
            warning = Terminal.Orange,
            danger = Terminal.Danger,
            containerLow = Terminal.SurfaceLow,
            containerHigh = Terminal.SurfaceHigh,
            isDark = true,
            chart = ChartPalette(),
        )
        darkTheme -> MeowExtraColors(
            success = SuccessGreenDark,
            onSuccess = Color(0xFF04291A),
            warning = WarningAmber,
            danger = DangerRedDark,
            containerLow = DarkSurfaceHigh,
            containerHigh = DarkSurfaceVariant,
            isDark = true,
            chart = ChartPalette(
                seriesA = AccentOrangeDark,
                seriesB = AccentBlueDark,
                positive = SuccessGreenDark,
                neutral = DarkOnSurfaceVariant,
                grid = Color(0xFF24272E),
                axis = Color(0xFF33373F),
            ),
        )
        else -> MeowExtraColors(
            success = SuccessGreen,
            onSuccess = Color.White,
            warning = WarningAmber,
            danger = DangerRed,
            containerLow = LightSurfaceHigh,
            containerHigh = LightSurfaceVariant,
            isDark = false,
            chart = ChartPalette(
                seriesA = AccentOrange,
                seriesB = AccentBlue,
                positive = SuccessGreen,
                neutral = Color(0xFFB4B2A9),
                grid = Color(0xFFE8EAEE),
                axis = Color(0xFFD6D9DF),
            ),
        )
    }

    CompositionLocalProvider(
        LocalMeowColors provides extras,
        LocalAppStyle provides style,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (terminal) TerminalTypography else MeowTypography,
            shapes = if (terminal) TerminalShapes else MeowShapes,
            content = content,
        )
    }
}
