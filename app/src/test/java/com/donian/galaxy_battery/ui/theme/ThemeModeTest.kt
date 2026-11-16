package com.donian.galaxy_battery.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 다크 모드 확정 규칙.
 *
 * 화면 테마는 "사용자가 고른 값이 있으면 그것, 없으면 기기 설정" 하나로 정해진다.
 * 우선순위가 뒤집히면 설정 화면에서 다크로 바꿔도 밝은 화면이 나오므로 여기서 고정한다.
 */
class ThemeModeTest {

    @Test
    fun `a chosen dark theme wins over a light device`() {
        assertTrue(resolveDarkTheme(stored = true, systemDark = false))
    }

    @Test
    fun `a chosen light theme wins over a dark device`() {
        assertFalse(resolveDarkTheme(stored = false, systemDark = true))
    }

    @Test
    fun `without a choice the device setting is used`() {
        assertTrue("기기가 다크면 다크", resolveDarkTheme(stored = null, systemDark = true))
        assertFalse("기기가 라이트면 라이트", resolveDarkTheme(stored = null, systemDark = false))
    }
}
