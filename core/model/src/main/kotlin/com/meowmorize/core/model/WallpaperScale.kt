package com.meowmorize.core.model

/** 壁纸铺法，对齐 Windows 的「填充 / 适应 / 拉伸 / 居中」。 */
enum class WallpaperScale(val label: String, val description: String) {
    /** 等比放大到铺满，超出部分裁掉。 */
    FILL("铺满", "等比放大铺满屏幕，超出裁掉"),

    /** 等比缩到完整可见，两侧或上下留空。 */
    FIT("适应", "完整显示，留边"),

    /** 不保持比例，拉满整屏。 */
    STRETCH("拉伸", "拉满整屏，可能变形"),

    /** 原始比例居中，不缩放。 */
    CENTER("居中", "原尺寸居中，四周留空"),
    ;

    companion object {
        fun fromName(name: String?): WallpaperScale =
            entries.firstOrNull { it.name == name } ?: FILL
    }
}
