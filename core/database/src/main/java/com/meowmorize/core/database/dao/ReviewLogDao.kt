package com.meowmorize.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.meowmorize.core.database.entity.ReviewLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {

    @Insert
    suspend fun insert(entity: ReviewLogEntity): Long

    @Query("SELECT * FROM review_logs WHERE reviewedAt BETWEEN :startMillis AND :endMillis ORDER BY reviewedAt ASC")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<ReviewLogEntity>

    @Query("SELECT * FROM review_logs WHERE reviewedAt BETWEEN :startMillis AND :endMillis ORDER BY reviewedAt ASC")
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<ReviewLogEntity>>

    @Query("SELECT * FROM review_logs WHERE itemId = :itemId ORDER BY reviewedAt DESC LIMIT 1")
    suspend fun latestForItem(itemId: Long): ReviewLogEntity?

    @Query("SELECT COUNT(*) FROM review_logs WHERE itemId = :itemId")
    suspend fun countForItem(itemId: Long): Int
}
