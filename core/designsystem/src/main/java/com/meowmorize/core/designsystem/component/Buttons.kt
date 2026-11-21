package com.meowmorize.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.meowmorize.core.designsystem.motion.meowPressScale
import com.meowmorize.core.designsystem.theme.LocalAppStyle
import com.meowmorize.core.model.AppStyle

/** 主操作按钮：胶囊形 + 橙色微渐变，是整个界面最强的视觉锚点。 */
@Composable
fun MeowPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val terminal = LocalAppStyle.current == AppStyle.TERMINAL
    val start = if (enabled) containerColor else containerColor.copy(alpha = 0.35f)
    val end = if (enabled) containerColor.copy(alpha = 0.82f) else containerColor.copy(alpha = 0.25f)
    // 终端风格禁止装饰性渐变：主按钮就是一块纯色，边界靠描边与字重
    val shape = if (terminal) MaterialTheme.shapes.small else CircleShape
    val interaction = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val fill = if (terminal) {
        Modifier.background(start)
    } else {
        Modifier.background(Brush.horizontalGradient(listOf(start, end)))
    }
    Row(
        modifier = modifier
            .meowPressScale(interactionSource = interaction, pressedScale = 0.97f)
            .clip(shape)
            .then(fill)
            .clickable(
                interactionSource = interaction,
                indication = indication,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.6f),
        )
    }
}

/** 次级按钮：描边 + 透明底，用于「稍后」「取消」等非主路径动作。 */
@Composable
fun MeowOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .meowPressScale(interactionSource = interaction, pressedScale = 0.97f)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = indication,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else contentColor.copy(alpha = 0.4f),
        )
    }
}

/** 纯文字动作，用于卡片内「编辑」「归档」这类低频操作。 */
@Composable
fun MeowTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .meowPressScale(interactionSource = interaction, pressedScale = 0.96f)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 圆形图标按钮（返回、更多等）。 */
@Composable
fun MeowIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .meowPressScale(interactionSource = interaction, pressedScale = 0.92f)
            .background(containerColor)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}
