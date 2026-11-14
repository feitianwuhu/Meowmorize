package com.meowmorize.core.domain.repository

/** OCR 引擎可用性。用于在界面上如实告知「为什么这个按钮可能不管用」。 */
enum class OcrAvailability {
    READY,
    /** 设备没有 Google Play 服务（部分国产 ROM），ML Kit 无法工作。 */
    PLAY_SERVICES_MISSING,
    /** 初始化失败或系统版本不支持。 */
    UNSUPPORTED,
}

enum class OcrError {
    UNAVAILABLE,
    NO_TEXT,
    FAILED,
}

data class OcrResult(
    val text: String? = null,
    val error: OcrError? = null,
)

/**
 * 图片文字识别。
 *
 * 当前实现使用 ML Kit 端侧中文识别（**测试功能**）。
 * 抽出接口的目的：一旦某类设备没有 Play 服务，可以替换为其他引擎（例如厂商 OCR、
 * 或「复制粘贴」这一最可靠的兜底方案），而不影响编辑器的调用方。
 */
interface OcrRepository {
    fun availability(): OcrAvailability

    /** @param uriString 图片的 content:// 或 file:// 地址 */
    suspend fun recognize(uriString: String): OcrResult
}
