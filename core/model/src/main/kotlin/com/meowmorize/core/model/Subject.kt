package com.meowmorize.core.model

import java.time.Instant

/**
 * 学科（科目）。
 *
 * 学科是背诵内容的一级归类，例如「英语」「政治」「专业课」。
 * [colorArgb] 用于在列表中提供低饱和度的色点标识，避免整屏文字难以区分。
 */
data class Subject(
    val id: Long = 0L,
    val name: String,
    val colorArgb: Int,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
    /** 学科权重：过载时用于分配复习时间（0.6 少排、1.6 多排）。 */
    val weight: Double = 1.0,
    /** 该学科的考试时间，作为学科内任务的兜底截止日期。 */
    val examAt: Instant? = null,
)

/** 预置学科配色（HyperOS 风格的柔和低饱和色）。 */
object SubjectPalette {
    val colors: List<Int> = listOf(
        0xFFFF7A45.toInt(), // 活力橙
        0xFF4C8DFF.toInt(), // 晴空蓝
        0xFF34C77B.toInt(), // 薄荷绿
        0xFFB26BFF.toInt(), // 藤紫
        0xFFFFB020.toInt(), // 琥珀黄
        0xFFFF5C8A.toInt(), // 樱花粉
        0xFF20C4C4.toInt(), // 湖水青
    )

    fun colorFor(index: Int): Int = colors[index.mod(colors.size)]
}
