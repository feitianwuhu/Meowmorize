package com.meowmorize.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 任务与系统日历事件的映射。
 *
 * 有了这张表，同步才能做到幂等：重复触发只更新同一条事件，
 * 任务删除/完成时也能准确找到要撤掉的那条日程。
 */
@Entity(
    tableName = "calendar_sync",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CalendarSyncEntity(
    @PrimaryKey val itemId: Long,
    val eventId: Long,
    val syncedAt: Long,
)
