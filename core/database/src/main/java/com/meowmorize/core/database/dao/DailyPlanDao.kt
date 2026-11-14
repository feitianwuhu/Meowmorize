package com.meowmorize.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meowmorize.core.database.entity.DailyPlanEntity
import com.meowmorize.core.database.entity.DailyPlanItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyPlanDao {

    @Query("SELECT * FROM daily_plans WHERE date = :date")
    fun observePlan(date: String): Flow<DailyPlanEntity?>

    @Query("SELECT * FROM daily_plans WHERE date = :date")
    suspend fun getPlan(date: String): DailyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(entity: DailyPlanEntity)

    @Query("DELETE FROM daily_plans WHERE date = :date")
    suspend fun deletePlan(date: String)

    @Query("SELECT * FROM daily_plans WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getPlansBetween(startDate: String, endDate: String): List<DailyPlanEntity>

    @Query("SELECT * FROM daily_plan_items WHERE date = :date ORDER BY orderIndex ASC")
    fun observePlanItems(date: String): Flow<List<DailyPlanItemEntity>>

    @Query("SELECT * FROM daily_plan_items WHERE date = :date ORDER BY orderIndex ASC")
    suspend fun getPlanItems(date: String): List<DailyPlanItemEntity>

    @Query("SELECT * FROM daily_plan_items WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getPlanItemsBetween(startDate: String, endDate: String): List<DailyPlanItemEntity>

    @Query("SELECT * FROM daily_plan_items WHERE date BETWEEN :startDate AND :endDate")
    fun observePlanItemsBetween(startDate: String, endDate: String): Flow<List<DailyPlanItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanItems(items: List<DailyPlanItemEntity>)

    @Query("DELETE FROM daily_plan_items WHERE date = :date")
    suspend fun deletePlanItems(date: String)
}
