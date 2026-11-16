package com.donian.galaxy_battery

import com.donian.galaxy_battery.parser.DesignCapacityTable
import com.donian.galaxy_battery.parser.LogFileNameParser
import com.donian.galaxy_battery.parser.NumberScanner
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

object BatteryParser {

    // ── 파일명 관련 도우미 (LogFileNameParser에 위임) ─────────────────────────

    fun isDumpStateLog(name: String): Boolean = LogFileNameParser.isDumpStateLog(name)

    fun truncateLogDisplayName(name: String): String = LogFileNameParser.truncateDisplayName(name)

    fun extractTimestampFromFileName(name: String): Long = LogFileNameParser.timestampFromFileName(name)

    fun lookupDesignCapacity(modelOrFileName: String): Int? = DesignCapacityTable.lookup(modelOrFileName)

    /**
     * 로그에서 배터리 관련 섹션을 뽑아 하나의 텍스트 블록으로 합친다. 없으면 null.
     *
     * 섹션은 두 종류다 — `BatteryInfoBackUp` 블록과 `DUMP OF SERVICE battery:` 블록.
     * 각 줄을 상태(대기/백업/서비스)에 따라 분류해 누적한 뒤,
     * 로그 버퍼(EventLogBuffer 등) 잡음을 걸러낸다.
     */
    internal fun readBatterySections(stream: InputStream): String? {
        // (라벨, 내용) 쌍을 순서대로 모은다.
        val collected = mutableListOf<Pair<String, String>>()
        val buffer     = StringBuilder()
        var label      = ""
        var mode       = Mode.SEEK

        fun flush() {
            collected += label to buffer.trim().toString()
            buffer.clear()
        }

        BufferedReader(InputStreamReader(stream)).use { reader ->
            reader.forEachLine { raw ->
                when (mode) {
                    Mode.SEEK -> {
                        val start = sectionStart(raw)
                        if (start != null) {
                            if (buffer.isNotEmpty()) flush()
                            label = start.label
                            mode = start.mode
                        }
                    }
                    Mode.BACKUP -> {
                        if (leavesBackupBlock(raw)) {
                            val next = serviceDumpStart(raw)
                            if (next != null) {
                                // 서비스 덤프가 바로 이어지면, 비어 있는 백업 섹션은 버린다.
                                if (buffer.isNotEmpty()) flush()
                                label = next.label
                                mode  = next.mode
                            } else {
                                // 그 외에는 지금까지 모은 백업 섹션(빈 섹션이어도)을 확정한다.
                                flush()
                                label = ""
                                mode  = Mode.SEEK
                            }
                        } else {
                            buffer.append(raw).append('\n')
                        }
                    }
                    Mode.SERVICE -> {
                        if (leavesServiceBlock(raw)) {
                            flush()
                            label = ""
                            mode = Mode.SEEK
                        } else {
                            buffer.append(raw).append('\n')
                        }
                    }
                }
            }
            if (buffer.isNotEmpty()) flush()
        }

        if (collected.isEmpty()) return null

        return collected.joinToString("\n\n") { (sectionLabel, content) ->
            val filtered = stripLogBufferNoise(content)
            if (sectionLabel.isNotEmpty()) "━━━ $sectionLabel ━━━\n\n$filtered" else filtered
        }
    }

    /** 섹션 판정 상태. SEEK=대기, BACKUP=백업 블록 내부, SERVICE=서비스 덤프 내부. */
    private enum class Mode { SEEK, BACKUP, SERVICE }

    /** 섹션 시작 줄이면 (라벨, 진입할 모드)를, 아니면 null 을 돌린다. */
    private data class SectionStart(val label: String, val mode: Mode)

    /** 이 줄이 새 섹션의 시작인지 판정한다. */
    private fun sectionStart(raw: String): SectionStart? = when {
        raw.contains("BatteryInfoBackUp") -> SectionStart("BatteryInfoBackUp", Mode.BACKUP)
        else -> serviceDumpStart(raw)
    }

    /** 서비스 덤프(`DUMP OF SERVICE battery:`) 시작 줄이면 해당 섹션 정보를 돌려준다. */
    private fun serviceDumpStart(raw: String): SectionStart? =
        if (raw.contains("DUMP OF SERVICE battery:")) {
            SectionStart("Battery Service Dump", Mode.SERVICE)
        } else {
            null
        }

    /** BACKUP 블록을 벗어나는 줄인지 판정한다(들여쓰기 없이 시작하고 'm'으로 시작하지 않는 줄). */
    private fun leavesBackupBlock(raw: String): Boolean =
        raw.isNotBlank() && !raw.startsWith(" ") && !raw.startsWith("\t") &&
                !raw.trimStart().startsWith("m")

    /** SERVICE 블록을 벗어나는 줄인지(다른 DUMP OF SERVICE). */
    private fun leavesServiceBlock(raw: String): Boolean =
        raw.contains("DUMP OF SERVICE") && !raw.contains("DUMP OF SERVICE battery:")

    /** `[...LogBuffer]` 이후의 잡음을 제거한 본문을 만든다. */
    private fun stripLogBufferNoise(content: String): String {
        val bufferHeader = Regex("""^\[\w+LogBuffer]""")
        return buildString {
            var insideBuffer = false
            content.lines().forEach { raw ->
                val trimmed = raw.trim()
                when {
                    bufferHeader.containsMatchIn(trimmed) -> insideBuffer = true
                    insideBuffer && trimmed.isEmpty() -> {}
                    insideBuffer && (trimmed.startsWith("[") ||
                            (trimmed.isNotEmpty() && !trimmed.startsWith(" ") && !trimmed.first().isDigit())) -> {
                        insideBuffer = false
                        appendLine(raw)
                    }
                    insideBuffer -> {}
                    else -> appendLine(raw)
                }
            }
        }.trimEnd()
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
        var activationDateMs = 0L
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
        // `[ril.actdate]: [20220325]` — 기기 최초 통화일. SYSTEM PROPERTIES 영역에 있다.
        val activationDateRegex = Regex("""^\[ril\.actdate]:\s*\[(\d{8})]""")
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
                if (activationDateMs == 0L) {
                    activationDateRegex.find(t)?.let { m ->
                        activationDateMs = parseCompactDate(m.groupValues[1], compactDateFmt)
                    }
                }
                if (logTimestampMs == 0L && t.length >= 19 && looksLikeTimestampLine(t)) {
                    fullDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    } ?: bracketDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    }
                }

                // ── 배터리 서비스의 실시간 측정값 ──────────────────────────────
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
                // 인라인 `cc:` 는 위 서비스 덤프가 값을 비워 둔 때만 쓰는 폴백이다.
                // 둘 다 있으면 서비스 덤프(`charge counter:`) 값이 남아야 한다.
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
            activationDateMs = activationDateMs,
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
