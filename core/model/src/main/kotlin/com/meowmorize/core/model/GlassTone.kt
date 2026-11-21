package com.meowmorize.core.model

/** 磨砂玻璃的明暗。跟随系统时不手动指定。 */
enum class GlassTone {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): GlassTone = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
