package com.meowmorize.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.meowmorize.core.model.LoadLevel
import com.meowmorize.core.model.LearningPhase
import com.meowmorize.core.model.PlanReason
import com.meowmorize.core.model.Priority
import com.meowmorize.core.model.TaskSortOrder

/**
 * 领域枚举的展示文案与配色集中在这里。
 *
 * 放在 design system 而不是 model 层：文案属于界面，换语言/换风格只改这一处；
 * model 层保持纯粹的业务语义。
 */

fun LearningPhase.displayName(): String = when (this) {
    LearningPhase.NEW -> "新学"
    LearningPhase.LEARNING -> "学习中"
    LearningPhase.REVIEW -> "复习中"
    LearningPhase.RELEARNING -> "重学"
}

fun PlanReason.displayName(): String = when (this) {
    PlanReason.OVERDUE -> "逾期"
    PlanReason.DUE_TODAY -> "今日复习"
    PlanReason.CARRY_OVER -> "顺延"
    PlanReason.NEW -> "新学"
}

fun LoadLevel.displayName(): String = when (this) {
    LoadLevel.RELAXED -> "轻松"
    LoadLevel.STEADY -> "标准"
    LoadLevel.INTENSE -> "冲刺"
}

fun Priority.displayName(): String = when (this) {
    Priority.LOW -> "低"
    Priority.NORMAL -> "普通"
    Priority.HIGH -> "高"
}

fun TaskSortOrder.displayName(): String = when (this) {
    TaskSortOrder.RECENT_UPDATED -> "最近更新"
    TaskSortOrder.RECENT_CREATED -> "最近创建"
    TaskSortOrder.NEXT_REVIEW -> "下次复习"
    TaskSortOrder.TOPIC -> "主题名称"
    TaskSortOrder.DURATION -> "预计用时"
    TaskSortOrder.PRIORITY -> "优先级"
}

/** 任务原因标签的配色：逾期用警示红、新学用蓝色，其余保持中性。 */
fun PlanReason.tagColors(): Pair<Color, Color> = when (this) {
    PlanReason.OVERDUE -> Color(0x1FFF5B5B) to Color(0xFFD93B3B)
    PlanReason.NEW -> Color(0x1F3D7EFF) to Color(0xFF2F6DE0)
    PlanReason.DUE_TODAY -> Color(0x1F1FA971) to Color(0xFF15855A)
    PlanReason.CARRY_OVER -> Color(0x1FE9A23B) to Color(0xFFB8791F)
}
