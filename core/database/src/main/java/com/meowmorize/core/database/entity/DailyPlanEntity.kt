package com.meowmorize.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一天的「计划头」：容量、负荷、算法解释等元信息。 */
@Entity(tableName = "daily_plans")
data class DailyPlanEntity(
    @PrimaryKey val date: String,
    val generatedAt: Long,
    val capacityMinutes: Int,
    val plannedMinutes: Int,
    val loadLevel: String,
    val targetRetention: Double,
    val elasticityFactor: Double,
    val backlogCount: Int,
    val newCount: Int,
    /** 多条说明用换行拼接，读取时再拆开，避免为了几十个字符再建一张表。 */
    val notes: String,
    val userAdjusted: Boolean,
)
