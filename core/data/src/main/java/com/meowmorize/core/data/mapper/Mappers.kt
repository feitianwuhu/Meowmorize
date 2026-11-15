package com.meowmorize.core.data.mapper

import com.meowmorize.core.database.entity.DailyPlanEntity
import com.meowmorize.core.database.entity.DailyPlanItemEntity
import com.meowmorize.core.database.entity.ItemEntity
import com.meowmorize.core.database.entity.ReviewLogEntity
import com.meowmorize.core.database.entity.ReviewStateEntity
import com.meowmorize.core.database.entity.SubjectEntity
import com.meowmorize.core.model.ContentFormat
import com.meowmorize.core.model.DailyPlan
import com.meowmorize.core.model.ItemStatus
import com.meowmorize.core.model.LearningPhase
import com.meowmorize.core.model.LoadLevel
import com.meowmorize.core.model.MemorizeItem
import com.meowmorize.core.model.PlannedTask
import com.meowmorize.core.model.PlanReason
import com.meowmorize.core.model.Priority
import com.meowmorize.core.model.RecallGrade
import com.meowmorize.core.model.ReviewLog
import com.meowmorize.core.model.ReviewState
import com.meowmorize.core.model.Subject
import java.time.Instant
import java.time.LocalDate

/**
 * Entity ↔ Model 映射。
 *
 * 只在这一层出现数据库字段的形态（Long 时间戳、枚举名字符串），
 * 上层模型始终使用 java.time 与强类型枚举。
 */

fun SubjectEntity.toModel(): Subject = Subject(
    id = id,
    name = name,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    createdAt = Instant.ofEpochMilli(createdAt),
    weight = weight,
    examAt = examAt?.let(Instant::ofEpochMilli),
)

fun Subject.toEntity(): SubjectEntity = SubjectEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    sortOrder = sortOrder,
    createdAt = createdAt.toEpochMilli(),
    weight = weight,
    examAt = examAt?.toEpochMilli(),
)

fun ItemEntity.toModel(): MemorizeItem = MemorizeItem(
    id = id,
    subjectId = subjectId,
    topic = topic,
    content = content,
    source = source,
    priority = Priority.fromName(priority),
    status = ItemStatus.fromName(status),
    estimatedMinutes = estimatedMinutes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    revision = revision,
    durationIsCustom = durationIsCustom,
    hint = hint,
    contentFormat = ContentFormat.fromName(contentFormat),
    deadlineAt = deadlineAt?.let(Instant::ofEpochMilli),
    clozeAnswer = clozeAnswer,
)

fun MemorizeItem.toEntity(): ItemEntity = ItemEntity(
    id = id,
    subjectId = subjectId,
    topic = topic,
    content = content,
    source = source,
    priority = priority.name,
    status = status.name,
    estimatedMinutes = estimatedMinutes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    revision = revision,
    durationIsCustom = durationIsCustom,
    hint = hint,
    contentFormat = contentFormat.name,
    deadlineAt = deadlineAt?.toEpochMilli(),
    clozeAnswer = clozeAnswer,
)

fun ReviewStateEntity.toModel(): ReviewState = ReviewState(
    itemId = itemId,
    dueAt = Instant.ofEpochMilli(dueAt),
    stability = stability,
    difficulty = difficulty,
    reps = reps,
    lapses = lapses,
    phase = LearningPhase.fromName(phase),
    lastReviewedAt = lastReviewedAt?.let(Instant::ofEpochMilli),
    scheduledDays = scheduledDays,
    leech = leech,
    leechSince = leechSince?.let(Instant::ofEpochMilli),
    learningStep = learningStep,
    sameDayReps = sameDayReps,
)

fun ReviewState.toEntity(): ReviewStateEntity = ReviewStateEntity(
    itemId = itemId,
    dueAt = dueAt.toEpochMilli(),
    stability = stability,
    difficulty = difficulty,
    reps = reps,
    lapses = lapses,
    phase = phase.name,
    lastReviewedAt = lastReviewedAt?.toEpochMilli(),
    scheduledDays = scheduledDays,
    leech = leech,
    leechSince = leechSince?.toEpochMilli(),
    learningStep = learningStep,
    sameDayReps = sameDayReps,
)

fun ReviewLogEntity.toModel(): ReviewLog = ReviewLog(
    id = id,
    itemId = itemId,
    reviewedAt = Instant.ofEpochMilli(reviewedAt),
    grade = RecallGrade.entries.firstOrNull { it.name == grade } ?: RecallGrade.GOOD,
    intervalBefore = intervalBefore,
    intervalAfter = intervalAfter,
    stabilityAfter = stabilityAfter,
    phaseAfter = LearningPhase.fromName(phaseAfter),
    itemRevision = itemRevision,
    elapsedDays = elapsedDays,
    scheduledDueAt = scheduledDueAt?.let(Instant::ofEpochMilli),
    stabilityBefore = stabilityBefore,
    difficultyBefore = difficultyBefore,
    difficultyAfter = difficultyAfter,
    retrievability = retrievability,
    hintUsed = hintUsed,
    wasOverdue = wasOverdue,
    actualMs = actualMs,
)

fun ReviewLog.toEntity(): ReviewLogEntity = ReviewLogEntity(
    id = id,
    itemId = itemId,
    reviewedAt = reviewedAt.toEpochMilli(),
    grade = grade.name,
    intervalBefore = intervalBefore,
    intervalAfter = intervalAfter,
    stabilityAfter = stabilityAfter,
    phaseAfter = phaseAfter.name,
    itemRevision = itemRevision,
    elapsedDays = elapsedDays,
    scheduledDueAt = scheduledDueAt?.toEpochMilli(),
    stabilityBefore = stabilityBefore,
    difficultyBefore = difficultyBefore,
    difficultyAfter = difficultyAfter,
    retrievability = retrievability,
    hintUsed = hintUsed,
    wasOverdue = wasOverdue,
    actualMs = actualMs,
)

fun DailyPlanEntity.toPlan(
    tasks: List<PlannedTask>,
    date: LocalDate,
): DailyPlan = DailyPlan(
    date = date,
    generatedAt = Instant.ofEpochMilli(generatedAt),
    tasks = tasks,
    capacityMinutes = capacityMinutes,
    plannedMinutes = plannedMinutes,
    loadLevel = LoadLevel.fromName(loadLevel),
    targetRetention = targetRetention,
    elasticityFactor = elasticityFactor,
    backlogCount = backlogCount,
    newCount = newCount,
    notes = notes.split('\n').filter { it.isNotBlank() },
    userAdjusted = userAdjusted,
)

fun DailyPlanItemEntity.reason(): PlanReason = PlanReason.fromName(reason)
