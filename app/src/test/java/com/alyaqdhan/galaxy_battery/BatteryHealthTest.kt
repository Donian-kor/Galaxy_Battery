package com.alyaqdhan.galaxy_battery

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
            BatteryHealthClassifier.assess(info.asocValue, info.batteryUsageValue).grade
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
            BatteryHealthClassifier.assess(info.asocValue, info.batteryUsageValue).grade
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
}

class BatteryHealthClassifierTest {

    private fun assertGrade(asoc: Int?, cycles: Int, expected: BatteryHealthGrade) {
        val assessment = BatteryHealthClassifier.assess(asoc, cycles * 100)
        assertEquals(expected, assessment.grade)
        assertEquals(cycles, assessment.convertedCycles)
    }

    private fun assertScore(asoc: Int, cycles: Int, expectedGrade: BatteryHealthGrade, expectedScore: Int) {
        val assessment = BatteryHealthClassifier.assess(asoc, cycles * 100)
        assertEquals(expectedGrade, assessment.grade)
        assertEquals(expectedScore, assessment.score)
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
        assertGrade(null, 30, BatteryHealthGrade.UNDETERMINED)
        assertGrade(null, 1199, BatteryHealthGrade.UNDETERMINED)
        assertGrade(null, 1200, BatteryHealthGrade.WEAK)
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
        assertEquals(0..49, BatteryHealthClassifier.assess(79, 3000).diagnosticIndexRange)
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
}
