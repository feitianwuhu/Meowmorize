package com.meowmorize.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meowmorize.core.database.entity.ReviewStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewStateDao {

    @Query("SELECT * FROM review_states")
    fun observeAll(): Flow<List<ReviewStateEntity>>

    @Query("SELECT * FROM review_states")
    suspend fun getAll(): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states WHERE itemId = :itemId")
    fun observeByItem(itemId: Long): Flow<ReviewStateEntity?>

    @Query("SELECT * FROM review_states WHERE itemId = :itemId")
    suspend fun getByItem(itemId: Long): ReviewStateEntity?

    @Query("SELECT * FROM review_states WHERE dueAt <= :endOfDay ORDER BY dueAt ASC")
    suspend fun getDueBefore(endOfDay: Long): List<ReviewStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReviewStateEntity)

    @Query("DELETE FROM review_states WHERE itemId = :itemId")
    suspend fun delete(itemId: Long)
}
