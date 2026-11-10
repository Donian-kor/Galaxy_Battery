package com.alyaqdhan.galaxy_battery

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

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

        val fullDateTimeRegex = Regex("""(?:==\s*dumpstate:|dumpstate:|Build time:)\s*(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})""")
        val bracketDateTimeRegex = Regex("""^\[(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})]""")
        val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val llbRegex = Regex("""^LLB\s+(CAL|MAN):\s*(\d{8})\s*$""", RegexOption.IGNORE_CASE)
        val compactDateFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

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
            }
        }

        if (logTimestampMs == 0L) logTimestampMs = extractTimestampFromFileName(fileName)

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
            readSuccess = readSuccess
        )
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