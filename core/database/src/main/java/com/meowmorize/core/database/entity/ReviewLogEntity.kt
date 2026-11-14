package com.meowmorize.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_logs",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId"), Index("reviewedAt")],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val itemId: Long,
    val reviewedAt: Long,
    val grade: String,
    val intervalBefore: Int,
    val intervalAfter: Int,
    val stabilityAfter: Double,
    val phaseAfter: String,
    val itemRevision: Int,
    /** 真实间隔（天），10 分钟记 0.0069。 */
    @ColumnInfo(defaultValue = "0")
    val elapsedDays: Double = 0.0,
    /** 复习前系统认为的到期时间。 */
    val scheduledDueAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val stabilityBefore: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val difficultyBefore: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val difficultyAfter: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val retrievability: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val hintUsed: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val wasOverdue: Boolean = false,
    /** 实际背诵耗时（毫秒），-1 表示未记录。 */
    @ColumnInfo(defaultValue = "-1")
    val actualMs: Long = -1,
)
