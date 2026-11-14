package com.meowmorize.core.common.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 纯 Kotlin 的展示格式化工具，避免 UI 层到处写魔法字符串。 */
object Formatters {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA)

    private val shortDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M/d", Locale.CHINA)

    fun dateLabel(date: LocalDate): String = date.format(dateFormatter)

    fun shortDate(date: LocalDate): String = date.format(shortDateFormatter)

    fun minutesLabel(minutes: Int): String = when {
        minutes <= 0 -> "不到 1 分钟"
        minutes < 60 -> "$minutes 分钟"
        minutes % 60 == 0 -> "${minutes / 60} 小时"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分"
    }

    fun percent(ratio: Double): String = "${(ratio * 100).roundToInt()}%"

    /** 距离某个时间点的自然语言描述，用于「下次复习：3 天后」。 */
    fun relativeToNow(target: Instant, now: Instant): String {
        val duration = Duration.between(now, target)
        val days = duration.toDays()
        val hours = duration.toHours()
        return when {
            days >= 30 -> "${days / 30} 个月后"
            days >= 2 -> "$days 天后"
            days == 1L -> "明天"
            days == 0L && hours >= 1 -> "$hours 小时后"
            duration.isNegative -> {
                val overdueDays = -days
                if (overdueDays <= 0) "就是现在" else "已逾期 $overdueDays 天"
            }
            else -> "稍后"
        }
    }

    /** 根据小时给出问候语，让首页语气软一点。 */
    fun greeting(hour: Int): String = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
}
