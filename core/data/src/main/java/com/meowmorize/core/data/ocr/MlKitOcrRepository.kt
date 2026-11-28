package com.meowmorize.core.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.meowmorize.core.domain.repository.OcrAvailability
import com.meowmorize.core.domain.repository.OcrError
import com.meowmorize.core.domain.repository.OcrRepository
import com.meowmorize.core.domain.repository.OcrResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit 端侧 OCR（中文 + 拉丁）。
 *
 * 用的是 **bundled（自带模型）** 版本，而不是 `play-services-mlkit-*` 瘦客户端：
 * 后者要求设备装有 Google Play 服务，国产 ROM 上基本等于「功能不可用」。
 * 自带模型版把识别模型打进 APK（约 20MB），完全离线，代价是包体积。
 *
 * 这个取舍是对的：OCR 的价值就在「不用手敲」，而恰恰是国内设备最需要它。
 */
@Singleton
class MlKitOcrRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrRepository {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 自带模型版不依赖外部服务，恒为可用。
     * 保留这个接口，是为了让上层不必区分「模型在包里」还是「模型在系统里」。
     */
    override fun availability(): OcrAvailability = OcrAvailability.READY

    override suspend fun recognize(uriString: String): OcrResult = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromFilePath(context, Uri.parse(uriString))
            val text = suspendCancellableCoroutine { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { result -> continuation.resume(result.text) }
                    .addOnFailureListener { error -> continuation.resumeWithException(error) }
            }
            if (text.isNullOrBlank()) OcrResult(error = OcrError.NO_TEXT) else OcrResult(text = text)
        }.getOrElse { OcrResult(error = OcrError.FAILED) }
    }
}
