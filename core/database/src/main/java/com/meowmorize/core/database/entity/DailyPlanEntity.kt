package com.meowmorize.core.database.entity

import androidx.room.ColumnInfo
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
    /**
     * 当天首次生成时的计划量快照。
     * 完成率的分母必须用它，而不是「最后一次重排后的计划」——否则一重排，历史就被改写。
     */
    @ColumnInfo(defaultValue = "0")
    val baselinePlannedMinutes: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val baselineTaskCount: Int = 0,
    /** 当天使用的「今天状态」（AUTO/TIRED/NORMAL/ENERGETIC），便于回溯为什么排得少。 */
    @ColumnInfo(defaultValue = "AUTO")
    val todayState: String = "AUTO",
)
