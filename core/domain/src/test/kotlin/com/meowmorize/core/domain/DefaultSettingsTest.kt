package com.meowmorize.core.domain

import com.meowmorize.core.model.AccentChoice
import com.meowmorize.core.model.AppStyle
import com.meowmorize.core.model.GlassTone
import com.meowmorize.core.model.UserSettings
import com.meowmorize.core.model.WallpaperScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首次使用的默认值。
 *
 * 这几条是「新人第一次打开 app 看到什么」的约定，属于产品行为，
 * 不该在后续改主题时被顺手改掉——所以用测试锁住。
 */
class DefaultSettingsTest {

    @Test
    fun `首次使用是柔角卡片`() {
        assertEquals(AppStyle.SOFT_CARD, UserSettings().appStyle)
    }

    @Test
    fun `没有存过风格或存了未知值都回落到柔角卡片`() {
        assertEquals(AppStyle.SOFT_CARD, AppStyle.fromName(null))
        assertEquals(AppStyle.SOFT_CARD, AppStyle.fromName(""))
        assertEquals(AppStyle.SOFT_CARD, AppStyle.fromName("WHATEVER"))
        assertEquals(AppStyle.SOFT_CARD, AppStyle.fromName("HYPEROS"))
    }

    @Test
    fun `已存过的风格能正确还原`() {
        assertEquals(AppStyle.GLASS, AppStyle.fromName("GLASS"))
        assertEquals(AppStyle.TERMINAL, AppStyle.fromName("TERMINAL"))
    }

    @Test
    fun `磨砂相关默认值保守`() {
        val settings = UserSettings()
        // 壁纸默认开着，但只有切到磨砂才会真正去取；其余风格不受影响
        assertTrue(settings.wallpaperEnabled)
        assertEquals(WallpaperScale.FILL, settings.wallpaperScale)
        assertEquals(GlassTone.SYSTEM, settings.glassTone)
        assertEquals(AccentChoice.SYSTEM, settings.accentChoice)
        assertTrue(settings.wallpaperBlur in 0..48)
        assertTrue(settings.wallpaperScrim in 0.0..0.7)
    }
}
