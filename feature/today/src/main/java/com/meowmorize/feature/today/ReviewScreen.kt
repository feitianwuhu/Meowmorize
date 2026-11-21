package com.meowmorize.feature.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meowmorize.core.designsystem.component.MarkdownText
import com.meowmorize.core.designsystem.motion.MeowMotion
import com.meowmorize.core.designsystem.motion.meowPressScale
import com.meowmorize.core.designsystem.motion.meowTween
import com.meowmorize.core.designsystem.theme.LocalAppStyle
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.designsystem.component.MeowCard
import com.meowmorize.core.designsystem.component.MeowChip
import com.meowmorize.core.designsystem.component.MeowDivider
import com.meowmorize.core.designsystem.component.MeowOutlinedButton
import com.meowmorize.core.designsystem.component.MeowIconButton
import com.meowmorize.core.designsystem.component.PillTag
import com.meowmorize.core.designsystem.component.SubjectLabel
import com.meowmorize.core.designsystem.theme.LocalMeowColors
import com.meowmorize.core.designsystem.theme.displayName
import com.meowmorize.core.model.ContentFormat
import com.meowmorize.core.model.MemorizeItem
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

            Spacer(Modifier.height(12.dp))
            // 揭示动作只剩一种触发方式：点下方熟练度。评分即揭晓——
            // 先凭记忆判断，再看对照，这才是复习真正发生的那一刻。
            val answer = item.clozeAnswer?.takeIf { it.isNotBlank() }
            val revealIn = meowTween<Float>(MeowMotion.BASE)
            val revealOut = meowTween<Float>(MeowMotion.FAST)
            MeowCard {
                if (answer != null) {
                    ItemContent(item = item, emptyHint = "这条任务还没有填写背诵内容。")
                    Spacer(Modifier.height(12.dp))
                    AnimatedContent(
                        targetState = state.revealed,
                        transitionSpec = { fadeIn(revealIn) togetherWith fadeOut(revealOut) },
                        label = "answerReveal",
                    ) { revealed ->
                        if (revealed) {
                            Column {
                                MeowDivider()
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = "答案",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = answer,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            Text(
                                text = "在心里过一遍，然后点下方熟练度——答案会在评分后出现",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = state.revealed,
                        transitionSpec = { fadeIn(revealIn) togetherWith fadeOut(revealOut) },
                        label = "contentReveal",
                    ) { revealed ->
                        if (revealed) {
                            ItemContent(item = item, emptyHint = "这条任务还没有填写内容。")
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
                                    text = "回想完就点下方熟练度，内容会在评分后出现",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
            canShowHint = hasHint,
            hintVisible = state.hintVisible,
            onToggleHint = if (state.hintVisible) onHideHint else onShowHint,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = state.outcome != null,
            // 进入：淡入 + 从 96% 放大；退出更快。绝不从 scale(0) 开始——
            // 现实里没有东西是从「不存在」瞬间长出来的
            enter = fadeIn(meowTween(MeowMotion.BASE)) +
                scaleIn(initialScale = 0.96f, animationSpec = meowTween(MeowMotion.BASE, MeowMotion.easeOut)),
            exit = fadeOut(meowTween(MeowMotion.FAST)) +
                scaleOut(targetScale = 0.98f, animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut)),
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
    canShowHint: Boolean,
    hintVisible: Boolean,
    onToggleHint: () -> Unit,
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
            // 玻璃风格下 background 是透明的，底部操作条会直接压在照片上；
            // 这种「承载按钮的条」必须用 surface（半透明玻璃）而不是 background
            .background(
                if (LocalAppStyle.current == AppStyle.GLASS) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.background
                },
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column {
            // 唯一的「求援」入口，放在拇指最容易够到的位置，做成整宽按钮
            if (canShowHint) {
                MeowOutlinedButton(
                    text = if (hintVisible) "收起提示" else "显示提示",
                    onClick = onToggleHint,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
            }
            AnimatedVisibility(
                visible = selectedGrade != null,
                enter = fadeIn(meowTween(MeowMotion.FAST)) +
                    expandVertically(animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut)),
                exit = fadeOut(meowTween(MeowMotion.PRESS)) +
                    shrinkVertically(animationSpec = meowTween(MeowMotion.PRESS, MeowMotion.easeOut)),
            ) {
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
                    // 选中态直接换色会「啪」地闪一下；这里用 150ms 过渡，
                    // 再叠一个按压缩放——评分是复习里点得最多的动作，手感要跟得上
                    val container by animateColorAsState(
                        targetValue = if (isSelected) color else color.copy(alpha = 0.12f),
                        animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut),
                        label = "gradeContainer",
                    )
                    val borderTint by animateColorAsState(
                        targetValue = if (isSelected) color else color.copy(alpha = 0.25f),
                        animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut),
                        label = "gradeBorder",
                    )
                    val titleColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else color,
                        animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut),
                        label = "gradeTitle",
                    )
                    val interaction = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .meowPressScale(interactionSource = interaction, pressedScale = 0.96f)
                            .background(container)
                            .border(1.dp, borderTint, RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = interaction,
                                indication = LocalIndication.current,
                                enabled = enabled,
                            ) {
                                if (isSelected) onConfirm() else onSelect(grade)
                            }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (isSelected) "下一步" else label,
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
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

/** 背诵内容的渲染（纯文本 / Markdown 两种），填空题与普通内容共用。 */
@Composable
private fun ItemContent(item: MemorizeItem, emptyHint: String) {
    val text = item.content.ifBlank { emptyHint }
    if (item.contentFormat == ContentFormat.MARKDOWN) {
        MarkdownText(markdown = text)
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
