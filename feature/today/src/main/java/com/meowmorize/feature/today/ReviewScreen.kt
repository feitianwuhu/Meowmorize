package com.meowmorize.feature.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meowmorize.core.designsystem.component.MarkdownText
import com.meowmorize.core.designsystem.component.MeowCard
import com.meowmorize.core.designsystem.component.MeowChip
import com.meowmorize.core.designsystem.component.MeowDivider
import com.meowmorize.core.designsystem.component.MeowIconButton
import com.meowmorize.core.designsystem.component.PillTag
import com.meowmorize.core.designsystem.component.SubjectLabel
import com.meowmorize.core.designsystem.theme.LocalMeowColors
import com.meowmorize.core.designsystem.theme.displayName
import com.meowmorize.core.model.ContentFormat
import com.meowmorize.core.model.RecallGrade
import kotlinx.coroutines.delay

@Composable
fun ReviewRoute(
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.outcome) {
        if (state.outcome != null) {
            delay(900)
            onBack()
        }
    }

    ReviewScreen(
        state = state,
        onBack = onBack,
        onReveal = viewModel::reveal,
        onShowHint = viewModel::showHint,
        onHideHint = viewModel::hideHint,
        onSelectGrade = viewModel::selectGrade,
        onConfirm = viewModel::confirm,
    )
}

@Composable
fun ReviewScreen(
    state: ReviewUiState,
    onBack: () -> Unit,
    onReveal: () -> Unit,
    onShowHint: () -> Unit,
    onHideHint: () -> Unit,
    onSelectGrade: (RecallGrade) -> Unit,
    onConfirm: () -> Unit,
) {
    val item = state.item ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val hasHint = !item.hint.isNullOrBlank()
    val colors = LocalMeowColors.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 210.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeowIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                PillTag(text = state.state?.phase?.displayName() ?: "新学")
                Spacer(Modifier.size(6.dp))
                PillTag(text = "${item.estimatedMinutes} 分钟")
            }

            Spacer(Modifier.height(22.dp))
            val deadline = state.deadlineAt
            if (deadline != null) {
                val daysLeft = java.time.Duration.between(
                    java.time.Instant.now(),
                    deadline,
                ).toDays()
                Text(
                    text = if (daysLeft >= 0) {
                        "距截止 $daysLeft 天 · 目标保持率 ${(state.targetRetention * 100).toInt()}%"
                    } else {
                        "截止日期已过 · 按普通曲线复习"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.state?.leech == true) {
                Text(
                    text = "这条内容已反复遗忘多次：建议回到任务库改写关键句，或拆成两条更短的。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalMeowColors.current.warning,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.subject != null) {
                SubjectLabel(
                    name = state.subject.name,
                    color = Color(state.subject.colorArgb),
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                text = item.topic,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!item.source.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "来源：${item.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))

            // 两个显式动作：看答案、看提示。提示是「降低难度」，所以会记入本次复习。
            // 填空题的「显示答案」放在卡片里、紧贴答案位置，这里不再重复放一个：
            // 同一屏出现两个一模一样的按钮，用户只会以为点错了。
            val showRevealChip = item.clozeAnswer == null
            if (showRevealChip || hasHint) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showRevealChip) {
                        MeowChip(
                            text = if (state.revealed) "内容已显示" else "显示内容",
                            selected = state.revealed,
                            onClick = onReveal,
                        )
                    }
                    if (hasHint) {
                        MeowChip(
                            text = if (state.hintVisible) "隐藏提示" else "显示提示",
                            selected = state.hintVisible,
                            onClick = if (state.hintVisible) onHideHint else onShowHint,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            val answer = item.clozeAnswer
            MeowCard {
                if (answer != null) {
                    // 填空题：题干始终可见，答案才需要「显示答案」。这才符合考试时的情境。
                    if (item.contentFormat == ContentFormat.MARKDOWN) {
                        MarkdownText(markdown = item.content.ifBlank { "这条任务还没有填写题干。" })
                    } else {
                        Text(
                            text = item.content.ifBlank { "这条任务还没有填写题干。" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.revealed) {
                        MeowDivider()
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "答案",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = answer.ifBlank { "（没有填写答案）" },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        MeowChip(text = "显示答案", onClick = onReveal, selected = false)
                    }
                } else if (state.revealed) {
                    if (item.contentFormat == ContentFormat.MARKDOWN) {
                        MarkdownText(markdown = item.content.ifBlank { "这条任务还没有填写内容。" })
                    } else {
                        Text(
                            text = item.content.ifBlank { "这条任务还没有填写内容。" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "先在心里过一遍",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (hasHint) {
                                "想不起来可以看提示，但用过提示就不能选「轻松」。"
                            } else {
                                "想不起来也没关系，点开对照才是记忆真正发生的时候。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(18.dp))
                        MeowChip(text = "显示内容", onClick = onReveal, selected = false)
                    }
                }
            }

            if (state.hintVisible && hasHint) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.warning.copy(alpha = 0.10f))
                        .border(
                            1.dp,
                            colors.warning.copy(alpha = 0.28f),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(
                            text = "提示",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.warning,
                        )
                        Text(
                            text = item.hint.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.hintUsed) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "本次已用提示，「轻松」已禁用",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.selectedGrade != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "答案已展开：确认没问题就点「下一步」，也可以改选其他熟练度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        GradeBar(
            previews = state.previews,
            selectedGrade = state.selectedGrade,
            hintUsed = state.hintUsed,
            enabled = !state.isLocked,
            onSelect = onSelectGrade,
            onConfirm = onConfirm,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = state.outcome != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val outcome = state.outcome
            MeowCard(
                containerColor = MaterialTheme.colorScheme.onSurface,
                borderColor = Color.Transparent,
                modifier = Modifier.padding(horizontal = 48.dp),
            ) {
                Text(
                    text = if ((outcome?.nextIntervalDays ?: 0) > 0) {
                        "下次见：${outcome?.nextIntervalDays} 天后"
                    } else {
                        "10 分钟后再来一次"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.surface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "记下了，不用一直想着它",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * 底部评分条。
 *
 * 未选择时四个档位都可点；点中之后，被点的那个变成「下一步」，
 * 其余仍是可切换的档位——先看答案再确认，是这个交互的核心。
 * 用过提示时「轻松」直接不出现，而不是灰掉，避免形成「反正能点」的错觉。
 */
@Composable
private fun GradeBar(
    previews: Map<RecallGrade, Int>,
    selectedGrade: RecallGrade?,
    hintUsed: Boolean,
    enabled: Boolean,
    onSelect: (RecallGrade) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalMeowColors.current
    val entries = buildList {
        add(Triple(RecallGrade.AGAIN, "忘了", colors.danger))
        add(Triple(RecallGrade.HARD, "模糊", colors.warning))
        add(Triple(RecallGrade.GOOD, "记得", MaterialTheme.colorScheme.primary))
        if (!hintUsed) add(Triple(RecallGrade.EASY, "轻松", colors.success))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column {
            if (selectedGrade != null) {
                Text(
                    text = "点击其他熟练度可改选",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { (grade, label, color) ->
                    val isSelected = grade == selectedGrade
                    val days = previews[grade]
                    val intervalLabel = when {
                        days == null -> ""
                        days <= 0 -> "今天再练"
                        days == 1 -> "明天"
                        else -> "$days 天后"
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) color else color.copy(alpha = 0.12f),
                            )
                            .border(
                                1.dp,
                                if (isSelected) color else color.copy(alpha = 0.25f),
                                RoundedCornerShape(18.dp),
                            )
                            .clickable(enabled = enabled) {
                                if (isSelected) onConfirm() else onSelect(grade)
                            }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (isSelected) "下一步" else label,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) Color.White else color,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (isSelected) "$label · $intervalLabel" else intervalLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) {
                                Color.White.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
