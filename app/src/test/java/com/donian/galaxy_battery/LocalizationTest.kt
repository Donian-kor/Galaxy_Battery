package com.donian.galaxy_battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class LocalizationTest {

    private val minute = 60_000L
    private val hour   = 60 * minute
    private val day    = 24 * hour

    // ── Time since a log was created ──────────────────────────────────────────

    @Test
    fun `under a minute is just now`() {
        assertNull(timeAgo(0))
        assertNull(timeAgo(minute - 1))
    }

    @Test
    fun `a time in the future is just now`() {
        assertNull(timeAgo(-5 * day))
    }

    @Test
    fun `minutes run up to an hour`() {
        assertEquals(TimeAgo(R.plurals.time_minutes, 1), timeAgo(minute))
        assertEquals(TimeAgo(R.plurals.time_minutes, 59), timeAgo(hour - 1))
    }

    @Test
    fun `hours run up to a day`() {
        assertEquals(TimeAgo(R.plurals.time_hours, 1), timeAgo(hour))
        assertEquals(TimeAgo(R.plurals.time_hours, 23), timeAgo(day - 1))
    }

    @Test
    fun `days run up to thirty`() {
        assertEquals(TimeAgo(R.plurals.time_days, 1), timeAgo(day))
        assertEquals(TimeAgo(R.plurals.time_days, 29), timeAgo(30 * day - 1))
    }

    @Test
    fun `months run up to a year`() {
        assertEquals(TimeAgo(R.plurals.time_months, 1), timeAgo(30 * day))
        assertEquals(TimeAgo(R.plurals.time_months, 12), timeAgo(365 * day - 1))
    }

    @Test
    fun `years have no upper limit`() {
        assertEquals(TimeAgo(R.plurals.time_years, 1), timeAgo(365 * day))
        assertEquals(TimeAgo(R.plurals.time_years, 101), timeAgo(37_000 * day))
    }

    // ── Battery date ──────────────────────────────────────────────────────────

    private val date = requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse("20240105")).time

    @Test
    fun `an unknown date is empty`() {
        assertEquals("", formatBatteryDate(0))
    }

    @Test
    fun `date is day month year`() {
        assertEquals("5/1/2024", formatBatteryDate(date))
    }

    @Test
    fun `date is formatted in korean style when korean is true`() {
        assertEquals("2024년 1월 5일", formatBatteryDate(date, korean = true))
    }
}
