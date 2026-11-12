package com.alyaqdhan.galaxy_battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class BatteryParserTest {

    private val fileName = "dumpState_SM-G991B_202611010900.log"

    private fun parse(log: String, name: String = fileName) =
        BatteryParser.parseInputStream(log.byteInputStream(), name)

    private fun sections(log: String) = BatteryParser.readBatterySections(log.byteInputStream())

    private fun millis(date: String, pattern: String = "yyyy-MM-dd HH:mm:ss") =
        requireNotNull(SimpleDateFormat(pattern, Locale.US).parse(date)).time

    // ── Health ────────────────────────────────────────────────────────────────

    @Test
    fun `asoc is the main health value`() {
        val info = parse("  mSavedBatteryAsoc: 87\n  mSavedBatteryBsoh: 90\n")
        assertEquals(87, info.healthPercent)
        assertEquals(90, info.bsohPercent)
        assertEquals("90", info.bsohRaw)
        assertFalse(info.healthUnsupported)
        assertTrue(info.readSuccess)
    }

    @Test
    fun `bsoh is used when asoc is missing`() {
        val info = parse("  mSavedBatteryBsoh: 74\n")
        assertEquals(74, info.healthPercent)
        assertEquals(74, info.bsohPercent)
    }

    @Test
    fun `unsupported bsoh has no gauge value`() {
        assertNull(parse("mSavedBatteryBsoh: [unsupported]\n").bsohPercent)
    }

    @Test
    fun `bsoh values above one hundred are not used in the gauge`() {
        assertNull(parse("mSavedBatteryBsoh: 101\n").bsohPercent)
    }

    @Test
    fun `bsoh is used when asoc is unsupported`() {
        val info = parse("  mSavedBatteryAsoc: [unsupported]\n  mSavedBatteryBsoh: 74\n")
        assertEquals(74, info.healthPercent)
    }

    @Test
    fun `health can be in brackets`() {
        assertEquals(87, parse("mSavedBatteryAsoc: [87]\n").healthPercent)
    }

    @Test
    fun `text after the health number is ignored`() {
        assertEquals(87, parse("mSavedBatteryAsoc: 87 percent\n").healthPercent)
    }

    @Test
    fun `negative health counts as missing`() {
        val info = parse("mSavedBatteryAsoc: -1\n")
        assertNull(info.healthPercent)
        assertTrue(info.healthUnsupported)
    }

    @Test
    fun `health is unavailable when nothing was read`() {
        val info = parse("unrelated line\n")

        assertNull(info.healthPercent)
        assertTrue(info.healthUnsupported)
        assertFalse(info.readSuccess)
    }

    // ── Display / assessment share one priority rule ─────────────────────────

    @Test
    fun `displayed health follows the bsoh first rule used by the assessment`() {
        val info = parse("mSavedBatteryAsoc: 85\nmSavedBatteryBsoh: 95\nmSavedBatteryUsage: 30000\n")

        assertEquals(HealthSource.BSOH, info.healthSource)
        assertEquals(95, info.healthPercent)
        assertEquals(95, info.assessment.healthValue)
        assertEquals(HealthSource.BSOH, info.assessment.healthSource)
        assertEquals(0.95, info.healthFactor, 0.0001)
    }

    @Test
    fun `estimated capacity uses the same health value as the assessment`() {
        val info = parse(
            "mSavedBatteryAsoc: 85\nmSavedBatteryBsoh: 95\n" +
                    "mSavedBatteryUsage: 30000\nDesign capacity: 4000000\n"
        )

        assertEquals(95, info.assessment.healthValue)
        assertEquals(3800, info.measuredCapacityMah)
    }

    @Test
    fun `asoc raw fallback uses the enum source`() {
        val withAsoc = parse("mSavedBatteryAsoc: 85\nmSavedBatteryUsage: 30000\n")
        assertEquals("85", withAsoc.displayAsocRaw)

        val bsohOnly = parse("mSavedBatteryBsoh: 95\nmSavedBatteryUsage: 30000\n")
        assertEquals("—", bsohOnly.displayAsocRaw)
    }

    // ── Usage / cycles ───────────────────────────────────────────────────────

    @Test
    fun `usage is converted to cycles`() {
        val info = parse("  mSavedBatteryUsage: 97034\n")

        assertEquals(97034, info.batteryUsageValue)
        assertEquals("97034", info.usageRaw)
        assertEquals(970, info.cycleCount)
    }

    @Test
    fun `negative usage is kept raw but not converted`() {
        val info = parse("  mSavedBatteryUsage: -1\n")

        assertEquals(-1, info.batteryUsageValue)
        assertNull(info.cycleCount)
        assertNull(info.assessment.convertedCycles)
    }

    @Test
    fun `inline cycle count fills in when saved usage is missing`() {
        val info = parse("ACTION_BATTERY_CHANGED cycle_count:412, cc:3643752\n")

        assertEquals(412, info.cycleCount)
        assertNull(info.batteryUsageValue)
    }

    @Test
    fun `inline charge counter is converted from micro amp hours`() {
        val info = parse("ACTION_BATTERY_CHANGED cycle_count:412, cc:3643752\n")

        assertEquals(3643, info.chargeCounterMah)
    }

    @Test
    fun `design capacity falls back to the model table`() {
        val info = parse("unrelated line\n", "dumpState_SM-G991B_202611010900.log")

        assertEquals(4000, info.designCapacityMah)
    }

    @Test
    fun `design capacity from the log wins over the model table`() {
        val info = parse("Design capacity: 4500000\n", "dumpState_SM-G991B_202611010900.log")

        assertEquals(4500, info.designCapacityMah)
    }

    @Test
    fun `measured capacity is estimated from design capacity and health`() {
        val info = parse(
            "mSavedBatteryAsoc: 80\nmSavedBatteryUsage: 30000\nDesign capacity: 4000000\n"
        )

        assertTrue(info.capacityEstimated)
        assertEquals(4000, info.designCapacityMah)
        assertEquals(3200, info.measuredCapacityMah)
    }

    @Test
    fun `a counter left over from a replaced battery is clamped to the level`() {
        val info = parse(
            "level: 40\ncharge counter: 4100000\nMaximum capacity: 4000000\n" +
                    "mSavedBatteryUsage: 30000\n"
        )

        assertEquals(4000, info.measuredCapacityMah)
        assertEquals(1600, info.chargeCounterMah)
    }

    // ── Timestamps ───────────────────────────────────────────────────────────

    @Test
    fun `log timestamp comes from the dumpstate header`() {
        val info = parse("== dumpstate: 2026-11-01 09:00:00\n")

        assertEquals(millis("2026-11-01 09:00:00"), info.logTimestampMs)
    }

    @Test
    fun `log timestamp falls back to the file name`() {
        val info = parse("unrelated line\n", "dumpState_dumpstate_202611010900.log")

        assertEquals(millis("2026-11-01 09:00:00"), info.logTimestampMs)
    }

    @Test
    fun `llb man line yields the battery manufacture date`() {
        val info = parse("LLB MAN: 20210825\n")

        // The parser reads the date in the device's default zone, so build the
        // expectation with the same default zone instead of a hard-coded epoch.
        val expected = requireNotNull(
            SimpleDateFormat("yyyyMMdd", Locale.US).parse("20210825")
        ).time
        assertEquals(expected, info.manufactureDateMs)
        assertEquals("MAN", info.llbType)
        // CAL이 없으면 장착일로 쓰지 않는다.
        assertEquals(0L, info.calibratedDateMs)
    }

    @Test
    fun `llb cal and man are separate dates`() {
        // 실제 Note 10+ 로그: CAL(보정)이 MAN(제조)보다 하루 뒤다.
        val info = parse("LLB CAL: 20190925\nLLB MAN: 20190924\n")

        val cal = requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse("20190925")).time
        val man = requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse("20190924")).time
        assertEquals(cal, info.calibratedDateMs)
        assertEquals(man, info.manufactureDateMs)
        // 한 변수에 섞이지 않는다.
        assertTrue(info.calibratedDateMs > info.manufactureDateMs)
        assertEquals("CAL", info.llbType)
    }

    @Test
    fun `llb line without a date leaves both dates empty`() {
        // 폴드3처럼 CAL 값이 비어 있는 로그.
        val info = parse("LLB CAL:\n")

        assertEquals(0L, info.calibratedDateMs)
        assertEquals(0L, info.manufactureDateMs)
        assertEquals("", info.llbType)
    }

    @Test
    fun `first use date is the device date not the battery date`() {
        // 실제 폴드3 로그: CAL은 비어 있고 FirstUseDate만 있다.
        // FirstUseDate는 기기(메인보드) 최초 사용일이라 배터리 날짜가 아니다.
        val info = parse(
            "LLB CAL:\n" +
                    "LLB MAN: 20210825\n" +
                    "battery FirstUseDate: [20230310]\n"
        )

        val expected = requireNotNull(
            SimpleDateFormat("yyyyMMdd", Locale.US).parse("20230310")
        ).time
        assertEquals(expected, info.firstUseDateMs)
        // 배터리 보정일로는 쓰지 않는다.
        assertEquals(0L, info.calibratedDateMs)
        assertEquals(
            requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse("20210825")).time,
            info.manufactureDateMs
        )
    }

    @Test
    fun `malformed first use date is ignored`() {
        val info = parse("battery FirstUseDate: [2023ab10]\n")

        assertEquals(0L, info.firstUseDateMs)
    }

    @Test
    fun `llb date is ignored when malformed`() {
        val info = parse("LLB MAN: 20xx0105\n")

        assertEquals(0L, info.manufactureDateMs)
        assertEquals("", info.llbType)
    }

    @Test
    fun `file name timestamp is zero when the name has no date`() {
        assertEquals(0L, BatteryParser.extractTimestampFromFileName("dumpState_SM-G991B.log"))
    }

    // ── File names ──────────────────────────────────────────────────────────

    @Test
    fun `dump state log names are recognised case insensitively`() {
        assertTrue(BatteryParser.isDumpStateLog("dumpState_SM-G991B_202611010900.log"))
        assertTrue(BatteryParser.isDumpStateLog("DUMPSTATE_X.LOG"))
        assertFalse(BatteryParser.isDumpStateLog("logcat_2026-11-01.log"))
        assertFalse(BatteryParser.isDumpStateLog("dumpState_X.txt"))
    }

    @Test
    fun `display names keep only the model`() {
        assertEquals(
            "dumpstate_G991B",
            BatteryParser.truncateLogDisplayName("dumpState_SM-G991B_202611010900.log")
        )
        assertEquals("plain_name", BatteryParser.truncateLogDisplayName("plain_name_123456.log"))
    }

    // ── Section extraction ──────────────────────────────────────────────────

    @Test
    fun `battery service dump section is extracted with its header`() {
        val text = sections(
            "DUMP OF SERVICE battery:\n" +
                    "  mSavedBatteryAsoc: 87\n" +
                    "DUMP OF SERVICE alarm:\n" +
                    "  mSavedBatteryAsoc: 1\n"
        )

        assertTrue(text!!.contains("Battery Service Dump"))
        assertTrue(text.contains("mSavedBatteryAsoc: 87"))
        assertFalse(text.contains("DUMP OF SERVICE alarm"))
    }

    @Test
    fun `backup block is extracted when it comes first`() {
        val text = sections(
            "BatteryInfoBackUp\n" +
                    "  mSavedBatteryAsoc: 88\n" +
                    "\n" +
                    "DUMP OF SERVICE battery:\n" +
                    "  mSavedBatteryAsoc: 87\n"
        )

        assertTrue(text!!.contains("BatteryInfoBackUp"))
        assertTrue(text.contains("mSavedBatteryAsoc: 88"))
        assertTrue(text.contains("mSavedBatteryAsoc: 87"))
    }

    @Test
    fun `log buffer noise is dropped from the extracted section`() {
        val text = sections(
            "DUMP OF SERVICE battery:\n" +
                    "  mSavedBatteryAsoc: 87\n" +
                    "[EventLogBuffer]\n" +
                    "\n" +
                    "  mSavedBatteryUsage: 500\n"
        )

        assertTrue(text!!.contains("mSavedBatteryAsoc: 87"))
        assertFalse(text.contains("EventLogBuffer"))
        assertTrue(text.contains("mSavedBatteryUsage: 500"))
    }

    @Test
    fun `logs without battery sections return null`() {
        assertNull(sections("DUMP OF SERVICE alarm:\n  something: 1\n"))
    }

    // ── Design capacity table ───────────────────────────────────────────────

    @Test
    fun `design capacity table covers each series`() {
        assertEquals(4400, BatteryParser.lookupDesignCapacity("SM-F966B"))
        assertEquals(4000, BatteryParser.lookupDesignCapacity("SM-F741B"))
        assertEquals(5000, BatteryParser.lookupDesignCapacity("SM-S938B"))
        assertEquals(4900, BatteryParser.lookupDesignCapacity("SM-S936B"))
        assertEquals(5000, BatteryParser.lookupDesignCapacity("SM-G998B"))
        assertEquals(4000, BatteryParser.lookupDesignCapacity("SM-G991B"))
        assertEquals(4500, BatteryParser.lookupDesignCapacity("SM-N986B"))
        assertEquals(3400, BatteryParser.lookupDesignCapacity("SM-G973F"))
        assertNull(BatteryParser.lookupDesignCapacity("SM-UNKNOWN"))
    }
}