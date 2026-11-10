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

        val levelRegex         = Regex("""^level:\s*(-?\d+)""")
        val chargeCounterRegex = Regex("""^[Cc]harge counter:\s*(-?\d+)""")
        val maxCapacityRegex   = Regex("""^Maximum capacity:\s*(-?\d+)""")
        val designCapRegex     = Regex("""^Design capacity:\s*(-?\d+)""")
        val voltageRegex       = Regex("""^voltage:\s*(-?\d+)""")
        val temperatureRegex   = Regex("""^temperature:\s*(-?\d+)""")
        val currentNowRegex    = Regex("""^current now:\s*(-?\d+)""")
        val statusRegex        = Regex("""^status:\s*(-?\d+)""")

        BufferedReader(InputStreamReader(stream)).use { reader ->
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
                if (batteryDateMs == 0L) {
                    llbRegex.find(t)?.let { m ->
                        llbType = m.groupValues[1].uppercase()
                        batteryDateMs = try {
                            compactDateFmt.parse(m.groupValues[2])?.time ?: 0L
                        } catch (_: Exception) { 0L }
                    }
                }
                if (logTimestampMs == 0L) {
                    fullDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    } ?: bracketDateTimeRegex.find(t)?.let { m ->
                        logTimestampMs = dateTimeFmt.parse(m.groupValues[1])?.time ?: 0L
                    }
                }

                // ── Live battery service telemetry ────────────────────────────
                if (level < 0) levelRegex.find(t)?.let { level = it.groupValues[1].toIntOrNull() ?: -1 }

                if (chargeCounterMah == null)
                    chargeCounterRegex.find(t)?.let { chargeCounterMah = microAhToMah(it.groupValues[1].toIntOrNull()) }
                if (measuredCapacityMah == null)
                    maxCapacityRegex.find(t)?.let { measuredCapacityMah = microAhToMah(it.groupValues[1].toIntOrNull()) }
                if (designCapacityMah == null)
                    designCapRegex.find(t)?.let { designCapacityMah = microAhToMah(it.groupValues[1].toIntOrNull()) }
                if (voltageMv == null)
                    voltageRegex.find(t)?.let { voltageMv = it.groupValues[1].toIntOrNull()?.takeIf { v -> v > 0 } }
                if (temperatureC == null)
                    temperatureRegex.find(t)?.let { m ->
                        m.groupValues[1].toIntOrNull()?.let { tenths ->
                            if (tenths > 0) temperatureC = tenths / 10f
                        }
                    }
                if (currentNowMa == null)
                    currentNowRegex.find(t)?.let { m ->
                        m.groupValues[1].toIntOrNull()?.let { currentNowMa = currentToMa(it) }
                    }
                if (chargeStatus == null)
                    statusRegex.find(t)?.let {
                        chargeStatus = it.groupValues[1].toIntOrNull()?.takeIf { s -> s in 1..5 }
                    }

                when {
                    t.startsWith("AC powered:")       && t.endsWith("true") -> powerSource = PowerSource.AC
                    t.startsWith("USB powered:")      && t.endsWith("true") -> powerSource = PowerSource.USB
                    t.startsWith("Wireless powered:") && t.endsWith("true") -> powerSource = PowerSource.WIRELESS
                }
            }
        }

        if (logTimestampMs == 0L) logTimestampMs = extractTimestampFromFileName(fileName)

        // Without a full-charge capacity, the charge counter at a known level still gives a
        // serviceable estimate. Only when the level is high enough for the rounding to hold.
        var capacityEstimated = false
        val counter = chargeCounterMah
        if (measuredCapacityMah == null && counter != null && level in 20..100) {
            measuredCapacityMah = (counter * 100.0 / level).roundToInt()
            capacityEstimated = true
        }

        val (resolvedHealth, healthSource, healthUnsupported) = when {
            asoc != null -> Triple(asoc, "asoc", false)
            bsoh != null -> Triple(bsoh, "bsoh", false)
            else -> Triple(null, "", true)
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