package com.alyaqdhan.galaxy_battery

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal data class TimeAgo(@param:PluralsRes val plural: Int, val count: Int)

/** 숫자 문자열을 그대로 반환 (아랍어 숫자 변환 제거됨) */
fun String.localDigits(): String = this

/** Returns null when the difference is under a minute ("just now"). */
internal fun timeAgo(diffMs: Long): TimeAgo? {
    val mins   = diffMs / 60_000
    val hours  = diffMs / 3_600_000
    val days   = diffMs / 86_400_000
    val months = days / 30
    val years  = days / 365
    return when {
        mins  < 1  -> null
        mins  < 60 -> TimeAgo(R.plurals.time_minutes, mins.toInt())
        hours < 24 -> TimeAgo(R.plurals.time_hours, hours.toInt())
        days  < 30 -> TimeAgo(R.plurals.time_days, days.toInt())
        years < 1  -> TimeAgo(R.plurals.time_months, months.toInt())
        else       -> TimeAgo(R.plurals.time_years, years.toInt())
    }
}

/** "3 days ago", or an empty string when [timestampMs] is unknown (0). */
@Composable
fun relativeTimeText(timestampMs: Long): String {
    if (timestampMs == 0L) return ""
    val ago = timeAgo(System.currentTimeMillis() - timestampMs)
        ?: return stringResource(R.string.time_just_now)
    return pluralStringResource(ago.plural, ago.count, ago.count)
}

/** A telemetry number with thousands separators. */
fun Int.groupedDigits(): String = String.format(Locale.US, "%,d", this)

/** Whole months since [dateMs], or null when the log didn't report a date. */
internal fun monthsSince(dateMs: Long): Int? {
    if (dateMs <= 0L) return null
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = dateMs }
    var months = (now.get(Calendar.YEAR) - then.get(Calendar.YEAR)) * 12 +
            (now.get(Calendar.MONTH) - then.get(Calendar.MONTH))
    if (now.get(Calendar.DAY_OF_MONTH) < then.get(Calendar.DAY_OF_MONTH)) months--
    return months.coerceAtLeast(0)
}

/** "오늘 14:32:08" / "어제 …" / "2025-01-02 …", or empty when the timestamp is unknown. */
@Composable
fun logMomentText(timestampMs: Long): String {
    if (timestampMs == 0L) return ""
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMs))
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestampMs }

    if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    ) return stringResource(R.string.log_moment_today, time)

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    ) return stringResource(R.string.log_moment_yesterday, time)

    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMs))
    return stringResource(R.string.log_moment_date, date, time)
}

/** [korean]이면 "2024년 1월 5일", 아니면 "5/1/2024". */
internal fun formatBatteryDate(dateMs: Long, korean: Boolean = false): String {
    if (dateMs == 0L) return ""
    val pattern = if (korean) "yyyy년 M월 d일" else "d/M/yyyy"
    return SimpleDateFormat(pattern, Locale.US).format(Date(dateMs))
}

/** The battery install date, or an empty string when the log didn't have one (0). */
@Composable
fun batteryDateText(dateMs: Long): String {
    val korean = LocalConfiguration.current.locales[0].language == "ko"
    return formatBatteryDate(dateMs, korean)
}
