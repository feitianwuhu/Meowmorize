package com.meowmorize.core.domain.srs

import com.meowmorize.core.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置 → 排程参数的映射。
 *
 * 这里最重要的一条是「默认设置必须等于默认参数」：
 * 设置项是后来加的，一旦默认值和算法默认值不一致，老用户升级后行为会悄悄变化。
 */
class SrsPolicyTest {

    @Test
    fun `默认设置映射出的参数与算法默认值完全一致`() {
        assertEquals(SrsConfig(), SrsPolicy.from(UserSettings()))
    }

    @Test
    fun `设置项逐项映射到参数`() {
        val config = SrsPolicy.from(
            UserSettings(
                maxIntervalDays = 90,
                intervalFuzzEnabled = true,
                difficultyReversion = 0.12,
                sameDayRepeatLimit = 5,
                overdueMaxBonus = 0.2,
            ),
        )
        assertEquals(90, config.maxIntervalDays)
        assertTrue(config.fuzzEnabled)
        assertEquals(0.12, config.difficultyReversion, 1e-9)
        assertEquals(5, config.sameDayRepeatLimit)
        assertEquals(0.2, config.overdueMaxBonus, 1e-9)
    }
}
