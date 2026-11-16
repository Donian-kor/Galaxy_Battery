package com.donian.galaxy_battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 상대 시간([timeAgo])과 배터리 날짜([formatBatteryDate]) 표기 검증.
 *
 * 상대 시간은 "방금"을 null로 표현하고 나머지는 plurals 리소스와 개수로 표현한다.
 * 각 단위의 상한 경계(59분/23시간/29일/12개월)가 어긋나면 화면 표기가 통째로 달라지므로
 * 경계값을 하나씩 고정한다.
 */
class LocalizationTest {

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR   = 60 * MINUTE
        const val DAY    = 24 * HOUR

        /** 배터리 제조일로 쓰이는 2024년 1월 5일. */
        val SAMPLE_DATE_MS: Long =
            requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse("20240105")).time
    }

    // ── timeAgo ───────────────────────────────────────────────────────────────

    @Test
    fun `under one minute reports null`() {
        assertNull(timeAgo(0))
        assertNull(timeAgo(MINUTE - 1))
    }

    @Test
    fun `a future timestamp also reports null`() {
        assertNull(timeAgo(-5 * DAY))
    }

    @Test
    fun `minutes span one up to fifty nine`() {
        assertEquals(TimeAgo(R.plurals.time_minutes, 1),  timeAgo(MINUTE))
        assertEquals(TimeAgo(R.plurals.time_minutes, 59), timeAgo(HOUR - 1))
    }

    @Test
    fun `hours span one up to twenty three`() {
        assertEquals(TimeAgo(R.plurals.time_hours, 1),  timeAgo(HOUR))
        assertEquals(TimeAgo(R.plurals.time_hours, 23), timeAgo(DAY - 1))
    }

    @Test
    fun `days span one up to twenty nine`() {
        assertEquals(TimeAgo(R.plurals.time_days, 1),  timeAgo(DAY))
        assertEquals(TimeAgo(R.plurals.time_days, 29), timeAgo(30 * DAY - 1))
    }

    @Test
    fun `months span one up to twelve`() {
        assertEquals(TimeAgo(R.plurals.time_months, 1),  timeAgo(30 * DAY))
        assertEquals(TimeAgo(R.plurals.time_months, 12), timeAgo(365 * DAY - 1))
    }

    @Test
    fun `years have no upper bound`() {
        assertEquals(TimeAgo(R.plurals.time_years, 1),   timeAgo(365 * DAY))
        assertEquals(TimeAgo(R.plurals.time_years, 101), timeAgo(37_000 * DAY))
    }

    // ── formatBatteryDate ─────────────────────────────────────────────────────

    @Test
    fun `an unknown date formats to an empty string`() {
        assertEquals("", formatBatteryDate(0))
    }

    @Test
    fun `the default pattern is day then month then year`() {
        assertEquals("5/1/2024", formatBatteryDate(SAMPLE_DATE_MS))
    }

    @Test
    fun `the korean pattern spells out the year and month`() {
        assertEquals("2024년 1월 5일", formatBatteryDate(SAMPLE_DATE_MS, korean = true))
    }
}