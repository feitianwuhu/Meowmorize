package com.meowmorize.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.meowmorize.core.model.AccentChoice

/**
 * 强调色。
 *
 * 预设取各家系统的主色（小米橙 / vivo 蓝 / 华为红 / OPPO 绿 / 苹果蓝），
 * 也可以自定义色相。`SYSTEM` 不覆盖原色板——柔角卡片原来的暖橙是设计的一部分。
 */
object MeowAccents {

    /** 预设（不含 SYSTEM / CUSTOM），供设置页画色点用。 */
    val presets: List<Pair<AccentChoice, Color>> = listOf(
        AccentChoice.XIAOMI to Color(0xFFFF6900),
        AccentChoice.VIVO to Color(0xFF415FFF),
        AccentChoice.HUAWEI to Color(0xFFCF0A2C),
        AccentChoice.OPPO to Color(0xFF17A05E),
        AccentChoice.APPLE to Color(0xFF0A84FF),
    )

    /** 自定义色相 → 颜色（固定较高饱和度与适中亮度，避免选到灰白看不出强调）。 */
    fun fromHue(hue: Float): Color = Color.hsv(hue.coerceIn(0f, 360f), 0.72f, 0.95f)

    /** 自定义颜色的色相，供滑杆回显。 */
    fun hueOf(color: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv[0]
    }

    /** 解析用户选择；返回 null 表示「不覆盖」。 */
    fun resolve(choice: AccentChoice, customArgb: Long): Color? = when (choice) {
        AccentChoice.SYSTEM -> null
        AccentChoice.CUSTOM -> Color(customArgb)
        else -> presets.firstOrNull { it.first == choice }?.second
    }

    /** 强调色上的文字色：亮色配黑，暗色配白。 */
    fun onColor(accent: Color): Color =
        if (accent.luminance() > 0.55f) Color(0xFF14161A) else Color.White
}
