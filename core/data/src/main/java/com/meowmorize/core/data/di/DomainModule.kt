package com.meowmorize.core.data.di

import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.data.calendar.AndroidCalendarRepository
import com.meowmorize.core.data.ocr.MlKitOcrRepository
import com.meowmorize.core.data.repository.CalendarRepositoryImpl
import com.meowmorize.core.data.repository.PlanRepositoryImpl
import com.meowmorize.core.data.time.AdjustableTimeProvider
import com.meowmorize.core.data.settings.SettingsRepositoryImpl
import com.meowmorize.core.data.repository.StudyRepositoryImpl
import com.meowmorize.core.domain.repository.CalendarRepository
import com.meowmorize.core.domain.repository.CalendarSyncRepository
import com.meowmorize.core.domain.repository.OcrRepository
import com.meowmorize.core.domain.repository.PlanRepository
import com.meowmorize.core.domain.repository.SettingsRepository
import com.meowmorize.core.domain.repository.StudyRepository
import com.meowmorize.core.domain.repository.TimeTravelRepository
import com.meowmorize.core.domain.plan.TodayPlanGenerator
import com.meowmorize.core.domain.srs.FsrsScheduler
import com.meowmorize.core.domain.text.DurationEstimator
import com.meowmorize.core.domain.usecase.GradeReviewUseCase
import com.meowmorize.core.domain.usecase.PrepareTodayPlanUseCase
import com.meowmorize.core.domain.usecase.SaveItemUseCase
import com.meowmorize.core.domain.usecase.SyncUpcomingToCalendarUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 仓库接口与实现的绑定。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStudyRepository(impl: StudyRepositoryImpl): StudyRepository

    @Binds
    @Singleton
    abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindCalendarSyncRepository(impl: AndroidCalendarRepository): CalendarSyncRepository

    @Binds
    @Singleton
    abstract fun bindOcrRepository(impl: MlKitOcrRepository): OcrRepository

    /** 同一个实例同时提供「当前时间」与「时间快进」能力，保证两边看到的偏移一致。 */
    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: AdjustableTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindTimeTravelRepository(impl: AdjustableTimeProvider): TimeTravelRepository
}

/**
 * 领域服务与用例的提供者。
 *
 * 领域层保持纯 Kotlin（不依赖 Hilt），由这里统一装配，
 * 既保证可单测，也避免领域类被框架注解污染。
 */
@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideFsrsScheduler(): FsrsScheduler = FsrsScheduler()

    @Provides
    @Singleton
    fun provideTodayPlanGenerator(): TodayPlanGenerator = TodayPlanGenerator()

    @Provides
    @Singleton
    fun provideDurationEstimator(): DurationEstimator = DurationEstimator()

    @Provides
    @Singleton
    fun providePrepareTodayPlanUseCase(
        planRepository: PlanRepository,
        settingsRepository: SettingsRepository,
        generator: TodayPlanGenerator,
        timeProvider: TimeProvider,
    ): PrepareTodayPlanUseCase = PrepareTodayPlanUseCase(
        planRepository = planRepository,
        settingsRepository = settingsRepository,
        generator = generator,
        timeProvider = timeProvider,
    )

    @Provides
    @Singleton
    fun provideSyncUpcomingToCalendarUseCase(
        settingsRepository: SettingsRepository,
        planRepository: PlanRepository,
        calendarSyncRepository: CalendarSyncRepository,
        timeProvider: TimeProvider,
    ): SyncUpcomingToCalendarUseCase = SyncUpcomingToCalendarUseCase(
        settingsRepository = settingsRepository,
        planRepository = planRepository,
        calendarSyncRepository = calendarSyncRepository,
        timeProvider = timeProvider,
    )

    @Provides
    @Singleton
    fun provideGradeReviewUseCase(
        planRepository: PlanRepository,
        studyRepository: StudyRepository,
        settingsRepository: SettingsRepository,
        timeProvider: TimeProvider,
        syncCalendar: SyncUpcomingToCalendarUseCase,
    ): GradeReviewUseCase = GradeReviewUseCase(
        planRepository = planRepository,
        studyRepository = studyRepository,
        settingsRepository = settingsRepository,
        timeProvider = timeProvider,
        syncCalendar = syncCalendar,
    )

    @Provides
    @Singleton
    fun provideSaveItemUseCase(
        studyRepository: StudyRepository,
        planRepository: PlanRepository,
        prepareTodayPlanUseCase: PrepareTodayPlanUseCase,
        timeProvider: TimeProvider,
        syncCalendar: SyncUpcomingToCalendarUseCase,
    ): SaveItemUseCase = SaveItemUseCase(
        studyRepository = studyRepository,
        planRepository = planRepository,
        prepareTodayPlan = prepareTodayPlanUseCase,
        timeProvider = timeProvider,
        syncCalendar = syncCalendar,
    )
}
