package com.meowmorize.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
    /** 强调色覆盖；null 表示沿用原色板。 */
    accent: Color? = null,
    /** GLASS 风格下的明暗（用户可选跟随系统 / 亮 / 暗）。 */
    glassDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val terminal = style == AppStyle.TERMINAL
    val glass = style == AppStyle.GLASS
    // 终端风格以近黑为底；磨砂风格按用户选择的明暗，其余跟随系统
    val dark = when {
        terminal -> true
        glass -> glassDark
        else -> darkTheme
    }

    val effectiveAccent = accent
    val colorScheme = when {
        terminal -> TerminalScheme.withAccent(effectiveAccent)
        glass -> glassScheme(dark = dark, accent = effectiveAccent ?: GlassFallbackAccent)
        darkTheme -> DarkScheme.withAccent(effectiveAccent)
        else -> LightScheme.withAccent(effectiveAccent)
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
        glass -> MeowExtraColors(
            success = if (dark) SuccessGreenDark else SuccessGreen,
            onSuccess = if (dark) Color(0xFF04291A) else Color.White,
            warning = WarningAmber,
            danger = if (dark) DangerRedDark else DangerRed,
            // 玻璃风格没有「实色容器」，低/高容器都用半透明白，叠在壁纸上
            containerLow = Color.White.copy(alpha = if (dark) 0.07f else 0.30f),
            containerHigh = Color.White.copy(alpha = if (dark) 0.12f else 0.46f),
            isDark = dark,
            chart = ChartPalette(
                seriesA = effectiveAccent ?: GlassFallbackAccent,
                seriesB = if (dark) AccentBlueDark else AccentBlue,
                positive = if (dark) SuccessGreenDark else SuccessGreen,
                neutral = if (dark) Color(0xFF8A9099) else Color(0xFFB4B2A9),
                grid = Color.White.copy(alpha = if (dark) 0.12f else 0.45f),
                axis = Color.White.copy(alpha = if (dark) 0.18f else 0.55f),
            ),
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
                seriesA = effectiveAccent ?: AccentOrangeDark,
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
                seriesA = effectiveAccent ?: AccentOrange,
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

/** 磨砂风格默认强调色（用户没选时的冷蓝，比暖橙更衬照片）。 */
internal val GlassFallbackAccent = Color(0xFF3B82F6)

/** 只替换强调色相关的槽位，其余沿用原色板。 */
private fun ColorScheme.withAccent(accent: Color?): ColorScheme {
    if (accent == null) return this
    val onAccent = MeowAccents.onColor(accent)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.18f),
        onPrimaryContainer = onAccent,
        inversePrimary = accent,
    )
}

/**
 * 磨砂玻璃配色。
 *
 * 关键点：surface / surfaceVariant 都是**半透明白**，`background` 是 Transparent。
 * 这样卡片、按钮、底栏不需要各自写玻璃效果——它们本来就用 surface，
 * 叠在壁纸上天然就是磨砂。Border 用极淡的白，靠 1px 亮边而不是投影塑造厚度。
 */
private fun glassScheme(dark: Boolean, accent: Color): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val onSurface = if (dark) Color(0xFFF4F5F7) else Color(0xFF14161A)
    val onSurfaceVariant = if (dark) Color(0xFFD5D9DF) else Color(0xFF464B53)
    val surfaceAlpha = if (dark) 0.14f else 0.58f
    val variantAlpha = if (dark) 0.09f else 0.40f
    val onAccent = MeowAccents.onColor(accent)
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.22f),
        onPrimaryContainer = onAccent,
        background = Color.Transparent,
        onBackground = onSurface,
        surface = Color.White.copy(alpha = surfaceAlpha),
        onSurface = onSurface,
        surfaceVariant = Color.White.copy(alpha = variantAlpha),
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = Color.Transparent,
        surfaceContainerLow = Color.White.copy(alpha = if (dark) 0.08f else 0.34f),
        surfaceContainer = Color.White.copy(alpha = if (dark) 0.11f else 0.44f),
        surfaceContainerHigh = Color.White.copy(alpha = if (dark) 0.15f else 0.52f),
        surfaceContainerHighest = Color.White.copy(alpha = if (dark) 0.19f else 0.60f),
        surfaceTint = accent,
        outline = Color.White.copy(alpha = if (dark) 0.24f else 0.60f),
        outlineVariant = Color.White.copy(alpha = if (dark) 0.13f else 0.35f),
        error = if (dark) DangerRedDark else DangerRed,
    )
}
