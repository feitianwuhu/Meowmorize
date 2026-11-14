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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
