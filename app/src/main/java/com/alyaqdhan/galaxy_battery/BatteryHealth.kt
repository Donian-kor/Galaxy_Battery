package com.alyaqdhan.galaxy_battery

enum class BatteryHealthGrade {
    GOOD,
    NORMAL,
    WEAK,
    BAD,
    UNDETERMINED
}

data class BatteryHealthAssessment(
    val grade: BatteryHealthGrade,
    val convertedCycles: Int?
)

/** 앱 내부 커뮤니티 판정표이며 삼성 공식 진단식이 아니다. */
object BatteryHealthClassifier {

    fun assess(asoc: Int?, batteryUsage: Int?): BatteryHealthAssessment {
        val cycles = batteryUsage?.takeIf { it >= 0 }?.div(100)
        if (cycles == null) {
            return BatteryHealthAssessment(BatteryHealthGrade.UNDETERMINED, null)
        }

        val grade = when {
            asoc == null || asoc == -1 || asoc == 0 -> when {
                cycles <= 900 -> BatteryHealthGrade.GOOD
                cycles <= 1200 -> BatteryHealthGrade.NORMAL
                else -> BatteryHealthGrade.BAD
            }
            asoc < 1 || asoc > 100 -> BatteryHealthGrade.UNDETERMINED
            asoc <= 65 -> BatteryHealthGrade.WEAK
            asoc <= 79 -> when {
                cycles < 30 -> BatteryHealthGrade.GOOD
                cycles <= 500 -> BatteryHealthGrade.BAD
                cycles < 1200 -> BatteryHealthGrade.NORMAL
                else -> BatteryHealthGrade.WEAK
            }
            asoc <= 84 -> {
                val badCycleLimit = when (asoc) {
                    80 -> 500
                    81 -> 467
                    82 -> 434
                    83 -> 400
                    else -> 350
                }
                when {
                    cycles < 30 -> BatteryHealthGrade.GOOD
                    cycles < badCycleLimit -> BatteryHealthGrade.BAD
                    cycles < 1200 -> BatteryHealthGrade.GOOD
                    else -> BatteryHealthGrade.WEAK
                }
            }
            else -> if (cycles < 1200) {
                BatteryHealthGrade.GOOD
            } else {
                BatteryHealthGrade.WEAK
            }
        }

        return BatteryHealthAssessment(grade, cycles)
    }
}
