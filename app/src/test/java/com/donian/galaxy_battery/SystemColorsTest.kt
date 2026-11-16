package com.donian.galaxy_battery

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Oklab 색상 계열 판정과 톤 팔레트 보간 검증.
 *
 * [sameHue]는 안드로이드 14+ 시스템 색 역할이 배경화면 팔레트를 따라오는지 판정하는 데 쓰인다.
 * 판정이 틀리면 삼성폰에서 사용자가 고른 색이 무시되므로, 경계 각도와 회색 처리를 특히 엄격히 본다.
 */
class SystemColorsTest {

    // ── sameHue ───────────────────────────────────────────────────────────────

    @Test
    fun `a colour belongs to its own hue family`() {
        assertTrue(sameHue(STOCK_BLUE, STOCK_BLUE))
    }

    @Test
    fun `lighter and darker steps of one colour share a hue`() {
        assertTrue(sameHue(Color(0xFF8B4A62), Color(0xFFFFB0CB)))
    }

    @Test
    fun `stock blue rejects red and green palettes`() {
        assertFalse(sameHue(STOCK_BLUE, Color(0xFF904A43)))
        assertFalse(sameHue(STOCK_BLUE, Color(0xFF406836)))
    }

    @Test
    fun `stock blue rejects a saturated purple palette`() {
        // 안드로이드 17 Vibrant 스타일, 보라 시드에서 기본 파랑과 30° 떨어짐
        assertFalse(sameHue(STOCK_BLUE, Color(0xFF6C39DC)))
    }

    @Test
    fun `a role a few degrees off the palette still matches`() {
        // 안드로이드 17 기본값에서 가장 벌어지는 조합: Expressive 스타일·다크·노랑 시드
        assertTrue(sameHue(Color(0xFFFFF0C5), Color(0xFFDEC56D)))
    }

    @Test
    fun `hue angles straddling zero use the shortest arc`() {
        assertTrue(sameHue(chromaColor(hue = 355f), chromaColor(hue = 5f)))
        assertFalse(sameHue(chromaColor(hue = 340f), chromaColor(hue = 10f)))
    }

    @Test
    fun `two near grays are treated as one hue`() {
        assertTrue(sameHue(Color(0xFF5E5E5E), Color(0xFF606062)))
    }

    @Test
    fun `stock blue rejects a gray palette`() {
        assertFalse(sameHue(STOCK_BLUE, Color(0xFF5E5E5E)))
    }

    // ── TonalRange ────────────────────────────────────────────────────────────

    @Test
    fun `supplied tones keep their exact lightness`() {
        val range = neutralRange()
        TonalRange.TONES.forEach { tone ->
            assertEquals(lightness(gray(tone)), lightness(range.tone(tone)), 0.01f)
        }
    }

    @Test
    fun `an interpolated tone lands on the requested lightness`() {
        val range = tintedRange()
        listOf(98, 96, 94, 92, 87, 24, 22, 17, 12, 6, 4).forEach { tone ->
            assertEquals("tone $tone", tone.toFloat(), lightness(range.tone(tone)), 0.6f)
        }
    }

    @Test
    fun `an interpolated tone keeps the palette tint`() {
        val lab = tintedRange().tone(94).convert(ColorSpaces.CieLab)
        assertEquals(0f, lab.green, 0.5f)
        assertEquals(-4f, lab.blue, 0.5f)
    }

    // ── paletteColorScheme ────────────────────────────────────────────────────

    @Test
    fun `the scheme picks the same tones Android 12 uses`() {
        val p = tintedRange()
        val light = paletteColorScheme(p, p, p, p, darkTheme = false)
        val dark  = paletteColorScheme(p, p, p, p, darkTheme = true)

        assertEquals(p.tone(40), light.primary)
        assertEquals(p.tone(80), dark.primary)
        assertEquals(98f, lightness(light.background), 0.6f)
        assertEquals(6f,  lightness(dark.background), 0.6f)
        assertEquals(94f, lightness(light.surfaceContainer), 0.6f)
        assertEquals(12f, lightness(dark.surfaceContainer), 0.6f)
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private companion object {
        /** 안드로이드 14 기본 primary 역할 색. One UI가 이 값을 그대로 남겨 둔다. */
        private val STOCK_BLUE = Color(0xFF415F91)
    }

    /** CieLab 명도(L*)를 읽는다. */
    private fun lightness(color: Color) = color.convert(ColorSpaces.CieLab).red

    /** 지정 색상각·채도를 갖는 Oklab 색을 만든다 (기본 채도 0.1). */
    private fun chromaColor(hue: Float, chroma: Float = 0.1f): Color {
        val radians = Math.toRadians(hue.toDouble())
        val a = (chroma * Math.cos(radians)).toFloat()
        val b = (chroma * Math.sin(radians)).toFloat()
        return Color(0.6f, a, b, 1f, ColorSpaces.Oklab).convert(ColorSpaces.Srgb)
    }

    /** 무채색(채도 0) 회색 계열. */
    private fun gray(tone: Int) =
        Color(tone.toFloat(), 0f, 0f, 1f, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)

    /** 안드로이드가 제공하는 전 톤의 무채색 팔레트. */
    private fun neutralRange() = TonalRange(TonalRange.TONES.map(::gray))

    /** 안드로이드 neutral variant 팔레트처럼 채도가 아주 낮은 푸른 팔레트. */
    private fun tintedRange() = TonalRange(
        TonalRange.TONES.map { tone ->
            val chroma = if (tone == 0 || tone == 100) 0f else 4f
            Color(tone.toFloat(), 0f, -chroma, 1f, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)
        }
    )
}