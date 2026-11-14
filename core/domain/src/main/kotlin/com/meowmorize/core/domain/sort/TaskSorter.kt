package com.meowmorize.core.domain.sort

import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.TaskSortOrder
import java.text.Collator
import java.time.Instant
import java.util.Locale

/** 排序所需的字段，避免把 UI 的列表模型下沉到领域层。 */
data class TaskSortKey(
    val item: MemorizeItem,
    /** 下次复习时间；新任务为 null。 */
    val nextReviewAt: Instant?,
)

/**
 * 任务库排序。
 *
 * 中文主题用 [Collator] 做拼音序，而不是 `String.compareTo`——
 * 后者按 UTF-16 码位排，会把「英语」排在「政治」后面，用户一眼就觉得乱。
 */
object TaskSorter {

    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    fun sort(keys: List<TaskSortKey>, order: TaskSortOrder): List<TaskSortKey> = when (order) {
        TaskSortOrder.RECENT_UPDATED -> keys.sortedByDescending { it.item.updatedAt }
        TaskSortOrder.RECENT_CREATED -> keys.sortedByDescending { it.item.createdAt }
        TaskSortOrder.NEXT_REVIEW -> keys.sortedWith(
            compareBy<TaskSortKey> { it.nextReviewAt == null }
                .thenBy { it.nextReviewAt ?: Instant.MAX },
        )
        TaskSortOrder.TOPIC -> keys.sortedWith { a, b -> collator.compare(a.item.topic, b.item.topic) }
        TaskSortOrder.DURATION -> keys.sortedWith(
            compareByDescending<TaskSortKey> { it.item.estimatedMinutes }
                .thenByDescending { it.item.updatedAt },
        )
        TaskSortOrder.PRIORITY -> keys.sortedWith(
            compareByDescending<TaskSortKey> { it.item.priority.weight }
                .thenByDescending { it.item.updatedAt },
        )
    }
}
