package com.meowmorize.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("subjectId"), Index("status")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val subjectId: Long?,
    val topic: String,
    val content: String,
    val source: String?,
    /** 存枚举名，避免序号漂移。 */
    val priority: String,
    val status: String,
    val estimatedMinutes: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Int = 1,
    /** 时长是否由用户手动指定；false 表示按内容长度自动估算。 */
    val durationIsCustom: Boolean = false,
    /** 用户自定的背诵提示，可空。 */
    val hint: String? = null,
    /** 内容格式：PLAIN / MARKDOWN。 */
    val contentFormat: String = "PLAIN",
)
