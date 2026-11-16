package com.donian.galaxy_battery

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class TimeAgo(@param:PluralsRes val plural: Int, val count: Int)

/** 숫자 문자열을 그대로 반환 (아랍어 숫자 변환 제거됨) */
fun String.localDigits(): String = this

/**
 * 경과 시간(ms)을 [TimeAgo] 단위로 환산한다.
 *
 * - 1분 미만이면 "방금"을 뜻하는 null 을 돌려준다.
 * - 미래 시각(음수)도 마찬가지로 null 이다.
 *
 * 각 단위는 [TimeUnit]의 고정 상수 변환으로 계산하며,
 * 경계(59분/23시간/29일/12개월)는 원본과 동일하게 미만(minutes<60 등)으로 처리한다.
 */
internal fun timeAgo(diffMs: Long): TimeAgo? {
    val mins   = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours  = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days   = TimeUnit.MILLISECONDS.toDays(diffMs)
    val months = days / 30
    val years  = days / 365

    // 단위 경계를 오름차순으로 검사해, 처음으로 상한을 넘지 않는 단위를 채택한다.
    if (mins < 1L)  return null
    if (mins < 60L) return TimeAgo(R.plurals.time_minutes, mins.toInt())
    if (hours < 24L) return TimeAgo(R.plurals.time_hours, hours.toInt())
    if (days < 30L)  return TimeAgo(R.plurals.time_days, days.toInt())
    if (years < 1L)  return TimeAgo(R.plurals.time_months, months.toInt())
    return TimeAgo(R.plurals.time_years, years.toInt())
}

/** "3 days ago", 또는 [timestampMs]를 모르면(0) 빈 문자열. */
@Composable
fun relativeTimeText(timestampMs: Long): String {
    if (timestampMs == 0L) return ""
    val elapsed = System.currentTimeMillis() - timestampMs
    val ago = timeAgo(elapsed) ?: return stringResource(R.string.time_just_now)
    return pluralStringResource(ago.plural, ago.count, ago.count)
}

/** 3자리마다 쉼표를 넣은 숫자 문자열. */
fun Int.groupedDigits(): String = String.format(Locale.US, "%,d", this)

/** 로그가 알려준 날짜로부터 지난 개월 수. 날짜를 모르면(<=0) null. */
internal fun monthsSince(dateMs: Long): Int? {
    if (dateMs <= 0L) return null
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = dateMs }
    var months = (now.get(Calendar.YEAR) - then.get(Calendar.YEAR)) * 12 +
            (now.get(Calendar.MONTH) - then.get(Calendar.MONTH))
    // 같은 달이라도 날짜가 아직 안 지났으면 한 달 덜 센다.
    if (now.get(Calendar.DAY_OF_MONTH) < then.get(Calendar.DAY_OF_MONTH)) months--
    return months.coerceAtLeast(0)
}

/** "오늘 14:32:08" / "어제 …" / "2025-01-02 …". 시각을 모르면(0) 빈 문자열. */
@Composable
fun logMomentText(timestampMs: Long): String {
    if (timestampMs == 0L) return ""
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestampMs))
    val now  = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestampMs }

    val sameYear  = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    if (sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)) {
        return stringResource(R.string.log_moment_today, time)
    }

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    ) {
        return stringResource(R.string.log_moment_yesterday, time)
    }

    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMs))
    return stringResource(R.string.log_moment_date, date, time)
}

/** [korean]이면 "2024년 1월 5일", 아니면 "5/1/2024". */
internal fun formatBatteryDate(dateMs: Long, korean: Boolean = false): String {
    if (dateMs == 0L) return ""
    val pattern = if (korean) "yyyy년 M월 d일" else "d/M/yyyy"
    return SimpleDateFormat(pattern, Locale.US).format(Date(dateMs))
}

/**
 * 8자리 날짜(배터리 제조일 또는 기기 최초사용일)를 현재 로케일 형식으로 바꾼다.
 * 0이면 로그에 그 값이 없다는 뜻이므로 빈 문자열.
 */
@Composable
fun batteryDateText(dateMs: Long): String {
    val korean = LocalConfiguration.current.locales[0].language == "ko"
    return formatBatteryDate(dateMs, korean)
}
