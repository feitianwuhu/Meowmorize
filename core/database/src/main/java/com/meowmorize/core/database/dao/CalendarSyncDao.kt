package com.meowmorize.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.meowmorize.core.database.entity.CalendarSyncEntity

@Dao
interface CalendarSyncDao {

    @Query("SELECT * FROM calendar_sync")
    suspend fun getAll(): List<CalendarSyncEntity>

    @Query("SELECT * FROM calendar_sync WHERE itemId = :itemId")
    suspend fun getByItem(itemId: Long): CalendarSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CalendarSyncEntity)

    @Query("DELETE FROM calendar_sync WHERE itemId = :itemId")
    suspend fun deleteByItem(itemId: Long)

    @Query("DELETE FROM calendar_sync")
    suspend fun deleteAll()
}
