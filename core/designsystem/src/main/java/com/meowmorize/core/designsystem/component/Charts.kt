package com.meowmorize.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowmorize.core.designsystem.theme.LocalMeowColors
import kotlin.math.max

/**
 * 「测试台」规格条：一排 label / value / unit 的等宽数据。
 *
 * 信息优先：没有卡片阴影、没有图标，只靠分栏线与字重建立层级；
 * 数字用等宽字体，保证多行数据上下对齐。
 */
@Immutable
data class SpecItem(
    val label: String,
    val value: String,
    val unit: String? = null,
    val accent: Color? = null,
)

@Composable
fun SpecStrip(
    items: List<SpecItem>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        items.forEachIndexed { index, item ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = item.accent ?: MaterialTheme.colorScheme.onSurface,
                )
                if (item.unit != null) {
                    Text(
                        text = item.unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (index != items.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height(38.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

/** 节标题：索引 + 英文小标 + 中文标题，收敛成「数据分区」而不是「卡片标题」。 */
@Composable
fun SectionLabel(
    index: String,
    english: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = index,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = english,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 图例：小方块 + 低对比文字，永远弱于数据本身。 */
@Composable
fun ChartLegend(
    items: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(color),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 记忆保持曲线：横轴为「今天起第 N 天」，纵轴为平均回忆概率。
 *
 * 只有一条序列，所以不填充面积；目标保持率用绿色虚线，属于「参考线」而非数据，
 * 因此不抢主曲线的视觉权重。
 */
@Composable
fun RetentionCurve(
    points: List<Float>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    target: Float = 0.9f,
) {
    val chart = LocalMeowColors.current.chart
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = chart.axis
    val gridColor = chart.grid

    Canvas(modifier = modifier.fillMaxWidth().height(156.dp)) {
        val left = 30.dp.toPx()
        val right = size.width - 6.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val axisWidth = 1f

        fun yOf(value: Float): Float = bottom - (bottom - top) * value.coerceIn(0f, 1f)
        fun xOf(index: Int): Float {
            val count = max(points.size - 1, 1)
            return left + (right - left) * index / count
        }

        // 网格与刻度：只保留 0 / 0.5 / 0.9 三条，避免干扰曲线
        listOf(0f, 0.5f, 0.9f).forEach { value ->
            val y = yOf(value)
            drawLine(gridColor, Offset(left, y), Offset(right, y), axisWidth)
            drawTextLabel(
                text = formatRatio(value),
                x = left - 6.dp.toPx(),
                y = y + 3.dp.toPx(),
                color = labelColor,
                sizePx = 9.sp.toPx(),
                alignRight = true,
            )
        }

        drawLine(axisColor, Offset(left, top), Offset(left, bottom), axisWidth)
        drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), axisWidth)

        // 目标保持率参考线
        if (target > 0f && target < 1f) {
            drawLine(
                color = chart.positive.copy(alpha = 0.55f),
                start = Offset(left, yOf(target)),
                end = Offset(right, yOf(target)),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }

        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(xOf(0), yOf(points[0]))
                for (i in 1 until points.size) lineTo(xOf(i), yOf(points[i]))
            }
            drawPath(
                path = path,
                color = chart.seriesA,
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            // 端点是「今天」的位置，用小实心点标记，不画整条曲线的投影
            points.forEachIndexed { index, value ->
                if (index == 0 || index == points.lastIndex) {
                    drawCircle(
                        color = chart.seriesA,
                        radius = 2.2.dp.toPx(),
                        center = Offset(xOf(index), yOf(value)),
                    )
                }
            }
        }

        xLabels.forEachIndexed { index, label ->
            drawTextLabel(
                text = label,
                x = xOf(index),
                y = bottom + 13.dp.toPx(),
                color = labelColor,
                sizePx = 9.sp.toPx(),
                centered = true,
            )
        }
    }
}

/** 一天的计划/完成负载。 */
@Immutable
data class LoadPoint(
    val label: String,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val isToday: Boolean = false,
)

/**
 * 手机端柱状图：每个槽位两根细柱，灰=计划、蓝=完成。
 * 数值标在柱顶外侧，柱体本身不加任何装饰。
 */
@Composable
fun LoadBars(
    points: List<LoadPoint>,
    modifier: Modifier = Modifier,
) {
    val chart = LocalMeowColors.current.chart
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = chart.axis

    Canvas(modifier = modifier.fillMaxWidth().height(150.dp)) {
        if (points.isEmpty()) return@Canvas
        val left = 26.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 16.dp.toPx()
        val bottom = size.height - 20.dp.toPx()

        val maxValue = points.maxOf { max(it.plannedMinutes, it.completedMinutes) }.coerceAtLeast(10)
        val slot = (right - left) / points.size
        val barWidth = minOf(slot * 0.24f, 10.dp.toPx())
        val gap = 3.dp.toPx()

        // 基线
        drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), 1f)
        drawLine(axisColor, Offset(left, top), Offset(left, bottom), 1f)

        // 峰值刻度，让数值有参照
        drawTextLabel(
            text = maxValue.toString(),
            x = left - 6.dp.toPx(),
            y = top + 3.dp.toPx(),
            color = labelColor,
            sizePx = 9.sp.toPx(),
            alignRight = true,
        )
        drawLine(chart.grid, Offset(left, top), Offset(right, top), 1f)

        points.forEachIndexed { index, point ->
            val center = left + slot * (index + 0.5f)
            val plannedHeight = (bottom - top) * (point.plannedMinutes.toFloat() / maxValue)
            val completedHeight = (bottom - top) * (point.completedMinutes.toFloat() / maxValue)

            if (point.plannedMinutes > 0) {
                drawRoundRect(
                    color = chart.neutral.copy(alpha = 0.45f),
                    topLeft = Offset(center - barWidth - gap / 2, bottom - plannedHeight),
                    size = Size(barWidth, plannedHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
            }
            if (point.completedMinutes > 0) {
                drawRoundRect(
                    color = chart.seriesA,
                    topLeft = Offset(center + gap / 2, bottom - completedHeight),
                    size = Size(barWidth, completedHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                )
                // 数值标在柱顶左侧，只有完成值需要被读到
                drawTextLabel(
                    text = point.completedMinutes.toString(),
                    x = center + gap / 2 + barWidth / 2,
                    y = bottom - completedHeight - 5.dp.toPx(),
                    color = if (point.isToday) chart.seriesA else labelColor,
                    sizePx = 9.sp.toPx(),
                    centered = true,
                )
            }

            drawTextLabel(
                text = point.label,
                x = center,
                y = bottom + 13.dp.toPx(),
                color = if (point.isToday) chart.seriesA else labelColor,
                sizePx = 9.sp.toPx(),
                centered = true,
            )
        }
    }
}

private fun formatRatio(value: Float): String = when {
    value <= 0f -> "0"
    else -> String.format(java.util.Locale.US, "%.1f", value)
}

/**
 * Canvas 内文本。
 *
 * 用系统 condensed 字族绘制数字与英文标签，和正文的 PingFang/MiSans 形成
 * 「西文数字 + 中文叙事」的双字族结构；字号与颜色由调用方控制，保持低对比。
 */
private fun DrawScope.drawTextLabel(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    sizePx: Float,
    centered: Boolean = false,
    alignRight: Boolean = false,
) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            textSize = sizePx
            typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
            textAlign = when {
                centered -> android.graphics.Paint.Align.CENTER
                alignRight -> android.graphics.Paint.Align.RIGHT
                else -> android.graphics.Paint.Align.LEFT
            }
        }
        canvas.nativeCanvas.drawText(text, x, y, paint)
    }
}
