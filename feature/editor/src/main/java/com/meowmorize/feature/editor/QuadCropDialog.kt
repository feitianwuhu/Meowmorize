package com.meowmorize.feature.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import com.meowmorize.core.designsystem.component.MeowOutlinedButton
import com.meowmorize.core.designsystem.component.MeowPrimaryButton
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 四点框选 + 透视矫正。
 *
 * 为什么需要它：手机拍书页几乎不可能正对，页面在画面里是梯形。整图识别会把
 * 倾斜的文字行揉在一起，错字率明显上升。让用户点四个角、把梯形「摊平」成矩形，
 * 再送去识别，效果提升比换识别引擎更直接。
 *
 * 交互刻意做成「只拖四个角」而不是「拖一个可旋转的框」：角点只约束透视，
 * 不需要再引入旋转与缩放两套手势，误操作少。
 */
@Composable
fun QuadCropDialog(
    image: ImageBitmap,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap) -> Unit,
) {
    // 四个角的归一化坐标，顺序固定为 左上 / 右上 / 右下 / 左下
    var corners by remember(image) {
        mutableStateOf(
            listOf(
                Offset(0.02f, 0.02f),
                Offset(0.98f, 0.02f),
                Offset(0.98f, 0.98f),
                Offset(0.02f, 0.98f),
            ),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "拖动四个角，框住要识别的那块文字",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(
                text = "斜着拍的书页也能被摊平——四个角按顺时针、贴住文字的四个角即可",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 12.dp),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val containerW = constraints.maxWidth.toFloat()
                val containerH = constraints.maxHeight.toFloat()
                // 与 ContentScale.Fit 一致：居中、按比例缩放
                val scale = min(
                    containerW / image.width.toFloat(),
                    containerH / image.height.toFloat(),
                )
                val drawW = image.width * scale
                val drawH = image.height * scale
                val originX = (containerW - drawW) / 2f
                val originY = (containerH - drawH) / 2f

                var dragging by remember { mutableStateOf(-1) }

                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { drawW.toDp() },
                            height = with(density) { drawH.toDp() },
                        )
                        .pointerInput(image) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    // 取最近的角作为本次拖拽目标
                                    val nearest = corners
                                        .mapIndexed { index, point ->
                                            index to hypot(
                                                offset.x - point.x * drawW,
                                                offset.y - point.y * drawH,
                                            )
                                        }
                                        .minByOrNull { it.second }
                                    // 阈值放宽到 96px：手指比手柄粗，太严会「拖不动」
                                    dragging = if (nearest != null && nearest.second < 96f) {
                                        nearest.first
                                    } else {
                                        -1
                                    }
                                },
                                onDragEnd = { dragging = -1 },
                                onDragCancel = { dragging = -1 },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (dragging < 0) return@detectDragGestures
                                    val current = corners[dragging]
                                    corners = corners.toMutableList().also { list ->
                                        list[dragging] = Offset(
                                            (current.x + dragAmount.x / drawW).coerceIn(0f, 1f),
                                            (current.y + dragAmount.y / drawH).coerceIn(0f, 1f),
                                        )
                                    }
                                },
                            )
                        },
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val points = corners.map { Offset(it.x * size.width, it.y * size.height) }
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
                            close()
                        }
                        // 框外压暗，视线自然聚焦到选区
                        drawPath(
                            path = Path().apply {
                                addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                                addPath(path)
                                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                            },
                            color = Color.Black.copy(alpha = 0.55f),
                        )
                        drawPath(path = path, color = Color.White, style = Stroke(width = 3f))
                        points.forEach { point ->
                            drawCircle(color = Color.White, radius = 16f, center = point)
                            drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = 7f, center = point)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MeowOutlinedButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    contentColor = Color.White,
                )
                MeowPrimaryButton(
                    text = "矫正并识别",
                    onClick = {
                        onConfirm(
                            warpToRect(
                                source = image.asAndroidBitmap(),
                                corners = corners,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

/**
 * 四点透视变换：把四边形摊平成矩形。
 *
 * 输出尺寸取「对边长度的较大值」，避免摊平后被压缩变形；
 * 采样用双线性过滤，缩放后的文字边缘更干净。
 */
fun warpToRect(source: Bitmap, corners: List<Offset>): Bitmap {
    val w = source.width.toFloat()
    val h = source.height.toFloat()
    val src = floatArrayOf(
        corners[0].x * w, corners[0].y * h,
        corners[1].x * w, corners[1].y * h,
        corners[2].x * w, corners[2].y * h,
        corners[3].x * w, corners[3].y * h,
    )

    fun distance(ax: Float, ay: Float, bx: Float, by: Float) = hypot(ax - bx, ay - by)
    val width = max(
        distance(src[0], src[1], src[2], src[3]),
        distance(src[6], src[7], src[8], src[9]),
    )
    val height = max(
        distance(src[0], src[1], src[6], src[7]),
        distance(src[2], src[3], src[8], src[9]),
    )
    val outW = width.roundToInt().coerceIn(32, 4096)
    val outH = height.roundToInt().coerceIn(32, 4096)

    val dst = floatArrayOf(
        0f, 0f,
        outW.toFloat(), 0f,
        outW.toFloat(), outH.toFloat(),
        0f, outH.toFloat(),
    )
    val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
    val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(output).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
    return output
}

/** 按最长边降采样解码：书页照片动辄 4000px，全尺寸解码会在中端机上卡住。 */
fun loadSampledBitmap(
    context: android.content.Context,
    uriString: String,
    maxDimension: Int = 1600,
): Bitmap? = runCatching {
    val uri = android.net.Uri.parse(uriString)
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, bounds)
    }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
    context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(
            it,
            null,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }
}.getOrNull()
