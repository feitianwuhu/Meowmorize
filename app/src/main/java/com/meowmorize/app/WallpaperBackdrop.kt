package com.meowmorize.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.meowmorize.core.model.WallpaperScale
import java.io.File

/**
 * 壁纸层。
 *
 * 三个刻意的选择：
 * 1. **先模糊再叠加遮罩**。背诵时背景里任何细节都是干扰，默认 24dp 的模糊比 iOS
 *    那套浅磨砂重得多；遮罩再压一层，保证白色文字在亮照片上也能读。
 * 2. **按屏幕尺寸挑图**（仓库层做），解码时再按目标尺寸降采样，避免 4K 原图吃内存。
 * 3. `Modifier.blur` 在 API 31 以下不生效，此时靠遮罩保证可读性。
 */
@Composable
fun WallpaperBackdrop(
    file: File?,
    scale: WallpaperScale,
    blurDp: Int,
    scrim: Float,
    /** 遮罩颜色：暗磨砂压黑（提亮文字），亮磨砂压白（压暗文字）。 */
    scrimColor: Color = Color.Black,
    modifier: Modifier = Modifier,
) {
    if (file == null || !file.exists()) return

    // 解码放到 IO 线程：一张 2K 图在主线程解码要几十毫秒，正好卡在首帧上。
    // 用 produceState 而不是 remember：remember 里做 IO 等于把耗时塞进组合。
    val painter by produceState<BitmapPainter?>(
        initialValue = null,
        file.path,
        file.lastModified(),
    ) {
        value = withContext(Dispatchers.IO) { decodeWallpaper(file) }
    }
    val ready = painter ?: return

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = ready,
            contentDescription = null,
            contentScale = when (scale) {
                WallpaperScale.FILL -> ContentScale.Crop
                WallpaperScale.FIT -> ContentScale.Fit
                WallpaperScale.STRETCH -> ContentScale.FillBounds
                WallpaperScale.CENTER -> ContentScale.None
            },
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurDp > 0) Modifier.blur(blurDp.dp) else Modifier),
        )
        if (scrim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrim.coerceIn(0f, 0.8f))),
            )
        }
    }
}


/** 按目标尺寸降采样解码，避免 4K 原图吃内存。 */
private fun decodeWallpaper(file: File): BitmapPainter? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
    var sample = 1
    while (longest / sample > 2200) sample *= 2
    BitmapFactory.decodeFile(
        file.path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )?.let { BitmapPainter(it.asImageBitmap()) }
}.getOrNull()
