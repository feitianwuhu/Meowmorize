package com.meowmorize.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移。
 *
 * 规则：只加不删，且新增列必须带默认值——否则老用户升级会直接崩在启动阶段。
 */
object MeowMigrations {

    /** v1 → v2：支持「自定义背诵时长」与「系统日历同步映射」。 */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `items` ADD COLUMN `durationIsCustom` INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `calendar_sync` (
                    `itemId` INTEGER NOT NULL,
                    `eventId` INTEGER NOT NULL,
                    `syncedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`itemId`),
                    FOREIGN KEY(`itemId`) REFERENCES `items`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }

    /** v2 → v3：背诵提示 + 内容格式（Markdown）。 */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `items` ADD COLUMN `hint` TEXT")
            db.execSQL("ALTER TABLE `items` ADD COLUMN `contentFormat` TEXT NOT NULL DEFAULT 'PLAIN'")
        }
    }

    /**
     * v3 → v4：把「可回溯的观测数据」补齐，并引入截止日期与反复遗忘标记。
     *
     * 旧数据没有这些值，统一按「未知」处理：数值列 0、耗时 -1、时间为 NULL。
     * 这样统计代码可以用 `actualMs >= 0` 之类的判断把老记录排除，而不是把 0 当成真实值。
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 复习日志：算法验证与后续校准的全部依据
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `elapsedDays` REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `scheduledDueAt` INTEGER")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `stabilityBefore` REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `difficultyBefore` REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `difficultyAfter` REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `retrievability` REAL NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `hintUsed` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `wasOverdue` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_logs` ADD COLUMN `actualMs` INTEGER NOT NULL DEFAULT -1")

            // 任务：截止时间（考试/汇报）
            db.execSQL("ALTER TABLE `items` ADD COLUMN `deadlineAt` INTEGER")

            // 学科：权重与考试时间
            db.execSQL("ALTER TABLE `subjects` ADD COLUMN `weight` REAL NOT NULL DEFAULT 1.0")
            db.execSQL("ALTER TABLE `subjects` ADD COLUMN `examAt` INTEGER")

            // 记忆状态：反复遗忘与同日次数
            db.execSQL("ALTER TABLE `review_states` ADD COLUMN `leech` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_states` ADD COLUMN `leechSince` INTEGER")
            db.execSQL("ALTER TABLE `review_states` ADD COLUMN `learningStep` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `review_states` ADD COLUMN `sameDayReps` INTEGER NOT NULL DEFAULT 0")

            // 计划：首版快照（完成率分母）与当天状态
            db.execSQL("ALTER TABLE `daily_plans` ADD COLUMN `baselinePlannedMinutes` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `daily_plans` ADD COLUMN `baselineTaskCount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `daily_plans` ADD COLUMN `todayState` TEXT NOT NULL DEFAULT 'AUTO'")

            // 老计划没有快照，用当前计划量回填，避免完成率分母变成 0
            db.execSQL(
                "UPDATE `daily_plans` SET `baselinePlannedMinutes` = `plannedMinutes`, " +
                    "`baselineTaskCount` = (SELECT COUNT(*) FROM `daily_plan_items` " +
                    "WHERE `daily_plan_items`.`date` = `daily_plans`.`date`)",
            )
        }
    }

    /** v4 → v5：填空题答案。null 表示普通内容，因此不需要回填。 */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `items` ADD COLUMN `clozeAnswer` TEXT")
        }
    }

    val ALL: Array<Migration> =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
