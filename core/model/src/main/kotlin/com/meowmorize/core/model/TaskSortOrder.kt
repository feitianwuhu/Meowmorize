package com.meowmorize.core.model

/**
 * 任务库排序方式。
 *
 * 排序是「用户对这批内容的心理模型」：备考期常按「下次复习」看进度，
 * 整理期常按「最近更新」查漏，所以不设默认唯一答案，交给用户切。
 */
enum class TaskSortOrder {
    /** 最近更新：刚改过/刚背过的在前。 */
    RECENT_UPDATED,

    /** 最近创建：新加的内容在前。 */
    RECENT_CREATED,

    /** 下次复习：到期越早越靠前，未开始的排最后。 */
    NEXT_REVIEW,

    /** 按主题名称（中文按拼音序）。 */
    TOPIC,

    /** 预计用时：长的在前，方便挑整块时间处理。 */
    DURATION,

    /** 优先级：高的在前。 */
    PRIORITY,
    ;

    companion object {
        fun fromName(name: String?): TaskSortOrder =
            entries.firstOrNull { it.name == name } ?: RECENT_UPDATED
    }
}
