package com.alyaqdhan.galaxy_battery

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

data class BatteryHealthAssessment(
    val grade: BatteryHealthGrade,
    val convertedCycles: Int?,
    val diagnosticIndexRange: IntRange?,
    val badReason: BatteryHealthBadReason? = null,
    val score: Int? = null
)

/** 앱 내부 커뮤니티 판정표이며 삼성 공식 진단식이 아니다. */
object BatteryHealthClassifier {
    private fun clamp01(value: Double): Double = value.coerceIn(0.0, 1.0)

    private fun toBadEarlyScore(cycles: Int, asoc: Int): Int {
        val threshold = when (asoc) {
            66 -> 501
            67 -> 501
            68 -> 501
            69 -> 501
            70 -> 501
            71 -> 501
            72 -> 501
            73 -> 501
            74 -> 501
            75 -> 501
            76 -> 501
            77 -> 501
            78 -> 501
            79 -> 501
            80 -> 500
            81 -> 467
            82 -> 434
            83 -> 400
            84 -> 350
            else -> 501
        }
        val lower = when (asoc) {
            in 66..79 -> 30
            in 80..84 -> 30
            else -> 30
        }
        val score = 20.0 + 29.0 * (cycles - lower) / (threshold - lower)
        return Math.round(score.coerceIn(20.0, 49.0)).toInt()
    }

    private fun gradeScoreRange(grade: BatteryHealthGrade): IntRange? = when (grade) {
        BatteryHealthGrade.BAD -> 0..49
        BatteryHealthGrade.WEAK -> 50..69
        BatteryHealthGrade.NORMAL -> 70..84
        BatteryHealthGrade.GOOD -> 85..100
        BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE,
        BatteryHealthGrade.CALCULATION_UNAVAILABLE,
        BatteryHealthGrade.UNDETERMINED -> null
    }

    private fun finalScoreInRange(rawScore: Double, range: IntRange): Int {
        val clamped = rawScore.coerceIn(range.first.toDouble(), range.last.toDouble())
        return Math.round(clamped).toInt()
    }

    private fun computeFormulaScore(grade: BatteryHealthGrade, asoc: Int?, cycles: Int): Int? {
        if (asoc == null) return null
        val range = gradeScoreRange(grade) ?: return null
        val raw = when (grade) {
            BatteryHealthGrade.GOOD -> {
                val a = clamp01((asoc - 80.0) / 20.0)
                val c = clamp01((1199.0 - cycles) / 1199.0)
                85.0 + (100.0 - 85.0) * Math.pow(a, 0.4) * Math.pow(c, 0.6)
            }
            BatteryHealthGrade.NORMAL -> {
                val a = clamp01((asoc - 66.0) / 13.0)
                val c = clamp01((1199.0 - cycles) / 698.0)
                70.0 + (84.0 - 70.0) * Math.pow(a, 0.4) * Math.pow(c, 0.6)
            }
            BatteryHealthGrade.WEAK -> {
                val a = clamp01((asoc - 50.0) / 15.0)
                val c = clamp01(1.0 - (cycles - 1200.0) / 1200.0)
                50.0 + (69.0 - 50.0) * Math.pow(a, 0.4) * Math.pow(c, 0.6)
            }
            BatteryHealthGrade.BAD -> {
                if (asoc <= 55) {
                    val a = clamp01(asoc / 55.0)
                    val c = clamp01(1.0 - (cycles - 1200.0) / 1800.0)
                    19.0 * Math.pow(a, 0.4) * Math.pow(c, 0.6)
                } else {
                    val threshold = when (asoc) {
                        66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79 -> 501
                        80 -> 500
                        81 -> 467
                        82 -> 434
                        83 -> 400
                        84 -> 350
                        else -> 501
                    }
                    20.0 + 29.0 * (cycles - 30.0) / (threshold - 30.0)
                }
            }
            else -> return null
        }
        return finalScoreInRange(raw, range)
    }

    fun assess(asoc: Int?, batteryUsage: Int?): BatteryHealthAssessment {
        val cycles = batteryUsage?.takeIf { it >= 0 }?.div(100)
        if (cycles == null) {
            return BatteryHealthAssessment(BatteryHealthGrade.UNDETERMINED, null, null)
        }

        if (cycles < 30) {
            return BatteryHealthAssessment(
                BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE,
                cycles,
                null
            )
        }
        if (asoc == -1 || asoc == 0) {
            return BatteryHealthAssessment(
                BatteryHealthGrade.CALCULATION_UNAVAILABLE,
                cycles,
                null
            )
        }
        if (asoc != null && asoc !in 1..100) {
            return BatteryHealthAssessment(BatteryHealthGrade.UNDETERMINED, cycles, null)
        }

        var badReason: BatteryHealthBadReason? = null
        val grade = when {
            asoc != null && asoc <= 55 && cycles >= 1200 -> {
                badReason = BatteryHealthBadReason.END_OF_LIFE
                BatteryHealthGrade.BAD
            }
            cycles >= 1200 || (asoc != null && asoc <= 65) -> BatteryHealthGrade.WEAK
            asoc != null && asoc in 66..79 && cycles in 30..500 -> {
                badReason = BatteryHealthBadReason.EARLY_DEGRADATION
                BatteryHealthGrade.BAD
            }
            asoc != null && asoc in 66..79 && cycles in 501..1199 -> BatteryHealthGrade.NORMAL
            asoc != null && asoc in 80..84 -> {
                val badCycleLimit = when (asoc) {
                    80 -> 500
                    81 -> 467
                    82 -> 434
                    83 -> 400
                    else -> 350
                }
                when {
                    cycles < badCycleLimit -> {
                        badReason = BatteryHealthBadReason.EARLY_DEGRADATION
                        BatteryHealthGrade.BAD
                    }
                    cycles < 1200 -> BatteryHealthGrade.GOOD
                    else -> BatteryHealthGrade.WEAK
                }
            }
            asoc != null && asoc in 85..100 && cycles in 30..1199 -> BatteryHealthGrade.GOOD
            else -> BatteryHealthGrade.UNDETERMINED
        }

        val indexRange = gradeScoreRange(grade)
        val score = if (asoc != null) computeFormulaScore(grade, asoc, cycles) else null
        return BatteryHealthAssessment(grade, cycles, indexRange, badReason, score)
    }
}
