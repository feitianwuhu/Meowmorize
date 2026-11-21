package com.meowmorize.core.model

/**
 * 界面风格。
 *
 * 两套风格不是「同一套 UI 换皮」，而是两种信息组织方式：
 * - [HYPEROS]：生活化、圆角、情感化留白，适合日常打卡；
 * - [TERMINAL]：仪表盘式、近黑底、数据优先，装饰不承载信息就不出现。
 */
enum class AppStyle {
    /** 大圆角、柔和层次、暖橙强调——按视觉特征命名为「柔角卡片」。 */
    SOFT_CARD,

    /** 深蓝黑底、规格条与曲线——「终端仪表盘」。 */
    TERMINAL,

    /**
     * 每日壁纸 + 磨砂玻璃。
     *
     * 与另外两套的区别是「底色」来自照片而不是色板：内容卡片做成半透明玻璃，
     * 压在模糊后的壁纸上。壁纸本身就是干扰源，所以默认模糊度比 iOS 更重。
     */
    GLASS,
    ;

    companion object {
        /** 兼容早期版本存下的 `HYPEROS`，避免升级后风格被重置。 */
        fun fromName(name: String?): AppStyle = when (name) {
            "HYPEROS", "SOFT_CARD" -> SOFT_CARD
            "TERMINAL" -> TERMINAL
            "GLASS" -> GLASS
            else -> SOFT_CARD
        }
    }
}
