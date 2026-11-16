package com.donian.galaxy_battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.donian.galaxy_battery.data.parser.*
import com.donian.galaxy_battery.model.*
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 덤프스테이트 로그 파서 검증 (38개).
 *
 * 여기서 고정하는 것은 전부 **관찰 가능한 출력**이다. 파싱된 [BatteryInfo]의 필드 값,
 * 섹션 추출 결과, 파일 이름 처리 결과가 같은 로그 입력에 대해 항상 같아야 한다.
 * 파서 내부 구현(정규식 여부, 상태 기계 표현 등)은 이 테스트의 관심사가 아니다.
 */
class BatteryParserTest {

    private companion object {
        /** 기기 모델과 타임스탬프가 박힌 전형적인 덤프스테이트 파일 이름. */
        const val SAMPLE_FILE = "dumpState_SM-G991B_202611010900.log"
    }

    private fun parse(log: String, fileName: String = SAMPLE_FILE) =
        BatteryParser.parseInputStream(log.byteInputStream(), fileName)

    private fun extractSections(log: String) =
        BatteryParser.readBatterySections(log.byteInputStream())

    /** "yyyy-MM-dd HH:mm:ss" 문자열을 epoch millis로 바꾼다. */
    private fun millis(dateTime: String): Long =
        requireNotNull(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(dateTime)).time

    /** 8자리(YYYYMMDD) 날짜 문자열을 epoch millis로 바꾼다. */
    private fun compactMillis(compactDate: String): Long =
        requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse(compactDate)).time

    // ── 건강도 표시 ──────────────────────────────────────────────────────────

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
    fun `bsoh steps in when asoc is absent`() {
        val info = parse("  mSavedBatteryBsoh: 74\n")

        assertEquals(74, info.healthPercent)
        assertEquals(74, info.bsohPercent)
    }

    @Test
    fun `a bsoh reported as unsupported leaves the gauge empty`() {
        assertNull(parse("mSavedBatteryBsoh: [unsupported]\n").bsohPercent)
    }

    @Test
    fun `a bsoh above one hundred never reaches the gauge`() {
        assertNull(parse("mSavedBatteryBsoh: 101\n").bsohPercent)
    }

    @Test
    fun `bsoh steps in when asoc is unsupported`() {
        val info = parse("  mSavedBatteryAsoc: [unsupported]\n  mSavedBatteryBsoh: 74\n")

        assertEquals(74, info.healthPercent)
    }

    @Test
    fun `a bracketed health value is still read`() {
        assertEquals(87, parse("mSavedBatteryAsoc: [87]\n").healthPercent)
    }

    @Test
    fun `trailing words after the health number are dropped`() {
        assertEquals(87, parse("mSavedBatteryAsoc: 87 percent\n").healthPercent)
    }

    @Test
    fun `a negative health value is treated as absent`() {
        val info = parse("mSavedBatteryAsoc: -1\n")

        assertNull(info.healthPercent)
        assertTrue(info.healthUnsupported)
    }

    @Test
    fun `nothing readable means no health at all`() {
        val info = parse("unrelated line\n")

        assertNull(info.healthPercent)
        assertTrue(info.healthUnsupported)
        assertFalse(info.readSuccess)
    }

    @Test
    fun `the displayed value obeys the same bsoh first rule as the assessment`() {
        val info = parse("mSavedBatteryAsoc: 85\nmSavedBatteryBsoh: 95\nmSavedBatteryUsage: 30000\n")

        assertEquals(HealthSource.BSOH, info.healthSource)
        assertEquals(95, info.healthPercent)
        assertEquals(95, info.assessment.healthValue)
        assertEquals(HealthSource.BSOH, info.assessment.healthSource)
        assertEquals(0.95, info.healthFactor, 0.0001)
    }

    @Test
    fun `an estimated capacity reuses the assessment health value`() {
        val info = parse(
            "mSavedBatteryAsoc: 85\nmSavedBatteryBsoh: 95\n" +
                    "mSavedBatteryUsage: 30000\nDesign capacity: 4000000\n"
        )

        assertEquals(95, info.assessment.healthValue)
        assertEquals(3800, info.measuredCapacityMah)
    }

    @Test
    fun `the asoc raw fallback consults the enum source`() {
        val withAsoc = parse("mSavedBatteryAsoc: 85\nmSavedBatteryUsage: 30000\n")
        assertEquals("85", withAsoc.displayAsocRaw)

        val bsohOnly = parse("mSavedBatteryBsoh: 95\nmSavedBatteryUsage: 30000\n")
        assertEquals("—", bsohOnly.displayAsocRaw)
    }

    // ── 사용량과 사이클 ──────────────────────────────────────────────────────

    @Test
    fun `a saved usage converts into cycles`() {
        val info = parse("  mSavedBatteryUsage: 97034\n")

        assertEquals(97034, info.batteryUsageValue)
        assertEquals("97034", info.usageRaw)
        assertEquals(970, info.cycleCount)
    }

    @Test
    fun `a negative usage survives raw but is never converted`() {
        val info = parse("  mSavedBatteryUsage: -1\n")

        assertEquals(-1, info.batteryUsageValue)
        assertNull(info.cycleCount)
        assertNull(info.assessment.convertedCycles)
    }

    @Test
    fun `an inline cycle count backfills a missing saved usage`() {
        val info = parse("ACTION_BATTERY_CHANGED cycle_count:412, cc:3643752\n")

        assertEquals(412, info.cycleCount)
        assertNull(info.batteryUsageValue)
    }

    @Test
    fun `an inline charge counter converts from micro amp hours`() {
        val info = parse("ACTION_BATTERY_CHANGED cycle_count:412, cc:3643752\n")

        assertEquals(3643, info.chargeCounterMah)
    }

    // ── 설계 용량과 실측 용량 ────────────────────────────────────────────────

    @Test
    fun `design capacity falls back to the model table`() {
        val info = parse("unrelated line\n", "dumpState_SM-G991B_202611010900.log")

        assertEquals(4000, info.designCapacityMah)
    }

    @Test
    fun `design capacity from the log outranks the model table`() {
        val info = parse("Design capacity: 4500000\n", "dumpState_SM-G991B_202611010900.log")

        assertEquals(4500, info.designCapacityMah)
    }

    @Test
    fun `measured capacity is derived from design capacity and health`() {
        val info = parse(
            "mSavedBatteryAsoc: 80\nmSavedBatteryUsage: 30000\nDesign capacity: 4000000\n"
        )

        assertTrue(info.capacityEstimated)
        assertEquals(4000, info.designCapacityMah)
        assertEquals(3200, info.measuredCapacityMah)
    }

    @Test
    fun `a counter inherited from a replaced battery is clamped by the level`() {
        val info = parse(
            "level: 40\ncharge counter: 4100000\nMaximum capacity: 4000000\n" +
                    "mSavedBatteryUsage: 30000\n"
        )

        assertEquals(4000, info.measuredCapacityMah)
        assertEquals(1600, info.chargeCounterMah)
    }

    // ── 타임스탬프 ───────────────────────────────────────────────────────────

    @Test
    fun `the log timestamp is taken from the dumpstate header`() {
        val info = parse("== dumpstate: 2026-11-01 09:00:00\n")

        assertEquals(millis("2026-11-01 09:00:00"), info.logTimestampMs)
    }

    @Test
    fun `the log timestamp falls back to the file name`() {
        val info = parse("unrelated line\n", "dumpState_dumpstate_202611010900.log")

        assertEquals(millis("2026-11-01 09:00:00"), info.logTimestampMs)
    }

    @Test
    fun `a file name timestamp is zero when the name carries no date`() {
        assertEquals(0L, BatteryParser.extractTimestampFromFileName("dumpState_SM-G991B.log"))
    }

    // ── LLB 날짜 ─────────────────────────────────────────────────────────────

    @Test
    fun `an llb man line yields the battery manufacture date`() {
        val info = parse("LLB MAN: 20210825\n")

        // 파서는 기기 기본 시간대에서 날짜를 읽으므로 기대값도 같은 시간대로 만든다.
        assertEquals(compactMillis("20210825"), info.manufactureDateMs)
        assertEquals("MAN", info.llbType)
        // CAL이 없으면 장착일로 쓰지 않는다.
        assertEquals(0L, info.calibratedDateMs)
    }

    @Test
    fun `llb cal and llb man stay separate dates`() {
        // 실제 Note 10+ 로그: CAL(보정)이 MAN(제조)보다 하루 뒤다.
        val info = parse("LLB CAL: 20190925\nLLB MAN: 20190924\n")

        assertEquals(compactMillis("20190925"), info.calibratedDateMs)
        assertEquals(compactMillis("20190924"), info.manufactureDateMs)
        assertTrue(info.calibratedDateMs > info.manufactureDateMs)
        assertEquals("CAL", info.llbType)
    }

    @Test
    fun `an llb line without a date leaves both dates empty`() {
        // 폴드3처럼 CAL 값이 비어 있는 로그.
        val info = parse("LLB CAL:\n")

        assertEquals(0L, info.calibratedDateMs)
        assertEquals(0L, info.manufactureDateMs)
        assertEquals("", info.llbType)
    }

    @Test
    fun `a malformed llb date is ignored`() {
        val info = parse("LLB MAN: 20xx0105\n")

        assertEquals(0L, info.manufactureDateMs)
        assertEquals("", info.llbType)
    }

    // ── 기기 날짜 ────────────────────────────────────────────────────────────

    @Test
    fun `first use date tracks the device rather than the battery`() {
        // 실제 폴드3 로그: CAL은 비어 있고 FirstUseDate만 있다.
        // FirstUseDate는 기기(메인보드) 최초 사용일이라 배터리 날짜가 아니다.
        val info = parse(
            "LLB CAL:\n" +
                    "LLB MAN: 20210825\n" +
                    "battery FirstUseDate: [20230310]\n"
        )

        assertEquals(compactMillis("20230310"), info.firstUseDateMs)
        // 배터리 보정일로는 쓰지 않는다.
        assertEquals(0L, info.calibratedDateMs)
        assertEquals(compactMillis("20210825"), info.manufactureDateMs)
    }

    @Test
    fun `a malformed first use date is ignored`() {
        val info = parse("battery FirstUseDate: [2023ab10]\n")

        assertEquals(0L, info.firstUseDateMs)
    }

    @Test
    fun `the activation date is read from ril actdate`() {
        val info = parse("[ril.actdate]: [20220325]\n")

        assertEquals(compactMillis("20220325"), info.activationDateMs)
    }

    @Test
    fun `a malformed activation date is ignored`() {
        val info = parse("[ril.actdate]: [2022ab25]\n")

        assertEquals(0L, info.activationDateMs)
    }

    // ── 파일 이름 ────────────────────────────────────────────────────────────

    @Test
    fun `dump state file names are matched case insensitively`() {
        assertTrue(BatteryParser.isDumpStateLog("dumpState_SM-G991B_202611010900.log"))
        assertTrue(BatteryParser.isDumpStateLog("DUMPSTATE_X.LOG"))
        assertFalse(BatteryParser.isDumpStateLog("logcat_2026-11-01.log"))
        assertFalse(BatteryParser.isDumpStateLog("dumpState_X.txt"))
    }

    @Test
    fun `a display name keeps only the model`() {
        assertEquals(
            "dumpstate_G991B",
            BatteryParser.truncateLogDisplayName("dumpState_SM-G991B_202611010900.log")
        )
        assertEquals("plain_name", BatteryParser.truncateLogDisplayName("plain_name_123456.log"))
    }

    // ── 섹션 추출 ────────────────────────────────────────────────────────────

    @Test
    fun `the battery service dump is extracted together with its header`() {
        val text = extractSections(
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
    fun `a backup block is extracted even when it comes first`() {
        val text = extractSections(
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
    fun `log buffer noise is stripped from the extracted section`() {
        val text = extractSections(
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
    fun `a log without battery sections returns null`() {
        assertNull(extractSections("DUMP OF SERVICE alarm:\n  something: 1\n"))
    }

    // ── 설계 용량 표 ─────────────────────────────────────────────────────────

    @Test
    fun `the design capacity table covers every series`() {
        assertEquals(4400, BatteryParser.lookupDesignCapacity("SM-F966B"))
        assertEquals(4000, BatteryParser.lookupDesignCapacity("SM-F741B"))
        assertEquals(5000, BatteryParser.lookupDesignCapacity("SM-S938B"))
        assertEquals(4900, BatteryParser.lookupDesignCapacity("SM-S936B"))
        // S24 계열
        assertEquals(4000, BatteryParser.lookupDesignCapacity("SM-S921B"))
        assertEquals(4900, BatteryParser.lookupDesignCapacity("SM-S926B"))
        assertEquals(5000, BatteryParser.lookupDesignCapacity("SM-S928B"))
        assertEquals(4700, BatteryParser.lookupDesignCapacity("SM-S721B"))
        assertEquals(5000, BatteryParser.lookupDesignCapacity("SM-G998B"))
        assertEquals(4000, BatteryParser.lookupDesignCapacity("SM-G991B"))
        assertEquals(4500, BatteryParser.lookupDesignCapacity("SM-N986B"))
        assertEquals(3400, BatteryParser.lookupDesignCapacity("SM-G973F"))
        assertNull(BatteryParser.lookupDesignCapacity("SM-UNKNOWN"))
    }
}