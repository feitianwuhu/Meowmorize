package com.meowmorize.core.domain.text

import kotlin.math.ceil

/**
 * 估算「背一遍需要多久」。
 *
 * 用户填写内容后自动给出时长，是降低录入负担的关键细节：
 * 大多数人并不知道自己背一段话要几分钟，但知道「大概两百字」。
 * 估算规则：中文阅读约 200 字/分钟，背诵需要反复，乘 2 倍，再留 1 分钟准备时间。
 */
class DurationEstimator(
    private val charsPerMinute: Int = 200,
    private val recitationMultiplier: Double = 2.0,
    private val setupMinutes: Int = 1,
) {
    fun estimate(content: String, explicitMinutes: Int? = null): Int {
        if (explicitMinutes != null && explicitMinutes > 0) return explicitMinutes.coerceIn(MIN, MAX)
        val chars = content.trim().count { !it.isWhitespace() }
        if (chars == 0) return MIN
        val reading = chars.toDouble() / charsPerMinute
        val minutes = ceil(reading * recitationMultiplier).toInt() + setupMinutes
        return minutes.coerceIn(MIN, MAX)
    }

    /** 为内容给出「长/中/短」提示，UI 可据此展示。 */
    fun sizeLabel(content: String): String {
        val chars = content.trim().count { !it.isWhitespace() }
        return when {
            chars == 0 -> "空内容"
            chars < 100 -> "短内容 · 约 $chars 字"
            chars < 400 -> "中等内容 · 约 $chars 字"
            else -> "长内容 · 约 $chars 字"
        }
    }

    companion object {
        const val MIN = 3
        const val MAX = 60
    }
}
