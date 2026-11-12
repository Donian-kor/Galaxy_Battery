package com.alyaqdhan.galaxy_battery

import kotlin.math.abs

enum class BatteryHealthStatus {
    OK,
    INSUFFICIENT_CYCLES,
    HEALTH_UNAVAILABLE,
    INVALID_DATA
}

enum class BatteryHealthGrade {
    GOOD,
    NORMAL,
    WEAK,
    BAD,
    DIAGNOSTIC_UNAVAILABLE,
    CALCULATION_UNAVAILABLE,
    UNDETERMINED
}

enum class BatteryHealthBadReason {
    END_OF_LIFE,
    EARLY_DEGRADATION
}

enum class HealthSource {
    BSOH,
    ASOC
}

data class BatteryHealthAssessment(
    val status: BatteryHealthStatus,
    val grade: BatteryHealthGrade,
    val convertedCycles: Int?,
    val diagnosticIndexRange: IntRange?,
    val badReason: BatteryHealthBadReason? = null,
    val score: Int? = null,
    val healthValue: Int? = null,
    val healthSource: HealthSource? = null,
    val isSourceVerified: Boolean = false,
    val sourceMismatch: Boolean = false,
    val isCustomRule: Boolean = false,
    val displayText: String? = null
)

/** 앱 내부 커뮤니티 판정표이며 삼성 공식 진단식이 아니다. */
object BatteryHealthClassifier {
    private fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    private fun getT(h: Int): Int = when (h) {
        80 -> 500
        81 -> 467
        82 -> 434
        83 -> 400
        84 -> 350
        in 66..79 -> 501
        else -> 501
    }

    private fun gradeScoreRange(grade: BatteryHealthGrade, badReason: BatteryHealthBadReason? = null): IntRange? = when (grade) {
        BatteryHealthGrade.GOOD -> 85..100
        BatteryHealthGrade.NORMAL -> 70..84
        BatteryHealthGrade.WEAK -> 50..69
        BatteryHealthGrade.BAD -> if (badReason == BatteryHealthBadReason.END_OF_LIFE) 0..19 else 20..49
        BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE,
        BatteryHealthGrade.CALCULATION_UNAVAILABLE,
        BatteryHealthGrade.UNDETERMINED -> null
    }

    private fun computeFormulaScore(h: Int, cycles: Int, grade: BatteryHealthGrade, badReason: BatteryHealthBadReason?): Int? {
        val hDouble = h.toDouble()
        val nDouble = cycles.toDouble()
        val raw = when (grade) {
            BatteryHealthGrade.GOOD -> {
                val a = clamp01((hDouble - 80.0) / 20.0)
                val c = clamp01((1199.0 - nDouble) / 1199.0)
                85.0 + 15.0 * Math.pow(a, 0.4) * Math.pow(c, 0.6)
            }
            BatteryHealthGrade.NORMAL -> {
                val a = clamp01((hDouble - 66.0) / 13.0)
                val c = clamp01((1199.0 - nDouble) / 698.0)
                70.0 + 14.0 * Math.pow(a, 0.4) * Math.pow(c, 0.6)
            }
            BatteryHealthGrade.WEAK -> {
                val a = clamp01((hDouble - 50.0) / 15.0)
                val c = clamp01(1.0 - (nDouble - 1200.0) / 1200.0)
                50.0 + 19.0 * Math.pow(a, 0.4) * Math.pow(c, 0.6)
            }
            BatteryHealthGrade.BAD -> {
                if (badReason == BatteryHealthBadReason.END_OF_LIFE) {
                    val a = clamp01(hDouble / 55.0)
                    val c = clamp01(1.0 - (nDouble - 1200.0) / 1800.0)
                    19.0 * Math.pow(a, 0.4) * Math.pow(c, 0.6)
                } else {
                    val t = getT(h).toDouble()
                    20.0 + 29.0 * (nDouble - 30.0) / (t - 30.0)
                }
            }
            else -> return null
        }
        val targetRange = gradeScoreRange(grade, badReason) ?: return null
        val rounded = Math.round(raw).toInt()
        return rounded.coerceIn(targetRange.first, targetRange.last)
    }

    fun assess(asoc: Int?, batteryUsage: Int?, bsoh: Int? = null): BatteryHealthAssessment {
        val cycles = batteryUsage?.takeIf { it >= 0 }?.div(100)

        if (cycles == null) {
            return BatteryHealthAssessment(
                status = BatteryHealthStatus.INVALID_DATA,
                grade = BatteryHealthGrade.UNDETERMINED,
                convertedCycles = null,
                diagnosticIndexRange = null
            )
        }

        if (cycles < 30) {
            return BatteryHealthAssessment(
                status = BatteryHealthStatus.INSUFFICIENT_CYCLES,
                grade = BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE,
                convertedCycles = cycles,
                diagnosticIndexRange = null
            )
        }

        val isBsohValid = bsoh != null && bsoh in 1..100
        val isAsocValid = asoc != null && asoc in 1..100

        val healthH: Int
        val source: HealthSource
        val isSourceVerified: Boolean

        if (bsoh != null && isBsohValid) {
            healthH = bsoh
            source = HealthSource.BSOH
            isSourceVerified = false
        } else if (asoc != null && isAsocValid) {
            healthH = asoc
            source = HealthSource.ASOC
            isSourceVerified = true
        } else {
            if (asoc == null || asoc == -1 || asoc == 0) {
                return BatteryHealthAssessment(
                    status = BatteryHealthStatus.HEALTH_UNAVAILABLE,
                    grade = BatteryHealthGrade.CALCULATION_UNAVAILABLE,
                    convertedCycles = cycles,
                    diagnosticIndexRange = null
                )
            } else {
                return BatteryHealthAssessment(
                    status = BatteryHealthStatus.INVALID_DATA,
                    grade = BatteryHealthGrade.UNDETERMINED,
                    convertedCycles = cycles,
                    diagnosticIndexRange = null
                )
            }
        }

        val sourceMismatch = bsoh != null && asoc != null && isBsohValid && isAsocValid && abs(bsoh - asoc) >= 10

        var badReason: BatteryHealthBadReason? = null
        val grade = when {
            healthH <= 55 && cycles >= 1200 -> {
                badReason = BatteryHealthBadReason.END_OF_LIFE
                BatteryHealthGrade.BAD
            }
            cycles >= 1200 -> BatteryHealthGrade.WEAK
            healthH <= 65 -> BatteryHealthGrade.WEAK
            healthH in 66..79 && cycles in 30..500 -> {
                badReason = BatteryHealthBadReason.EARLY_DEGRADATION
                BatteryHealthGrade.BAD
            }
            healthH in 66..79 && cycles in 501..1199 -> BatteryHealthGrade.NORMAL
            healthH in 80..84 && cycles in 30 until getT(healthH) -> {
                badReason = BatteryHealthBadReason.EARLY_DEGRADATION
                BatteryHealthGrade.BAD
            }
            healthH in 80..84 && cycles in getT(healthH)..1199 -> BatteryHealthGrade.GOOD
            healthH in 85..100 && cycles in 30..1199 -> BatteryHealthGrade.GOOD
            else -> BatteryHealthGrade.UNDETERMINED
        }

        val isCustomRule = (grade == BatteryHealthGrade.BAD && badReason == BatteryHealthBadReason.END_OF_LIFE)
        val score = computeFormulaScore(healthH, cycles, grade, badReason)
        val indexRange = gradeScoreRange(grade, badReason)

        return BatteryHealthAssessment(
            status = BatteryHealthStatus.OK,
            grade = grade,
            convertedCycles = cycles,
            diagnosticIndexRange = indexRange,
            badReason = badReason,
            score = score,
            healthValue = healthH,
            healthSource = source,
            isSourceVerified = isSourceVerified,
            sourceMismatch = sourceMismatch,
            isCustomRule = isCustomRule,
            displayText = null
        )
    }
}
