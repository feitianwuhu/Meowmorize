package com.meowmorize.core.domain.srs

/**
 * 「反复遗忘」判定。
 *
 * 只做判定，不做惩罚：这类内容通常不是「记不住」，而是「写得太长/没有线索」，
 * 正确动作是提示用户改写或拆分，而不是继续加大复习量。
 */
object LeechPolicy {

    const val DEFAULT_THRESHOLD = 4

    fun isLeech(
        lapses: Int,
        reps: Int,
        threshold: Int = DEFAULT_THRESHOLD,
    ): Boolean {
        if (lapses >= threshold) return true
        // 复习次数不少、但一半以上都忘了，同样属于结构性难记
        return reps >= 6 && lapses * 2 >= reps
    }
}
