package com.meowmorize.core.domain.repository

import java.time.Duration
import java.time.Instant

/**
 * 可调时钟（时间快进）。
 *
 * 遗忘曲线类应用的完整流程要跨越几天甚至几周，等真实时间没法验证「第 7 天会不会
 * 雪崩」这类问题。这里提供一个显式的、可复位的时间偏移，让测试和使用者都能
 * 把应用拨到未来某一天。
 *
 * 偏移量会持久化，进程重启后仍然有效——否则「快进 3 天再杀进程」就穿帮了。
 */
interface TimeTravelRepository {
    /** 当前偏移（毫秒）。正数表示把时间往前拨。 */
    fun offsetMillis(): Long

    /** 当前的虚拟时间。 */
    fun virtualNow(): Instant

    /** 快进一段时间。 */
    suspend fun advanceBy(duration: Duration)

    /** 跳到某个绝对时间点（按设备当前时区解释）。 */
    suspend fun jumpTo(target: Instant)

    /** 回到真实时间。 */
    suspend fun reset()
}
