package com.alyaqdhan.galaxy_battery

import com.alyaqdhan.galaxy_battery.parser.DesignCapacityTable
import com.alyaqdhan.galaxy_battery.parser.LogFileNameParser
import com.alyaqdhan.galaxy_battery.parser.NumberScanner
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

object BatteryParser {

    // ── File-name helpers (delegated to LogFileNameParser) ───────────────────

    fun isDumpStateLog(name: String): Boolean = LogFileNameParser.isDumpStateLog(name)

    fun truncateLogDisplayName(name: String): String = LogFileNameParser.truncateDisplayName(name)

    fun extractTimestampFromFileName(name: String): Long = LogFileNameParser.timestampFromFileName(name)

    fun lookupDesignCapacity(modelOrFileName: String): Int? = DesignCapacityTable.lookup(modelOrFileName)

    /** Returns the log's battery sections as one block of text, or null if it has none. */
    internal fun readBatterySections(stream: InputStream): String? {
        val sections = mutableListOf<Pair<String, String>>()
        val currentSection = StringBuilder()
        var currentLabel = ""
        var state = 0

        fun closeSection() {
            sections.add(currentLabel to currentSection.trim().toString())
            currentSection.clear()
        }

        BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.forEachLine { line ->
                when (state) {
                    0 -> when {
                        line.contains("BatteryInfoBackUp") -> {
                            if (currentSection.isNotEmpty()) closeSection()
                            state = 1; currentLabel = "BatteryInfoBackUp"
                        }
                        line.contains("DUMP OF SERVICE battery:") -> {
                            if (currentSection.isNotEmpty()) closeSection()
                            state = 2; currentLabel = "Battery Service Dump"
                        }
                    }
                    1 -> {
                        if (line.isNotBlank() && !line.startsWith(" ") && !line.startsWith("\t")
                            && !line.trimStart().startsWith("m")) {
                            state = 0
                            if (line.contains("DUMP OF SERVICE battery:")) {
                                if (currentSection.isNotEmpty()) closeSection()
                                state = 2; currentLabel = "Battery Service Dump"
                            } else {
                                closeSection()
                                currentLabel = ""
                            }
                        } else {
                            currentSection.append(line).append('\n')
                        }
                    }
                    2 -> {
                        if (line.contains("DUMP OF SERVICE") && !line.contains("DUMP OF SERVICE battery:")) {
                            state = 0
                            closeSection()
                            currentLabel = ""
                        } else {
                            currentSection.append(line).append('\n')
                        }
                    }
                }
            }
            if (currentSection.isNotEmpty()) closeSection()
        }

        if (sections.isEmpty()) return null

        val logBufferRegex = Regex("""^\[\w+LogBuffer]""")
        return sections.joinToString("\n\n") { (label, content) ->
            val filtered = buildString {
                var insideBuffer = false
                content.lines().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        logBufferRegex.containsMatchIn(trimmed) -> insideBuffer = true
                        insideBuffer && trimmed.isEmpty() -> {}
                        insideBuffer && (trimmed.startsWith("[") || (!trimmed.startsWith(" ") &&
                                trimmed.isNotEmpty() && !trimmed.first().isDigit())) -> {
                            insideBuffer = false; appendLine(line)
                        }
                        insideBuffer -> {}
                        else -> appendLine(line)
                    }
                }
            }.trimEnd()
            if (label.isNotEmpty()) "━━━ $label ━━━\n\n$filtered" else filtered
        }
    }

    fun parseInputStream(stream: InputStream, fileName: String): BatteryInfo {
        var asoc: Int? = null
        var asocValue: Int? = null
        var asocSeen = false
        var asocRaw = ""
        var bsoh: Int? = null
        var bsohValue: Int? = null
        var bsohRaw = ""
        var usage: Int? = null
        var usageRaw = ""
        var batteryUsageValue: Int? = null
        var llbType = ""
        var manufactureDateMs = 0L
        var calibratedDateMs = 0L
        var firstUseDateMs = 0L
        var logTimestampMs = 0L

        // 용량 계산에 사용하는 배터리 서비스 값 (백업 블록 외부).
        var level = -1
        var chargeCounterMah: Int? = null
        var measuredCapacityMah: Int? = null
        var designCapacityMah: Int? = null

        val fullDateTimeRegex = Regex("""(?:==\s*dumpstate:|dumpstate:|Build time:)\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
        val bracketDateTimeRegex = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]""")
        val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        // LLB CAL과 LLB MAN은 서로 다른 날짜다. 한 변수에 섞으면 기종마다 값의 의미가 달라진다.
        val llbRegex = Regex("""^LLB\s+(CAL|MAN):\s*(\d{8})\s*$""", RegexOption.IGNORE_CASE)
        // `battery FirstUseDate: [20230310]` — 배터리 사용 기록 시작일.
        val firstUseDateRegex = Regex("""FirstUseDate:\s*\[(\d{8})]""")
        val compactDateFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

        // 정규식은 날짜와 LLB 두 곳에만 남기고, 값 추출은 NumberScanner로 처리한다.

        BufferedReader(InputStreamReader(stream), 1 shl 16).use { reader ->
            reader.forEachLine { line ->
                val t = line.trim()

                if (t.contains("mSavedBatteryAsoc:")) {
                    asocSeen = true
                    asocRaw = t.substringAfter("mSavedBatteryAsoc:").trim()
                    asoc = extractValueOrNull(t, "mSavedBatteryAsoc:")
                    asocValue = extractSignedValueOrNull(t, "mSavedBatteryAsoc:")
                }
                if (t.contains("mSavedBatteryBsoh:")) {
                    bsohRaw = t.substringAfter("mSavedBatteryBsoh:").trim()
                    bsoh = extractValueOrNull(t, "mSavedBatteryBsoh:")
                    bsohValue = extractSignedValueOrNull(t, "mSavedBatteryBsoh:")
                }
                if (t.contains("mSavedBatteryUsage:")) {
                    usageRaw = t.substringAfter("mSavedBatteryUsage:").trim()
                    batteryUsageValue = extractSignedValueOrNull(t, "mSavedBatteryUsage:")
                    usage = batteryUsageValue?.takeIf { it >= 0 }?.div(100)
                }
                if (t.startsWith("LLB", ignoreCase = true)) {
                    llbRegex.find(t)?.let { m ->
                        val value = parseCompactDate(m.groupValues[2], compactDateFmt)
                        when (m.groupValues[1].uppercase()) {
                            "CAL" -> {
                                llbType = "CAL"
                                calibratedDateMs = value
                            }
                            "MAN" -> {
                                if (llbType.isEmpty()) llbType = "MAN"
                                manufactureDateMs = value
                            }
                        }
                    }
                }
                if (firstUseDateMs == 0L) {
                    firstUseDateRegex.find(t)?.let { m ->
                        firstUseDateMs = parseCompactDate(m.groupValues[1], compactDateFmt)
                    }
                }
                if (logTimestampMs == 0L && t.length >= 19 && looksLikeTimestampLine(t)) {
                    fullDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    } ?: bracketDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    }
                }

                // ── Live battery service telemetry ────────────────────────────
                if (level < 0) NumberScanner.leadingNumber(t, "level:")?.let { level = it }

                if (chargeCounterMah == null)
                    (NumberScanner.leadingNumber(t, "charge counter:")
                        ?: NumberScanner.leadingNumber(t, "Charge counter:"))
                        ?.let { chargeCounterMah = NumberScanner.microAhToMah(it) }
                if (measuredCapacityMah == null)
                    NumberScanner.leadingNumber(t, "Maximum capacity:")
                        ?.let { measuredCapacityMah = NumberScanner.microAhToMah(it) }
                if (designCapacityMah == null)
                    NumberScanner.leadingNumber(t, "Design capacity:")
                        ?.let { designCapacityMah = NumberScanner.microAhToMah(it) }
                // ── ACTION_BATTERY_CHANGED 인라인 충전 카운터·사이클 ────────────
                if (chargeCounterMah == null)
                    NumberScanner.inlineNumber(t, "cc:", allowNegative = false)
                        ?.let { chargeCounterMah = NumberScanner.microAhToMah(it) }
                if (usage == null)
                    NumberScanner.inlineNumber(t, "cycle_count:", allowNegative = false)
                        ?.let { usage = it }
            }
        }

        if (logTimestampMs == 0L) logTimestampMs = extractTimestampFromFileName(fileName)
        if (designCapacityMah == null) designCapacityMah = lookupDesignCapacity(fileName)

        // 표시용 건강도는 판정과 같은 BSOH 우선 규칙으로 정한다.
        // (예전에는 ASOC 우선이라 게이지 값과 판정 기준이 서로 달랐다)
        val assessment = BatteryHealthClassifier.assess(asocValue, batteryUsageValue, bsohValue)
        val healthPercent = assessment.healthValue ?: asoc ?: bsoh
        val healthSource = assessment.healthSource
        val healthUnsupported = healthPercent == null

        var capacityEstimated = false
        val counter = chargeCounterMah
        val designCap = designCapacityMah
        // 추정 용량도 판정과 같은 H를 써야 화면의 등급과 어긋나지 않는다.
        val healthFactor = assessment.healthValue?.takeIf { it in 1..100 }?.div(100.0) ?: 1.0

        if (measuredCapacityMah == null) {
            if (designCap != null) {
                measuredCapacityMah = (designCap * healthFactor).roundToInt()
                capacityEstimated = true
            } else if (counter != null && level in 20..100) {
                val nominal = counter * 100.0 / level
                measuredCapacityMah = (nominal * healthFactor).roundToInt()
                capacityEstimated = true
            }
        }

        val cur = chargeCounterMah
        val max = measuredCapacityMah
        if (cur != null && max != null && cur > max) {
            chargeCounterMah = if (level in 0..100) (max * level / 100.0).roundToInt() else max
        }

        val readSuccess = healthPercent != null || bsoh != null || usage != null ||
                manufactureDateMs != 0L || calibratedDateMs != 0L || asocSeen

        return BatteryInfo(
            healthPercent = healthPercent,
            healthSource = healthSource,
            healthUnsupported = healthUnsupported,
            cycleCount = usage,
            asocRaw = asocRaw,
            asocValue = asocValue,
            bsohRaw = bsohRaw,
            bsohPercent = bsoh?.takeIf { it in 0..100 },
            bsohValue = bsohValue,
            usageRaw = usageRaw,
            batteryUsageValue = batteryUsageValue,
            llbType = llbType,
            manufactureDateMs = manufactureDateMs,
            calibratedDateMs = calibratedDateMs,
            firstUseDateMs = firstUseDateMs,
            logFileName = fileName,
            logTimestampMs = logTimestampMs,
            readSuccess = readSuccess,
            chargeCounterMah = chargeCounterMah,
            measuredCapacityMah = measuredCapacityMah,
            capacityEstimated = capacityEstimated,
            designCapacityMah = designCapacityMah,
            assessment = assessment
        )
    }

    /** 정규식 두 개를 줄마다 돌리지 않도록, 날짜가 있을 수 있는 줄만 걸러낸다. */
    private fun looksLikeTimestampLine(t: String): Boolean =
        t[0] == '[' || t.contains("dumpstate:") || t.contains("Build time:")

    /** 로그에 흔한 8자리 날짜(YYYYMMDD)를 epoch millis로. 깨졌으면 0. */
    private fun parseCompactDate(text: String, format: SimpleDateFormat): Long =
        try {
            format.parse(text)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }

    private fun extractValueOrNull(line: String, key: String): Int? =
        extractSignedValueOrNull(line, key)?.takeIf { it >= 0 }

    private fun extractSignedValueOrNull(line: String, key: String): Int? {
        val raw = line.substringAfter(key, "").trim()
        if (raw.isBlank()) return null
        val stripped = when {
            raw.startsWith("[") && raw.contains("]") ->
                raw.removePrefix("[").substringBefore("]").trim()
            else -> raw.takeWhile { it.isDigit() || it == '-' }
        }
        if (stripped.equals("unsupported", ignoreCase = true)) return null
        return stripped.toIntOrNull()
    }
}