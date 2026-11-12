package com.donian.galaxy_battery

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

/** 진단·계산이 불가능한 구체 사유. 화면 문구는 같아도 원인 안내가 달라질 수 있다. */
enum class BatteryHealthUnavailableReason {
    /** mSavedBatteryUsage(사이클)를 읽지 못했거나 음수 값이다. */
    MISSING_CYCLES,

    /** 건강도 입력이 1~100 범위 밖이다. (예: -2, 101) */
    HEALTH_OUT_OF_RANGE,

    /** 건강도 입력 자체가 없다. (없음 / -1 / 0) */
    HEALTH_MISSING,

    /** 환산 사이클이 진단 하한보다 적어 판정할 수 없다. */
    CYCLES_BELOW_MINIMUM
}

data class BatteryHealthAssessment(
    val status: BatteryHealthStatus,
    val grade: BatteryHealthGrade,
    val convertedCycles: Int?,
    val diagnosticIndexRange: IntRange?,
    val badReason: BatteryHealthBadReason? = null,
    val unavailableReason: BatteryHealthUnavailableReason? = null,
    val score: Int? = null,
    val healthValue: Int? = null,
    val healthSource: HealthSource? = null,
    val isSourceVerified: Boolean = false,
    val sourceMismatch: Boolean = false,
    val isCustomRule: Boolean = false
)

/** 앱 내부 커뮤니티 판정표이며 삼성 공식 진단식이 아니다. */
object BatteryHealthClassifier {
    /** 진단이 성립하는 최소 환산 사이클. 판정과 점수 공식 양쪽이 이 값을 공유한다. */
    const val MIN_CYCLES_FOR_DIAGNOSIS = 30

    /** 조기 열화 판정 시 사용하는 환산 사이클 하한. (BAD 20~49 구간 공식의 분모) */
    private const val EARLY_CYCLE_FLOOR = 30

    private fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    /** H 80~84 구간의 조기 열화/정상 경계 사이클. 그 외 H는 모두 같은 값을 쓴다. */
    private fun getT(h: Int): Int = when (h) {
        80 -> 500
        81 -> 467
        82 -> 434
        83 -> 400
        84 -> 350
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
                    20.0 + 29.0 * (nDouble - EARLY_CYCLE_FLOOR) / (t - EARLY_CYCLE_FLOOR)
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
                diagnosticIndexRange = null,
                unavailableReason = BatteryHealthUnavailableReason.MISSING_CYCLES
            )
        }

        if (cycles < MIN_CYCLES_FOR_DIAGNOSIS) {
            return BatteryHealthAssessment(
                status = BatteryHealthStatus.INSUFFICIENT_CYCLES,
                grade = BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE,
                convertedCycles = cycles,
                diagnosticIndexRange = null,
                unavailableReason = BatteryHealthUnavailableReason.CYCLES_BELOW_MINIMUM
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
                    diagnosticIndexRange = null,
                    unavailableReason = BatteryHealthUnavailableReason.HEALTH_MISSING
                )
            } else {
                return BatteryHealthAssessment(
                    status = BatteryHealthStatus.INVALID_DATA,
                    grade = BatteryHealthGrade.UNDETERMINED,
                    convertedCycles = cycles,
                    diagnosticIndexRange = null,
                    unavailableReason = BatteryHealthUnavailableReason.HEALTH_OUT_OF_RANGE
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
            healthH in 66..79 && cycles in EARLY_CYCLE_FLOOR..500 -> {
                badReason = BatteryHealthBadReason.EARLY_DEGRADATION
                BatteryHealthGrade.BAD
            }
            healthH in 66..79 && cycles in 501..1199 -> BatteryHealthGrade.NORMAL
            healthH in 80..84 && cycles in EARLY_CYCLE_FLOOR until getT(healthH) -> {
                badReason = BatteryHealthBadReason.EARLY_DEGRADATION
                BatteryHealthGrade.BAD
            }
            healthH in 80..84 && cycles in getT(healthH)..1199 -> BatteryHealthGrade.GOOD
            healthH in 85..100 && cycles in EARLY_CYCLE_FLOOR..1199 -> BatteryHealthGrade.GOOD
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
            isCustomRule = isCustomRule
        )
    }
}
