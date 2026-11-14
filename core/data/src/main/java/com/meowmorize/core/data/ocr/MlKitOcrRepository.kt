package com.meowmorize.core.data.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
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
 * 现实约束：ML Kit 依赖 Google Play 服务，部分国产 ROM / 无 GMS 设备上不可用。
 * 所以这里只负责「能力上报 + 单次识别」，不可用时由界面引导用户走
 * 「相册取字后粘贴」的兜底路径，而不是让按钮静默失败。
 */
@Singleton
class MlKitOcrRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrRepository {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override fun availability(): OcrAvailability {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return when (status) {
            ConnectionResult.SUCCESS -> OcrAvailability.READY
            ConnectionResult.SERVICE_MISSING,
            ConnectionResult.SERVICE_DISABLED,
            ConnectionResult.SERVICE_INVALID,
            -> OcrAvailability.PLAY_SERVICES_MISSING
            else -> OcrAvailability.UNSUPPORTED
        }
    }

    override suspend fun recognize(uriString: String): OcrResult = withContext(Dispatchers.IO) {
        if (availability() != OcrAvailability.READY) {
            return@withContext OcrResult(error = OcrError.UNAVAILABLE)
        }
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
