package com.meowmorize.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「终端仪表盘」风格色板（socpk / 极客湾语汇）。
 *
 * 约束：只允许四个功能色——冷蓝、暖橙、信号绿、中性灰。
 * 每个颜色必须对应一个语义（计划 / 完成 / 警告 / 基线），不做情绪化用色。
 */
object Terminal {
    // 深蓝黑：比纯黑轻，但比冷灰更有「仪器面板」的纵深感，长时间看不刺眼
    val Background = Color(0xFF0A1018)
    val Surface = Color(0xFF0F1722)
    val SurfaceVariant = Color(0xFF131C29)
    val SurfaceLow = Color(0xFF0C131D)
    val SurfaceHigh = Color(0xFF182333)
    val OnSurface = Color(0xFFE6EDF5)
    val OnSurfaceVariant = Color(0xFF8FA0B5)
    val Outline = Color(0xFF223144)

    // 中饱和霓虹：足够区分数据类别，但不会在暗底上「发光」
    val Blue = Color(0xFF4DA3FF)
    val Orange = Color(0xFFFF8A4C)
    val Green = Color(0xFF4FE0A8)
    val Gray = Color(0xFF7E8FA3)

    val Danger = Color(0xFFE5484D)
}

/** 图表专用的四个数据色，语义固定，不随主题明暗改变色相。 */
@Immutable
data class ChartPalette(
    /** 主数据序列（已排计划 / 主曲线）。 */
    val seriesA: Color = Terminal.Blue,
    /** 次数据序列（已完成 / 对照曲线）。 */
    val seriesB: Color = Terminal.Orange,
    /** 达成、健康状态。 */
    val positive: Color = Terminal.Green,
    /** 基线、未完成、弱化数据。 */
    val neutral: Color = Terminal.Gray,
    val grid: Color = Color(0xFF182431),
    val axis: Color = Color(0xFF243549),
)

val TerminalScheme = darkColorScheme(
    primary = Terminal.Blue,
    onPrimary = Color(0xFF04101F),
    primaryContainer = Color(0xFF13233A),
    onPrimaryContainer = Color(0xFFCFE0FF),
    secondary = Terminal.Orange,
    onSecondary = Color(0xFF1E0D00),
    secondaryContainer = Color(0xFF2A1808),
    onSecondaryContainer = Color(0xFFFFD9BE),
    tertiary = Terminal.Green,
    onTertiary = Color(0xFF00160C),
    background = Terminal.Background,
    onBackground = Terminal.OnSurface,
    surface = Terminal.Surface,
    onSurface = Terminal.OnSurface,
    surfaceVariant = Terminal.SurfaceVariant,
    onSurfaceVariant = Terminal.OnSurfaceVariant,
    outline = Terminal.Outline,
    outlineVariant = Terminal.Outline,
    error = Terminal.Danger,
    onError = Color(0xFF1E0000),
)

/** 仪表盘只保留极小的圆角：信息块的边界靠描边与留白，而不是柔软的形状。 */
val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(5.dp),
    medium = RoundedCornerShape(7.dp),
    large = RoundedCornerShape(9.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

/**
 * 终端风格字体层级：字号整体下压、字距略开（大写英文标签）与略收（数字），
 * 让信息密度上升的同时不显得拥挤。
 */
val TerminalTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    ),
)

/** 数字与单位使用等宽字体，保证列对齐；中文正文仍用系统无衬线。 */
val TerminalNumberFamily: FontFamily = FontFamily.Monospace
