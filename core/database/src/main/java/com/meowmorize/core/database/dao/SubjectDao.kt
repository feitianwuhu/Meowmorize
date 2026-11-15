package com.meowmorize.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.meowmorize.core.database.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {

    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): SubjectEntity?

    @Query("SELECT COUNT(*) FROM subjects")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: SubjectEntity): Long

    @Update
    suspend fun update(entity: SubjectEntity)

    @Query("SELECT COUNT(*) FROM items WHERE subjectId = :subjectId")
    suspend fun itemCount(subjectId: Long): Int

    /** 删除学科；该学科下的任务会被外键置为「未分类」，不会被连带删除。 */
    @Query("DELETE FROM subjects WHERE id = :subjectId")
    suspend fun deleteById(subjectId: Long)
}
