package com.meowmorize.core.domain.srs

/**
 * 间隔抖动（fuzz）。
 *
 * 为什么不用 `Random`：复习页按钮上的「7 天后」和点击后真正落库的间隔必须一致，
 * 否则用户会觉得「你写的和做的不一样」。这里用 (itemId, reps) 作为种子的确定性哈希，
 * 同一张卡在同一次复习中永远得到同一个系数。
 */
object IntervalFuzz {

    const val DEFAULT_RATIO = 0.05

    fun factor(seed: Long, ratio: Double = DEFAULT_RATIO): Double {
        val mixed = mix64(seed)
        val unit = (mixed ushr 11).toDouble() / (1L shl 53).toDouble()
        return 1.0 + (unit * 2 - 1) * ratio.coerceIn(0.0, 0.2)
    }

    private fun mix64(input: Long): Long {
        var z = input + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
