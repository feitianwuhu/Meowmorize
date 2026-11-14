package com.meowmorize.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meowmorize.core.designsystem.component.MeowBottomSheet
import com.meowmorize.core.designsystem.component.MeowOutlinedButton
import com.meowmorize.core.designsystem.component.MeowPrimaryButton
import com.meowmorize.core.designsystem.component.MeowTextButton
import com.meowmorize.core.model.UserSettings
import kotlin.math.roundToInt

/**
 * 「调整今日任务量」弹层。
 *
 * 只放一个滑杆，是因为用户此刻需要的不是配置项，而是「今天轻松一点」的一个动作。
 * 拖动时实时给出预计条数，让调整是可预期的。
 */
@Composable
fun AdjustLoadSheet(
    initialMinutes: Int,
    recommendedMinutes: Int,
    taskCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onResetRecommended: () -> Unit,
) {
    var minutes by remember { mutableFloatStateOf(initialMinutes.toFloat()) }
    val rounded = minutes.roundToInt()
    val estimatedTasks = if (taskCount == 0) 0 else {
        val avg = (initialMinutes.toFloat() / taskCount).coerceAtLeast(1f)
        (rounded / avg).roundToInt().coerceAtLeast(1)
    }
    val diff = rounded - recommendedMinutes

    MeowBottomSheet(visible = true, onDismissRequest = onDismiss) {
        Text(
            text = "今天能做多久？",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "把可用时间告诉我们，算法会只安排放得下的内容，其余的顺延到明天，不会影响记忆效果。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$rounded",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.padding(horizontal = 3.dp))
            Text(
                text = "分钟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 5.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    diff < 0 -> "比推荐少 ${-diff} 分钟"
                    diff > 0 -> "比推荐多 $diff 分钟"
                    else -> "推荐容量"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Slider(
            value = minutes,
            onValueChange = { minutes = it },
            valueRange = UserSettings.MIN_CAPACITY_MINUTES.toFloat()..UserSettings.MAX_CAPACITY_MINUTES.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

        Text(
            text = if (taskCount == 0) {
                "今天还没有安排任务，调整后会按新容量重新排程。"
            } else {
                "预计安排约 $estimatedTasks 条（当前 $taskCount 条）"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeowTextButton(text = "恢复推荐", onClick = onResetRecommended)
            Spacer(Modifier.weight(1f))
            MeowOutlinedButton(text = "取消", onClick = onDismiss)
            MeowPrimaryButton(text = "按这个安排", onClick = { onConfirm(rounded) })
        }
    }
}
