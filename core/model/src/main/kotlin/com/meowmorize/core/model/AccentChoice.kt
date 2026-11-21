package com.meowmorize.core.model

/**
 * 强调色来源。
 *
 * 预设取各家系统的主色，用户也可以自己调一个色相（[CUSTOM]）。
 * `SYSTEM` 表示用项目原本的暖橙，不覆盖。
 */
enum class AccentChoice(val label: String) {
    SYSTEM("默认"),
    XIAOMI("小米橙"),
    VIVO("vivo 蓝"),
    HUAWEI("华为红"),
    OPPO("OPPO 绿"),
    APPLE("苹果蓝"),
    CUSTOM("自定义"),
    ;

    companion object {
        fun fromName(name: String?): AccentChoice =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
