package com.donian.galaxy_battery.ui.rawlog

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** 섹션 제목을 감싸는 세로 막대. */
private const val EDGE = "━━━"

/** 구분자 기호에 입히는 투명도. 키보다 흐리게 보이도록 죽인다. */
private const val SEPARATOR_ALPHA = 0.6f

/**
 * 한 줄짜리 필드로 볼 수 없는 문자들.
 *
 * 원본 판정이 정규식 `.` 에 기대고 있었고, `.` 은 이 문자들을 넘지 못한다.
 * 그래서 이런 문자가 섞인 줄은 헤더도 필드도 아닌 것으로 본다.
 */
private val LINE_BREAKS = charArrayOf('\n', '\r', '\u0085', '\u2028', '\u2029')

/**
 * 로그 한 줄의 종류.
 *
 * 색을 칠하기 전에 종류를 먼저 확정해 두면, 뒤쪽 분기는 "무엇을 칠할지"만
 * 결정하면 되고 "이 줄이 무엇인지"를 다시 따지지 않아도 된다.
 */
private sealed interface LogLineKind {
    data class Section(val raw: String) : LogLineKind
    data class Blank(val raw: String) : LogLineKind
    data class Field(val parts: LineParts) : LogLineKind
    data class Plain(val raw: String) : LogLineKind
}

/**
 * 한 줄을 앞공백·키·구분자·값 네 토막으로 나눈 결과.
 * 네 토막을 그대로 이어 붙이면 원래 줄과 글자 단위로 같아진다.
 */
private data class LineParts(
    val leading: String,
    val key: String,
    val separator: String,
    val value: String
)

private fun isLineBreak(ch: Char): Boolean = LINE_BREAKS.any { it == ch }

/**
 * 정규식 `\s` 가 공백으로 인정하는 문자.
 *
 * 유니코드 공백(줄바꿈 없는 공백 등)은 여기에 들지 않는다. 원본 판정이
 * 정규식이었으므로 같은 기준을 그대로 쓴다.
 */
private fun isRegexSpace(ch: Char): Boolean = when (ch) {
    ' ', '\t', '\u000B', '\u000C', '\r', '\n' -> true
    else -> false
}

/** 앞뒤 공백을 걷어낸 문자열이 "━━━ 내용 ━━━" 꼴인지 본다. */
private fun isSectionHeader(bare: String): Boolean {
    val head = "$EDGE "
    val tail = " $EDGE"
    val innerLength = bare.length - head.length - tail.length
    if (innerLength < 1) return false
    if (!bare.startsWith(head)) return false
    if (!bare.endsWith(tail)) return false
    val inner = bare.substring(head.length, head.length + innerLength)
    return !inner.any(::isLineBreak)
}

/** [from] 부터 공백을 건너뛴 자리가 구분자 기호면 그 위치를 돌려준다. */
private fun separatorAt(raw: String, from: Int): Int? {
    var cursor = from
    while (cursor < raw.length && isRegexSpace(raw[cursor])) cursor++
    if (cursor >= raw.length) return null
    val ch = raw[cursor]
    return if (ch == ':' || ch == '=') cursor else null
}

/**
 * [marker] 뒤에서 값이 시작하는 자리를 정한다.
 *
 * 구분자 뒤 공백은 구분자 쪽에 붙이고, 남는 글자가 하나도 없으면
 * 마지막 공백 한 칸을 값으로 넘긴다. 값이 아예 비면 null.
 */
private fun valueStartAfter(raw: String, marker: Int): Int? {
    var cursor = marker + 1
    while (cursor < raw.length && isRegexSpace(raw[cursor])) cursor++
    if (cursor < raw.length) return cursor
    if (raw.length - 1 > marker) return raw.length - 1
    return null
}

/**
 * 한 줄을 네 토막으로 나눈다. 나눌 수 없으면 null.
 *
 * 앞공백을 최대한 가져간 상태로 먼저 시도하고, 그 상태에서 구분자를
 * 찾지 못하면 앞공백을 한 칸씩 되돌려 다시 본다. 앞공백 한 칸을 키로
 * 삼켜야만 나뉘는 줄(예: " :값")이 그런 경우다.
 */
private fun splitKeyValue(raw: String): LineParts? {
    if (raw.any(::isLineBreak)) return null

    val firstText = raw.indexOfFirst { !isRegexSpace(it) }
    val widestLead = if (firstText < 0) raw.length else firstText

    for (lead in widestLead downTo 0) {
        for (keyEnd in lead + 1 until raw.length) {
            val marker = separatorAt(raw, keyEnd) ?: continue
            val valueStart = valueStartAfter(raw, marker) ?: continue
            return LineParts(
                leading = raw.substring(0, lead),
                key = raw.substring(lead, keyEnd),
                separator = raw.substring(keyEnd, valueStart),
                value = raw.substring(valueStart)
            )
        }
    }
    return null
}

private fun classifyLogLine(raw: String): LogLineKind {
    val bare = raw.trim()
    return when {
        isSectionHeader(bare) -> LogLineKind.Section(raw)
        bare.isEmpty() -> LogLineKind.Blank(raw)
        else -> splitKeyValue(raw)?.let(LogLineKind::Field) ?: LogLineKind.Plain(raw)
    }
}

/** 나눈 토막에 색을 입힌다. 앞공백과 키는 같은 색이라 한 번에 칠한다. */
private fun AnnotatedString.Builder.paintField(
    parts: LineParts,
    keyColor: Color,
    valueColor: Color
) {
    withStyle(SpanStyle(color = keyColor)) {
        append(parts.leading)
        append(parts.key)
    }
    withStyle(SpanStyle(color = keyColor.copy(alpha = SEPARATOR_ALPHA))) {
        append(parts.separator)
    }
    withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Medium)) {
        append(parts.value)
    }
}

fun buildColorizedLogLine(
    line: String,
    keyColor: Color,
    valueColor: Color,
    sectionColor: Color
): AnnotatedString = buildAnnotatedString {
    when (val kind = classifyLogLine(line)) {
        is LogLineKind.Section ->
            withStyle(SpanStyle(color = sectionColor, fontWeight = FontWeight.SemiBold)) {
                append(kind.raw)
            }
        is LogLineKind.Blank -> append(kind.raw)
        is LogLineKind.Field -> paintField(kind.parts, keyColor, valueColor)
        is LogLineKind.Plain ->
            withStyle(SpanStyle(color = keyColor)) { append(kind.raw) }
    }
}

/** 겹치지 않는 일치 위치를 앞에서부터 모은다. [advance] 만큼씩 전진한다. */
private fun matchOffsets(haystack: String, needle: String, advance: Int): List<Int> =
    buildList {
        var cursor = 0
        while (cursor <= haystack.length - needle.length) {
            val hit = haystack.indexOf(needle, cursor)
            if (hit < 0) return@buildList
            add(hit)
            cursor = hit + advance
        }
    }

fun buildSearchHighlightedText(
    base: AnnotatedString,
    query: String,
    highlightBg: Color,
    highlightFg: Color
): AnnotatedString {
    if (query.isBlank()) return base
    val haystack = base.text.lowercase()
    val needle = query.lowercase()
    if (!haystack.contains(needle)) return base

    val hits = matchOffsets(haystack, needle, query.length)
    if (hits.isEmpty()) return base

    val mark = SpanStyle(background = highlightBg, color = highlightFg)
    return buildAnnotatedString {
        append(base)
        for (hit in hits) addStyle(mark, start = hit, end = hit + query.length)
    }
}
