package com.meowmorize.core.data.di

import android.content.Context
import androidx.room.Room
import com.meowmorize.core.database.MeowMigrations
import com.meowmorize.core.database.MeowmorizeDatabase
import com.meowmorize.core.database.dao.CalendarSyncDao
import com.meowmorize.core.database.dao.DailyPlanDao
import com.meowmorize.core.database.dao.ItemDao
import com.meowmorize.core.database.dao.ReviewLogDao
import com.meowmorize.core.database.dao.ReviewStateDao
import com.meowmorize.core.database.dao.SubjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeowmorizeDatabase =
        Room.databaseBuilder(context, MeowmorizeDatabase::class.java, MeowmorizeDatabase.NAME)
            .addMigrations(*MeowMigrations.ALL)
            // 兜底：未来若漏写迁移，宁可重建也不要在启动时崩溃（当前仅 v2）
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideSubjectDao(db: MeowmorizeDatabase): SubjectDao = db.subjectDao()

    @Provides
    fun provideItemDao(db: MeowmorizeDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideReviewStateDao(db: MeowmorizeDatabase): ReviewStateDao = db.reviewStateDao()

    @Provides
    fun provideReviewLogDao(db: MeowmorizeDatabase): ReviewLogDao = db.reviewLogDao()

    @Provides
    fun provideDailyPlanDao(db: MeowmorizeDatabase): DailyPlanDao = db.dailyPlanDao()

    @Provides
    fun provideCalendarSyncDao(db: MeowmorizeDatabase): CalendarSyncDao = db.calendarSyncDao()
}
