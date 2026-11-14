package com.meowmorize.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "subjects",
    indices = [Index(value = ["name"], unique = true)],
)
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorArgb: Int,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    /** 学科权重：过载时按权重分配复习时间。 */
    @ColumnInfo(defaultValue = "1.0")
    val weight: Double = 1.0,
    /** 学科考试时间（可空），作为任务的兜底截止日期。 */
    val examAt: Long? = null,
)
