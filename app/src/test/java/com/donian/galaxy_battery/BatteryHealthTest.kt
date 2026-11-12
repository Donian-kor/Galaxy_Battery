package com.donian.galaxy_battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryHealthParsingTest {

    private fun parse(log: String) =
        BatteryParser.parseInputStream(log.byteInputStream(), "dumpState_test.log")

    @Test
    fun `bracketed asoc and saved usage retain original values`() {
        val info = parse("mSavedBatteryAsoc: [73]\nmSavedBatteryUsage: 97034\n")

        assertEquals(73, info.asocValue)
        assertEquals("[73]", info.asocRaw)
        assertEquals(97034, info.batteryUsageValue)
        assertEquals("97034", info.usageRaw)
        assertEquals(970, info.cycleCount)
        assertEquals(
            BatteryHealthGrade.NORMAL,
            BatteryHealthClassifier.assess(info.asocValue, info.batteryUsageValue, info.bsohValue).grade
        )
    }

    @Test
    fun `unbracketed asoc and fractional cycles parse for note ten plus`() {
        val info = parse("mSavedBatteryAsoc: 84\nmSavedBatteryUsage: 169906\n")

        assertEquals(84, info.asocValue)
        assertEquals("84", info.asocRaw)
        assertEquals(169906, info.batteryUsageValue)
        assertEquals(1699, info.cycleCount)
        assertEquals(
            BatteryHealthGrade.WEAK,
            BatteryHealthClassifier.assess(info.asocValue, info.batteryUsageValue, info.bsohValue).grade
        )
    }

    @Test
    fun `missing and negative source values remain distinguishable`() {
        val missing = parse("unrelated line\n")
        val negative = parse("mSavedBatteryAsoc: -1\nmSavedBatteryUsage: -100\n")

        assertNull(missing.asocValue)
        assertNull(missing.batteryUsageValue)
        assertEquals(-1, negative.asocValue)
        assertEquals(-100, negative.batteryUsageValue)
        assertEquals("-1", negative.asocRaw)
        assertEquals("-100", negative.usageRaw)
        assertNull(negative.cycleCount)
    }

    @Test
    fun `zero and out of range asoc values are preserved`() {
        assertEquals(0, parse("mSavedBatteryAsoc: 0\n").asocValue)
        assertEquals(-2, parse("mSavedBatteryAsoc: -2\n").asocValue)
        assertEquals(101, parse("mSavedBatteryAsoc: 101\n").asocValue)
    }

    @Test
    fun `parsed info carries its assessment`() {
        val info = parse("mSavedBatteryAsoc: 73\nmSavedBatteryUsage: 97034\n")

        assertEquals(
            BatteryHealthClassifier.assess(73, 97034, null),
            info.assessment
        )
        assertEquals(76, info.assessment.score)
    }
}

class BatteryHealthClassifierTest {

    private fun assertGrade(asoc: Int?, cycles: Int, expected: BatteryHealthGrade, bsoh: Int? = null) {
        val assessment = BatteryHealthClassifier.assess(asoc, cycles * 100, bsoh)
        assertEquals(expected, assessment.grade)
        assertEquals(cycles, assessment.convertedCycles)
    }

    private fun assertScore(asoc: Int, cycles: Int, expectedGrade: BatteryHealthGrade, expectedScore: Int, bsoh: Int? = null) {
        val assessment = BatteryHealthClassifier.assess(asoc, cycles * 100, bsoh)
        assertEquals(expectedGrade, assessment.grade)
        assertEquals(expectedScore, assessment.score)
    }

    @Test
    fun `rule 7 verification table tests`() {
        // 1. 없음, 73, 970 -> 보통, 76
        val t1 = BatteryHealthClassifier.assess(asoc = 73, batteryUsage = 97000, bsoh = null)
        assertEquals(BatteryHealthGrade.NORMAL, t1.grade)
        assertEquals(76, t1.score)

        // 2. 없음, 84, 1699 -> 주의, 64
        val t2 = BatteryHealthClassifier.assess(asoc = 84, batteryUsage = 169900, bsoh = null)
        assertEquals(BatteryHealthGrade.WEAK, t2.grade)
        assertEquals(64, t2.score)

        // 3. 없음, 94, 530 -> 정상, 94
        val t3 = BatteryHealthClassifier.assess(asoc = 94, batteryUsage = 53000, bsoh = null)
        assertEquals(BatteryHealthGrade.GOOD, t3.grade)
        assertEquals(94, t3.score)

        // 4. 94, 100, 429 -> 정상, 95 (BSOH 사용)
        val t4 = BatteryHealthClassifier.assess(asoc = 100, batteryUsage = 42900, bsoh = 94)
        assertEquals(BatteryHealthGrade.GOOD, t4.grade)
        assertEquals(95, t4.score)
        assertEquals(HealthSource.BSOH, t4.healthSource)
        assertEquals(false, t4.isSourceVerified)

        // 5. 95, 99, 344 -> 정상, 96 (BSOH 사용)
        val t5 = BatteryHealthClassifier.assess(asoc = 99, batteryUsage = 34400, bsoh = 95)
        assertEquals(BatteryHealthGrade.GOOD, t5.grade)
        assertEquals(96, t5.score)
        assertEquals(HealthSource.BSOH, t5.healthSource)

        // 6. 100, 97, 1107 -> 정상, 88 (BSOH 사용)
        val t6 = BatteryHealthClassifier.assess(asoc = 97, batteryUsage = 110700, bsoh = 100)
        assertEquals(BatteryHealthGrade.GOOD, t6.grade)
        assertEquals(88, t6.score)
        assertEquals(HealthSource.BSOH, t6.healthSource)

        // 7. 없음, 90, 300 -> 정상, 95
        val t7 = BatteryHealthClassifier.assess(asoc = 90, batteryUsage = 30000, bsoh = null)
        assertEquals(BatteryHealthGrade.GOOD, t7.grade)
        assertEquals(95, t7.score)

        // 8. 없음, 73, 200 -> 나쁨(조기 열화), 30
        val t8 = BatteryHealthClassifier.assess(asoc = 73, batteryUsage = 20000, bsoh = null)
        assertEquals(BatteryHealthGrade.BAD, t8.grade)
        assertEquals(BatteryHealthBadReason.EARLY_DEGRADATION, t8.badReason)
        assertEquals(30, t8.score)

        // 9. 없음, 50, 2000 -> 나쁨(수명 종료), 13, isCustomRule=true
        val t9 = BatteryHealthClassifier.assess(asoc = 50, batteryUsage = 200000, bsoh = null)
        assertEquals(BatteryHealthGrade.BAD, t9.grade)
        assertEquals(BatteryHealthBadReason.END_OF_LIFE, t9.badReason)
        assertEquals(13, t9.score)
        assertEquals(true, t9.isCustomRule)

        // 10. 0, 99, 344 -> ASOC 사용, 정상, 97
        val t10 = BatteryHealthClassifier.assess(asoc = 99, batteryUsage = 34400, bsoh = 0)
        assertEquals(HealthSource.ASOC, t10.healthSource)
        assertEquals(BatteryHealthGrade.GOOD, t10.grade)
        assertEquals(97, t10.score)
        assertEquals(true, t10.isSourceVerified)

        // 11. -1, 73, 970 -> ASOC 사용, 보통, 76
        val t11 = BatteryHealthClassifier.assess(asoc = 73, batteryUsage = 97000, bsoh = -1)
        assertEquals(HealthSource.ASOC, t11.healthSource)
        assertEquals(BatteryHealthGrade.NORMAL, t11.grade)
        assertEquals(76, t11.score)

        // 12. 85, 97, 300 -> 정상, 92, sourceMismatch=true
        val t12 = BatteryHealthClassifier.assess(asoc = 97, batteryUsage = 30000, bsoh = 85)
        assertEquals(BatteryHealthGrade.GOOD, t12.grade)
        assertEquals(92, t12.score)
        assertEquals(true, t12.sourceMismatch)

        // 13. 없음, -1, 500 -> 산정 불가
        val t13 = BatteryHealthClassifier.assess(asoc = -1, batteryUsage = 50000, bsoh = null)
        assertEquals(BatteryHealthStatus.HEALTH_UNAVAILABLE, t13.status)
        assertEquals(BatteryHealthGrade.CALCULATION_UNAVAILABLE, t13.grade)

        // 14. 없음, 73, 29 -> 진단 불가(사이클 부족)
        val t14 = BatteryHealthClassifier.assess(asoc = 73, batteryUsage = 2900, bsoh = null)
        assertEquals(BatteryHealthStatus.INSUFFICIENT_CYCLES, t14.status)
        assertEquals(BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE, t14.grade)
    }

    @Test
    fun `asoc 85 boundaries`() {
        assertGrade(85, 29, BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE)
        assertGrade(85, 30, BatteryHealthGrade.GOOD)
        assertGrade(85, 1199, BatteryHealthGrade.GOOD)
        assertGrade(85, 1200, BatteryHealthGrade.WEAK)
    }

    @Test
    fun `asoc 84 boundaries`() {
        assertGrade(84, 29, BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE)
        assertGrade(84, 30, BatteryHealthGrade.BAD)
        assertGrade(84, 349, BatteryHealthGrade.BAD)
        assertGrade(84, 350, BatteryHealthGrade.GOOD)
        assertGrade(84, 1199, BatteryHealthGrade.GOOD)
        assertGrade(84, 1200, BatteryHealthGrade.WEAK)
    }

    @Test
    fun `asoc 83 bad cutoff`() {
        assertGrade(83, 399, BatteryHealthGrade.BAD)
        assertGrade(83, 400, BatteryHealthGrade.GOOD)
    }

    @Test
    fun `asoc 82 bad cutoff`() {
        assertGrade(82, 433, BatteryHealthGrade.BAD)
        assertGrade(82, 434, BatteryHealthGrade.GOOD)
    }

    @Test
    fun `asoc 81 bad cutoff`() {
        assertGrade(81, 466, BatteryHealthGrade.BAD)
        assertGrade(81, 467, BatteryHealthGrade.GOOD)
    }

    @Test
    fun `asoc 80 bad cutoff`() {
        assertGrade(80, 499, BatteryHealthGrade.BAD)
        assertGrade(80, 500, BatteryHealthGrade.GOOD)
    }

    @Test
    fun `asoc 79 boundaries`() {
        assertGrade(79, 29, BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE)
        assertGrade(79, 30, BatteryHealthGrade.BAD)
        assertGrade(79, 500, BatteryHealthGrade.BAD)
        assertGrade(79, 501, BatteryHealthGrade.NORMAL)
        assertGrade(79, 1199, BatteryHealthGrade.NORMAL)
        assertGrade(79, 1200, BatteryHealthGrade.WEAK)
    }

    @Test
    fun `asoc 66 boundaries`() {
        assertGrade(66, 29, BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE)
        assertGrade(66, 30, BatteryHealthGrade.BAD)
        assertGrade(66, 500, BatteryHealthGrade.BAD)
        assertGrade(66, 501, BatteryHealthGrade.NORMAL)
        assertGrade(66, 1200, BatteryHealthGrade.WEAK)
    }

    @Test
    fun `cycle under 30 takes priority over other conditions`() {
        assertGrade(55, 0, BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE)
        assertGrade(0, 29, BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE)
    }

    @Test
    fun `asoc minus one and zero cannot be calculated from cycle 30 onward`() {
        for (asoc in listOf(-1, 0)) {
            assertGrade(asoc, 30, BatteryHealthGrade.CALCULATION_UNAVAILABLE)
            assertGrade(asoc, 1200, BatteryHealthGrade.CALCULATION_UNAVAILABLE)
        }
    }

    @Test
    fun `asoc 55 and below is end of life only from 1200 cycles`() {
        assertGrade(55, 30, BatteryHealthGrade.WEAK)
        assertGrade(55, 1199, BatteryHealthGrade.WEAK)
        assertGrade(55, 1200, BatteryHealthGrade.BAD)
        assertEquals(
            BatteryHealthBadReason.END_OF_LIFE,
            BatteryHealthClassifier.assess(55, 120000).badReason
        )
    }

    @Test
    fun `asoc 56 through 65 is weak below 1200 cycles`() {
        assertGrade(56, 30, BatteryHealthGrade.WEAK)
        assertGrade(65, 1199, BatteryHealthGrade.WEAK)
        assertGrade(65, 1200, BatteryHealthGrade.WEAK)
    }

    @Test
    fun `early degradation bad has a distinct reason`() {
        assertEquals(
            BatteryHealthBadReason.EARLY_DEGRADATION,
            BatteryHealthClassifier.assess(79, 3000).badReason
        )
    }

    @Test
    fun `missing asoc is only classified when the cycle rule determines it`() {
        assertGrade(null, 29, BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE)
        assertGrade(null, 30, BatteryHealthGrade.CALCULATION_UNAVAILABLE)
        assertGrade(null, 1199, BatteryHealthGrade.CALCULATION_UNAVAILABLE)
        assertGrade(null, 1200, BatteryHealthGrade.CALCULATION_UNAVAILABLE)
    }

    @Test
    fun `missing or negative usage cannot be assessed`() {
        val missing = BatteryHealthClassifier.assess(73, null)
        val negative = BatteryHealthClassifier.assess(73, -1)

        assertEquals(BatteryHealthGrade.UNDETERMINED, missing.grade)
        assertNull(missing.convertedCycles)
        assertEquals(BatteryHealthGrade.UNDETERMINED, negative.grade)
        assertNull(negative.convertedCycles)
    }

    @Test
    fun `out of range asoc is not corrected`() {
        assertGrade(-2, 100, BatteryHealthGrade.UNDETERMINED)
        assertGrade(101, 100, BatteryHealthGrade.UNDETERMINED)
    }

    @Test
    fun `missing asoc and usage are undetermined`() {
        val assessment = BatteryHealthClassifier.assess(null, null)

        assertEquals(BatteryHealthGrade.UNDETERMINED, assessment.grade)
        assertNull(assessment.convertedCycles)
        assertNull(assessment.diagnosticIndexRange)
    }

    @Test
    fun `final grade maps to its diagnostic index range`() {
        assertEquals(20..49, BatteryHealthClassifier.assess(79, 3000).diagnosticIndexRange)
        assertEquals(50..69, BatteryHealthClassifier.assess(84, 120000).diagnosticIndexRange)
        assertEquals(70..84, BatteryHealthClassifier.assess(73, 97034).diagnosticIndexRange)
        assertEquals(85..100, BatteryHealthClassifier.assess(85, 3000).diagnosticIndexRange)
    }

    @Test
    fun `score formulas match the expected values`() {
        assertScore(73, 970, BatteryHealthGrade.NORMAL, 76)
        assertScore(84, 1699, BatteryHealthGrade.WEAK, 64)
        assertScore(90, 300, BatteryHealthGrade.GOOD, 95)
        assertScore(73, 200, BatteryHealthGrade.BAD, 30)
        assertScore(50, 2000, BatteryHealthGrade.BAD, 13)
    }

    @Test
    fun `unavailable grade carries the specific reason`() {
        // 사이클을 못 읽음 / 건강도 범위 밖 / 건강도 없음 / 사이클 부족은
        // 화면 문구는 같아도 원인이 다르다.
        assertEquals(
            BatteryHealthUnavailableReason.MISSING_CYCLES,
            BatteryHealthClassifier.assess(73, null).unavailableReason
        )
        assertEquals(
            BatteryHealthUnavailableReason.HEALTH_OUT_OF_RANGE,
            BatteryHealthClassifier.assess(-2, 10000).unavailableReason
        )
        assertEquals(
            BatteryHealthUnavailableReason.HEALTH_MISSING,
            BatteryHealthClassifier.assess(-1, 10000).unavailableReason
        )
        assertEquals(
            BatteryHealthUnavailableReason.CYCLES_BELOW_MINIMUM,
            BatteryHealthClassifier.assess(73, 2900).unavailableReason
        )
    }

    @Test
    fun `ok grades carry no unavailable reason`() {
        assertNull(BatteryHealthClassifier.assess(73, 97034).unavailableReason)
        assertNull(BatteryHealthClassifier.assess(50, 200000).unavailableReason)
    }
}
