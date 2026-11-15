package com.meowmorize.core.data.repository

import com.meowmorize.core.common.time.TimeProvider
import com.meowmorize.core.data.mapper.toEntity
import com.meowmorize.core.data.mapper.toModel
import com.meowmorize.core.database.dao.ItemDao
import com.meowmorize.core.database.dao.ReviewStateDao
import com.meowmorize.core.database.dao.SubjectDao
import com.meowmorize.core.database.entity.ItemEntity
import com.meowmorize.core.database.entity.SubjectEntity
import com.meowmorize.core.domain.repository.StudyRepository
import com.meowmorize.core.domain.text.DurationEstimator
import com.meowmorize.core.model.ItemDraft
import com.meowmorize.core.model.ItemStatus
import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.ScheduleUpdatePolicy
import com.meowmorize.core.model.Subject
import com.meowmorize.core.model.SubjectPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudyRepositoryImpl @Inject constructor(
    private val itemDao: ItemDao,
    private val subjectDao: SubjectDao,
    private val reviewStateDao: ReviewStateDao,
    private val timeProvider: TimeProvider,
    private val durationEstimator: DurationEstimator,
) : StudyRepository {

    override fun observeSubjects(): Flow<List<Subject>> =
        subjectDao.observeAll().map { list -> list.map(SubjectEntity::toModel) }

    override suspend fun subjects(): List<Subject> = subjectDao.getAll().map(SubjectEntity::toModel)

    override suspend fun ensureSubject(name: String): Subject {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "学科名称不能为空" }
        subjectDao.findByName(trimmed)?.let { return it.toModel() }

        val count = subjectDao.count()
        val entity = SubjectEntity(
            name = trimmed,
            colorArgb = SubjectPalette.colorFor(count),
            sortOrder = count,
            createdAt = timeProvider.now().toEpochMilli(),
        )
        val id = subjectDao.insert(entity)
        return entity.copy(id = id).toModel()
    }

    override fun observeActiveItems(): Flow<List<MemorizeItem>> =
        itemDao.observeActive().map { list -> list.map(ItemEntity::toModel) }

    override fun observeAllItems(): Flow<List<MemorizeItem>> =
        itemDao.observeAll().map { list -> list.map(ItemEntity::toModel) }

    override fun observeItem(id: Long): Flow<MemorizeItem?> =
        itemDao.observeById(id).map { it?.toModel() }

    override suspend fun getItem(id: Long): MemorizeItem? = itemDao.getById(id)?.toModel()

    override suspend fun activeItems(): List<MemorizeItem> = itemDao.getActive().map(ItemEntity::toModel)

    override suspend fun createItem(draft: ItemDraft): Long {
        val now = timeProvider.now()
        // 用户填了时长就尊重用户；没填则按内容长度估算
        val minutes = if (draft.durationIsCustom) {
            durationEstimator.estimate(draft.content, draft.estimatedMinutes)
        } else {
            durationEstimator.estimate(draft.content, null)
        }
        val entity = ItemEntity(
            subjectId = draft.subjectId,
            topic = draft.topic.trim(),
            content = draft.content.trim(),
            source = draft.source?.trim()?.takeIf { it.isNotEmpty() },
            priority = draft.priority.name,
            status = ItemStatus.ACTIVE.name,
            estimatedMinutes = minutes,
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
            revision = 1,
            durationIsCustom = draft.durationIsCustom,
            hint = draft.hint?.trim()?.takeIf { it.isNotEmpty() },
            contentFormat = draft.contentFormat.name,
            deadlineAt = draft.deadlineAt?.toEpochMilli(),
            clozeAnswer = draft.clozeAnswer,
        )
        return itemDao.insert(entity)
    }

    override suspend fun updateItem(item: MemorizeItem, policy: ScheduleUpdatePolicy) {
        val existing = itemDao.getById(item.id) ?: return
        val contentChanged = existing.content != item.content || existing.topic != item.topic
        val updated = item.copy(
            // 自定义时长原样保留；否则内容一变就重算，避免「改了内容时长还是旧的」
            estimatedMinutes = if (item.durationIsCustom) {
                item.estimatedMinutes.coerceIn(DurationEstimator.MIN, DurationEstimator.MAX)
            } else {
                durationEstimator.estimate(item.content, null)
            },
            updatedAt = timeProvider.now(),
            revision = if (contentChanged) existing.revision + 1 else existing.revision,
        )
        itemDao.update(updated.toEntity().copy(createdAt = existing.createdAt))

        // 用户选择「按新内容重新学习」时清空记忆状态，任务会在下一次生成计划时作为新学出现。
        // 复习日志保留，便于后续对比「改了内容之后是不是更难背了」。
        if (policy == ScheduleUpdatePolicy.RESET_SCHEDULE) {
            reviewStateDao.delete(item.id)
        }
    }

    override suspend fun deleteItem(id: Long) {
        itemDao.deleteById(id)
    }

    override suspend fun deleteSubject(id: Long) {
        subjectDao.deleteById(id)
    }

    override suspend fun subjectItemCount(id: Long): Int = subjectDao.itemCount(id)

    override suspend fun archiveItem(id: Long) {
        itemDao.setStatus(id, ItemStatus.ARCHIVED.name, timeProvider.now().toEpochMilli())
    }

    override suspend fun restoreItem(id: Long) {
        itemDao.setStatus(id, ItemStatus.ACTIVE.name, timeProvider.now().toEpochMilli())
    }
}
