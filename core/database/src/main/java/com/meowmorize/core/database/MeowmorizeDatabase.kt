package com.meowmorize.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.meowmorize.core.database.dao.CalendarSyncDao
import com.meowmorize.core.database.dao.DailyPlanDao
import com.meowmorize.core.database.dao.ItemDao
import com.meowmorize.core.database.dao.ReviewLogDao
import com.meowmorize.core.database.dao.ReviewStateDao
import com.meowmorize.core.database.dao.SubjectDao
import com.meowmorize.core.database.entity.CalendarSyncEntity
import com.meowmorize.core.database.entity.DailyPlanEntity
import com.meowmorize.core.database.entity.DailyPlanItemEntity
import com.meowmorize.core.database.entity.ItemEntity
import com.meowmorize.core.database.entity.ReviewLogEntity
import com.meowmorize.core.database.entity.ReviewStateEntity
import com.meowmorize.core.database.entity.SubjectEntity

/**
 * 本地数据库。
 *
 * 版本策略：每次结构变更 version + 1，并提供 [Migration]；
 * `schemas/` 目录下的 JSON 会被纳入版本控制，用于生成迁移测试。
 */
@Database(
    entities = [
        SubjectEntity::class,
        ItemEntity::class,
        ReviewStateEntity::class,
        ReviewLogEntity::class,
        DailyPlanEntity::class,
        DailyPlanItemEntity::class,
        CalendarSyncEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class MeowmorizeDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun itemDao(): ItemDao
    abstract fun reviewStateDao(): ReviewStateDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun dailyPlanDao(): DailyPlanDao
    abstract fun calendarSyncDao(): CalendarSyncDao

    companion object {
        const val NAME = "meowmorize.db"
    }
}
