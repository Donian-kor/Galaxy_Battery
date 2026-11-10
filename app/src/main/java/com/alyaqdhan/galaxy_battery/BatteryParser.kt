package com.alyaqdhan.galaxy_battery

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object BatteryParser {

    // ── File-name helpers (pure) ─────────────────────────────────────────────

    fun isDumpStateLog(name: String) =
        name.startsWith("dumpState_", ignoreCase = true) &&
                name.endsWith(".log", ignoreCase = true)

    /**
     * Returns a compact display name for a dumpstate log file.
     */
    fun truncateLogDisplayName(name: String): String {
        val noExt = if (name.endsWith(".log", ignoreCase = true)) name.dropLast(4) else name

        if (!noExt.startsWith("dumpState_", ignoreCase = true)) {
            return noExt.replace(Regex("""_\d{6,}$"""), "")
        }

        val afterPrefix = noExt.substring("dumpState_".length)

        val modelMatch = Regex("""[A-Za-z]\d+[A-Za-z]?""").find(afterPrefix)
        val model = modelMatch?.value ?: afterPrefix.substringBefore("_")

        return "dumpstate_$model"
    }

    fun lookupDesignCapacity(modelOrFileName: String): Int? {
        val upper = modelOrFileName.uppercase(Locale.US)
        return when {
            // Fold series
            upper.contains("F966") || upper.contains("F960") -> 4400 // Fold 7
            upper.contains("F976") || upper.contains("F970") -> 4400 // Fold 8
            upper.contains("F926") -> 4400 // Fold 3
            upper.contains("F936") -> 4400 // Fold 4
            upper.contains("F946") -> 4400 // Fold 5
            upper.contains("F956") -> 4400 // Fold 6
            upper.contains("F900") || upper.contains("F907") -> 4380 // Fold 1
            upper.contains("F916") -> 4500 // Fold 2

            // Flip series
            upper.contains("F751") || upper.contains("F750") -> 4000 // Flip 7
            upper.contains("F761") || upper.contains("F760") -> 4000 // Flip 8
            upper.contains("F700") || upper.contains("F707") -> 3300 // Flip 1
            upper.contains("F711") -> 3300 // Flip 3
            upper.contains("F721") -> 3700 // Flip 4
            upper.contains("F731") -> 3700 // Flip 5
            upper.contains("F741") -> 4000 // Flip 6

            // S26 series
            upper.contains("S941") -> 4000
            upper.contains("S946") -> 4900
            upper.contains("S948") -> 5000

            // S25 series
            upper.contains("S931") -> 4000
            upper.contains("S936") -> 4900
            upper.contains("S938") -> 5000

            // S23 series
            upper.contains("S911") -> 3900
            upper.contains("S916") -> 4700
            upper.contains("S918") -> 5000
            upper.contains("S711") -> 4500

            // S22 series
            upper.contains("S901") -> 3700
            upper.contains("S906") -> 4500
            upper.contains("S908") -> 5000

            // S21 series
            upper.contains("G991") -> 4000
            upper.contains("G996") -> 4800
            upper.contains("G998") -> 5000
            upper.contains("G990") -> 4500

            // S20 series
            upper.contains("G981") || upper.contains("G980") -> 4000
            upper.contains("G986") || upper.contains("G985") -> 4500
            upper.contains("G988") -> 5000
            upper.contains("G781") || upper.contains("G780") -> 4500

            // Note series
            upper.contains("N970") -> 3500 // Note 10
            upper.contains("N975") || upper.contains("N976") -> 4300 // Note 10+ / 5G
            upper.contains("N981") || upper.contains("N980") -> 4300
            upper.contains("N986") || upper.contains("N985") -> 4500

            // S10 series
            upper.contains("G970") -> 3100
            upper.contains("G973") -> 3400
            upper.contains("G975") -> 4100
            upper.contains("G977") -> 4500
            else -> null
        }
    }

    private val timestampFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    fun extractTimestampFromFileName(name: String): Long {
        val match = Regex("""(\d{12})""").find(name) ?: return 0L
        val f = match.groupValues[1]
        val str = "${f.substring(0, 4)}-${f.substring(4, 6)}-${f.substring(6, 8)} " +
                "${f.substring(8, 10)}:${f.substring(10, 12)}:00"
        return try {
            timestampFormat.get()!!.parse(str)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

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

        val logBufferRegex = Regex("""^\[[\w]+LogBuffer\]""")
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
        var asocSeen = false
        var asocRaw = ""
        var bsoh: Int? = null
        var bsohRaw = ""
        var usage: Int? = null
        var usageRaw = ""
        var llbType = ""
        var batteryDateMs = 0L
        var logTimestampMs = 0L

        // Live telemetry, from the battery service dump (not the backup block).
        var level = -1
        var chargeCounterMah: Int? = null
        var measuredCapacityMah: Int? = null
        var designCapacityMah: Int? = null
        var voltageMv: Int? = null
        var temperatureC: Float? = null
        var currentNowMa: Int? = null
        var chargeStatus: Int? = null
        var powerSource = PowerSource.NONE

        val fullDateTimeRegex = Regex("""(?:==\s*dumpstate:|dumpstate:|Build time:)\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
        val bracketDateTimeRegex = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]""")
        val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val llbRegex = Regex("""^LLB\s+(CAL|MAN):\s*(\d{8})\s*$""", RegexOption.IGNORE_CASE)
        val compactDateFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

        // 덤프 로그는 수십 MB라 줄마다 정규식을 여러 개 돌리면 폴더를 고를 때 체감될 만큼 느리다.
        // 값 추출은 leadingNumber/inlineNumber로 처리하고, 정규식은 아래 두 곳(날짜·LLB)에만 남긴다.

        BufferedReader(InputStreamReader(stream), 1 shl 16).use { reader ->
            reader.forEachLine { line ->
                val t = line.trim()

                if (t.contains("mSavedBatteryAsoc:")) {
                    asocSeen = true
                    asocRaw = t.substringAfter("mSavedBatteryAsoc:").trim()
                    asoc = extractValueOrNull(t, "mSavedBatteryAsoc:")
                }
                if (t.contains("mSavedBatteryBsoh:")) {
                    bsohRaw = t.substringAfter("mSavedBatteryBsoh:").trim()
                    bsoh = extractValueOrNull(t, "mSavedBatteryBsoh:")
                }
                if (t.contains("mSavedBatteryUsage:")) {
                    usageRaw = t.substringAfter("mSavedBatteryUsage:").trim()
                    val raw = extractValueOrNull(t, "mSavedBatteryUsage:")
                    usage = if (raw != null) raw / 100 else null
                }
                if (batteryDateMs == 0L && t.startsWith("LLB", ignoreCase = true)) {
                    llbRegex.find(t)?.let { m ->
                        llbType = m.groupValues[1].uppercase()
                        batteryDateMs = try {
                            compactDateFmt.parse(m.groupValues[2])?.time ?: 0L
                        } catch (_: Exception) { 0L }
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
                if (level < 0) leadingNumber(t, "level:")?.let { level = it }

                if (chargeCounterMah == null)
                    (leadingNumber(t, "charge counter:") ?: leadingNumber(t, "Charge counter:"))
                        ?.let { chargeCounterMah = microAhToMah(it) }
                if (measuredCapacityMah == null)
                    leadingNumber(t, "Maximum capacity:")?.let { measuredCapacityMah = microAhToMah(it) }
                if (designCapacityMah == null)
                    leadingNumber(t, "Design capacity:")?.let { designCapacityMah = microAhToMah(it) }
                if (voltageMv == null)
                    leadingNumber(t, "voltage:")?.takeIf { v -> v > 0 }?.let { voltageMv = it }
                if (temperatureC == null)
                    leadingNumber(t, "temperature:")?.let { tenths ->
                        if (tenths > 0) temperatureC = tenths / 10f
                    }
                if (currentNowMa == null)
                    leadingNumber(t, "current now:")?.let { currentNowMa = currentToMa(it) }
                if (chargeStatus == null)
                    leadingNumber(t, "status:")?.takeIf { s -> s in 1..5 }?.let { chargeStatus = it }

                // ── ACTION_BATTERY_CHANGED 인라인 원격 측정 ─────────────────────
                // 예: voltage:4256, temperature:259, current_avg:1, cc:3864000,
                //     cycle_count:0, health:2, ac:true, usb:false, wireless:false
                if (voltageMv == null)
                    inlineNumber(t, "voltage:")?.takeIf { v -> v > 0 }?.let { voltageMv = it }
                if (temperatureC == null)
                    inlineNumber(t, "temperature:")?.let { tenths ->
                        if (tenths > 0) temperatureC = tenths / 10f
                    }
                if (currentNowMa == null)
                    inlineNumber(t, "current_avg:")?.let { currentNowMa = currentToMa(it) }
                if (chargeCounterMah == null)
                    inlineNumber(t, "cc:", allowNegative = false)?.let { chargeCounterMah = microAhToMah(it) }
                if (usage == null)
                    inlineNumber(t, "cycle_count:", allowNegative = false)?.let { usage = it }
                if (chargeStatus == null)
                    inlineNumber(t, "status:", allowNegative = false)?.takeIf { s -> s in 1..5 }
                        ?.let { chargeStatus = it }

                if (t.contains("true")) {
                    when {
                        t.startsWith("AC powered:") || t.contains("ac:true") -> powerSource = PowerSource.AC
                        t.startsWith("USB powered:") || t.contains("usb:true") -> powerSource = PowerSource.USB
                        t.startsWith("Wireless powered:") || t.contains("wireless:true") -> powerSource = PowerSource.WIRELESS
                    }
                }
            }
        }

        if (logTimestampMs == 0L) logTimestampMs = extractTimestampFromFileName(fileName)
        if (designCapacityMah == null) designCapacityMah = lookupDesignCapacity(fileName)

        val (resolvedHealth, healthSource, healthUnsupported) = when {
            asoc != null -> Triple(asoc, "asoc", false)
            bsoh != null -> Triple(bsoh, "bsoh", false)
            else -> Triple(null, "", true)
        }

        var capacityEstimated = false
        val counter = chargeCounterMah
        val designCap = designCapacityMah
        val healthFactor = if (resolvedHealth != null && resolvedHealth in 1..100) resolvedHealth / 100.0 else 1.0

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

        val readSuccess = resolvedHealth != null || bsoh != null || usage != null ||
                batteryDateMs != 0L || asocSeen

        return BatteryInfo(
            healthPercent = resolvedHealth,
            healthSource = healthSource,
            healthUnsupported = healthUnsupported,
            cycleCount = usage,
            asocRaw = asocRaw,
            bsohRaw = bsohRaw,
            usageRaw = usageRaw,
            llbType = llbType,
            batteryDateMs = batteryDateMs,
            logFileName = fileName,
            logTimestampMs = logTimestampMs,
            readSuccess = readSuccess,
            voltageMv = voltageMv,
            temperatureC = temperatureC,
            currentNowMa = currentNowMa,
            chargeCounterMah = chargeCounterMah,
            measuredCapacityMah = measuredCapacityMah,
            capacityEstimated = capacityEstimated,
            designCapacityMah = designCapacityMah,
            chargeStatus = chargeStatus,
            powerSource = powerSource
        )
    }

    /** Charge counters are reported in µAh; a few devices already print mAh. */
    private fun microAhToMah(value: Int?): Int? {
        if (value == null || value <= 0) return null
        return if (value > 20_000) (value / 1000).takeIf { it > 0 } else value
    }

    /** Samsung prints "current now" in mA; Android's µA figures are three digits larger. */
    private fun currentToMa(value: Int): Int =
        if (abs(value) > 20_000) value / 1000 else value

    /** 정규식 두 개를 줄마다 돌리지 않도록, 날짜가 있을 수 있는 줄만 걸러낸다. */
    private fun looksLikeTimestampLine(t: String): Boolean =
        t[0] == '[' || t.contains("dumpstate:") || t.contains("Build time:")

    /** `key`로 시작하는 줄에서 첫 정수를 읽는다. (정규식 `^key:\s*(-?\d+)` 대체) */
    private fun leadingNumber(t: String, key: String): Int? {
        if (!t.startsWith(key)) return null
        var i = key.length
        while (i < t.length && t[i].isWhitespace()) i++
        return numberAt(t, i, allowNegative = true)
    }

    /** `key` 뒤에 곧바로 붙은 정수를 첫 번째 것부터 찾는다. (정규식 `key:(-?\d+)` 대체) */
    private fun inlineNumber(t: String, key: String, allowNegative: Boolean = true): Int? {
        var searchFrom = 0
        while (true) {
            val at = t.indexOf(key, searchFrom)
            if (at < 0) return null
            if (at == 0 || !t[at - 1].isLetterOrDigit()) {
                numberAt(t, at + key.length, allowNegative)?.let { return it }
            }
            searchFrom = at + 1
        }
    }

    /** [start] 위치에서 `-?\d+`를 읽는다. 숫자가 없으면 null. */
    private fun numberAt(t: String, start: Int, allowNegative: Boolean): Int? {
        var i = start
        var negative = false
        if (allowNegative && i < t.length && t[i] == '-') {
            negative = true
            i++
        }
        val digitsStart = i
        while (i < t.length && t[i] in '0'..'9') i++
        if (i == digitsStart) return null
        val text = if (negative) "-" + t.substring(digitsStart, i) else t.substring(digitsStart, i)
        return text.toIntOrNull()
    }


    private fun extractValueOrNull(line: String, key: String): Int? {
        val raw = line.substringAfter(key, "").trim()
        if (raw.isBlank()) return null
        val stripped = when {
            raw.startsWith("[") && raw.contains("]") ->
                raw.removePrefix("[").substringBefore("]").trim()
            else -> raw.takeWhile { it.isDigit() || it == '-' }
        }
        if (stripped.equals("unsupported", ignoreCase = true)) return null
        val v = stripped.toIntOrNull() ?: return null
        return if (v < 0) null else v
    }
}