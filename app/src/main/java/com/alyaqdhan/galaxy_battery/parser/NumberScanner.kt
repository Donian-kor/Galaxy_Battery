package com.alyaqdhan.galaxy_battery.parser

/**
 * 덤프 로그 줄에서 정규식 없이 정수를 뽑는 스캐너.
 *
 * 덤프 로그는 수십 MB라 줄마다 정규식을 여러 개 돌리면 폴더 선택이 체감될 만큼 느려진다.
 * 여기서는 `indexOf`와 문자 검사만 쓴다.
 */
internal object NumberScanner {

    /** `key`로 시작하는 줄에서 첫 정수를 읽는다. (`^key:\s*(-?\d+)` 대체) */
    fun leadingNumber(line: String, key: String): Int? {
        if (!line.startsWith(key)) return null
        var i = key.length
        while (i < line.length && line[i].isWhitespace()) i++
        return numberAt(line, i, allowNegative = true)
    }

    /** `key` 뒤에 붙은 정수를, 단어 경계에 있는 것부터 찾는다. (`key:(-?\d+)` 대체) */
    fun inlineNumber(line: String, key: String, allowNegative: Boolean = true): Int? {
        var searchFrom = 0
        while (true) {
            val at = line.indexOf(key, searchFrom)
            if (at < 0) return null
            if (at == 0 || !line[at - 1].isLetterOrDigit()) {
                numberAt(line, at + key.length, allowNegative)?.let { return it }
            }
            searchFrom = at + 1
        }
    }

    /** [start] 위치에서 `-?\d+`를 읽는다. 숫자가 없으면 null. */
    private fun numberAt(line: String, start: Int, allowNegative: Boolean): Int? {
        var i = start
        var negative = false
        if (allowNegative && i < line.length && line[i] == '-') {
            negative = true
            i++
        }
        val digitsStart = i
        while (i < line.length && line[i] in '0'..'9') i++
        if (i == digitsStart) return null
        val text = if (negative) "-${line.substring(digitsStart, i)}" else line.substring(digitsStart, i)
        return text.toIntOrNull()
    }

    /** 충전 카운터는 µAh로 오지만 일부 기기는 mAh로 찍는다. */
    fun microAhToMah(value: Int?): Int? {
        if (value == null || value <= 0) return null
        return if (value > 20_000) (value / 1000).takeIf { it > 0 } else value
    }
}