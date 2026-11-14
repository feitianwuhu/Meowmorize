package com.meowmorize.core.model

/**
 * 背诵内容的格式。
 *
 * - [PLAIN]：纯文本，适合一段话直接背；
 * - [MARKDOWN]：支持标题、列表、引用与「两栏对照表」，用于结构化理解与分栏背诵。
 */
enum class ContentFormat {
    PLAIN,
    MARKDOWN,
    ;

    companion object {
        fun fromName(name: String?): ContentFormat = entries.firstOrNull { it.name == name } ?: PLAIN
    }
}
