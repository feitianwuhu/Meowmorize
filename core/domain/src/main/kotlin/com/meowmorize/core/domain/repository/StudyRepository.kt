package com.meowmorize.core.domain.repository

import com.meowmorize.core.model.ItemDraft
import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.ScheduleUpdatePolicy
import com.meowmorize.core.model.Subject
import kotlinx.coroutines.flow.Flow

/** 任务与学科的读写入口。实现位于 `:core:data`，UI 只依赖该接口。 */
interface StudyRepository {

    fun observeSubjects(): Flow<List<Subject>>

    suspend fun subjects(): List<Subject>

    /** 按名称获取学科，不存在则创建。用于编辑器里的「新建学科」。 */
    suspend fun ensureSubject(name: String): Subject

    fun observeActiveItems(): Flow<List<MemorizeItem>>

    fun observeAllItems(): Flow<List<MemorizeItem>>

    fun observeItem(id: Long): Flow<MemorizeItem?>

    suspend fun getItem(id: Long): MemorizeItem?

    /** 一次性读取全部在学任务（生成计划、日历同步等批处理场景）。 */
    suspend fun activeItems(): List<MemorizeItem>

    suspend fun createItem(draft: ItemDraft): Long

    /**
     * 更新任务。
     *
     * @param policy 决定是否同步重置记忆状态：
     *  - [ScheduleUpdatePolicy.KEEP_SCHEDULE]：只覆盖内容与属性，复习时间不变（改错别字、补来源时用）
     *  - [ScheduleUpdatePolicy.RESET_SCHEDULE]：内容大改，按新内容重新学习
     */
    suspend fun updateItem(item: MemorizeItem, policy: ScheduleUpdatePolicy)

    suspend fun archiveItem(id: Long)

    suspend fun restoreItem(id: Long)
}
