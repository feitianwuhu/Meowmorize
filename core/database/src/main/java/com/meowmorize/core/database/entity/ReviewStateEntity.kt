package com.meowmorize.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 记忆状态与任务一对一，主键即 itemId。 */
@Entity(
    tableName = "review_states",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dueAt")],
)
data class ReviewStateEntity(
    @PrimaryKey val itemId: Long,
    val dueAt: Long,
    val stability: Double,
    val difficulty: Double,
    val reps: Int,
    val lapses: Int,
    val phase: String,
    val lastReviewedAt: Long?,
    val scheduledDays: Int,
)
