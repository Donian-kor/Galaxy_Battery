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
        assertEquals("asoc", info.healthSource)
        assertFalse(info.healthUnsupported)
        assertTrue(info.readSuccess)
    }

    @Test
    fun `bsoh is used when asoc is missing`() {
        val info = parse("  mSavedBatteryBsoh: 74\n")
        assertEquals(74, info.healthPercent)
        assertEquals("bsoh", info.healthSource)
    }

    @Test
    fun `bsoh is used when asoc is unsupported`() {
        val info = parse("  mSavedBatteryAsoc: [unsupported]\n  mSavedBatteryBsoh: 74\n")
        assertEquals(74, info.healthPercent)
        assertEquals("bsoh", info.healthSource)
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
    fun `no health value means unsupported`() {
        val info = parse("mSavedBatteryUsage: 124500\n")
        assertNull(info.healthPercent)
        assertTrue(info.healthUnsupported)
    }

    // ── Cycles ────────────────────────────────────────────────────────────────

    @Test
    fun `usage is stored as cycles times one hundred`() {
        assertEquals(1245, parse("mSavedBatteryUsage: 124500\n").cycleCount)
    }

    @Test
    fun `usage rounds down to whole cycles`() {
        assertEquals(1245, parse("mSavedBatteryUsage: 124599\n").cycleCount)
    }

    @Test
    fun `unsupported usage has no cycle count`() {
        assertNull(parse("mSavedBatteryUsage: [unsupported]\n").cycleCount)
    }

    // ── Dates ─────────────────────────────────────────────────────────────────

    @Test
    fun `llb cal gives the battery date`() {
        val info = parse("LLB CAL: 20240115\n")
        assertEquals(millis("20240115", "yyyyMMdd"), info.batteryDateMs)
        assertEquals("CAL", info.llbType)
    }

    @Test
    fun `llb man gives the battery date`() {
        val info = parse("llb man: 20230405\n")
        assertEquals(millis("20230405", "yyyyMMdd"), info.batteryDateMs)
        assertEquals("MAN", info.llbType)
    }

    @Test
    fun `the first llb line wins`() {
        val info = parse("LLB CAL: 20240115\nLLB MAN: 20230405\n")
        assertEquals(millis("20240115", "yyyyMMdd"), info.batteryDateMs)
    }

    @Test
    fun `an llb line with extra text is ignored`() {
        assertEquals(0L, parse("LLB CAL: 20240115 trailing\n").batteryDateMs)
    }

    @Test
    fun `log time comes from the dumpstate header`() {
        val info = parse("== dumpstate: 2026-10-31 08:15:30\n", name = "dumpState_nostamp.log")
        assertEquals(millis("2026-10-31 08:15:30"), info.logTimestampMs)
    }

    @Test
    fun `log time can come from a bracketed line`() {
        val info = parse("[2026-10-31 08:15:30] something\n", name = "dumpState_nostamp.log")
        assertEquals(millis("2026-10-31 08:15:30"), info.logTimestampMs)
    }

    @Test
    fun `log time falls back to the file name`() {
        assertEquals(millis("2026-11-01 09:00:00"), parse("no dates here\n").logTimestampMs)
    }

    // ── Read success ──────────────────────────────────────────────────────────

    @Test
    fun `a log without battery lines is not a success`() {
        assertFalse(parse("nothing useful here\n").readSuccess)
    }

    @Test
    fun `seeing an unsupported asoc still counts as a success`() {
        val info = parse("mSavedBatteryAsoc: [unsupported]\n")
        assertTrue(info.readSuccess)
        assertTrue(info.healthUnsupported)
    }

    @Test
    fun `a battery date alone counts as a success`() {
        assertTrue(parse("LLB CAL: 20240115\n").readSuccess)
    }

    // ── File names ────────────────────────────────────────────────────────────

    @Test
    fun `dumpstate logs are recognised whatever the case`() {
        assertTrue(BatteryParser.isDumpStateLog("dumpState_SM-G991B_202611010900.log"))
        assertTrue(BatteryParser.isDumpStateLog("DUMPSTATE_x.LOG"))
    }

    @Test
    fun `other files are not dumpstate logs`() {
        assertFalse(BatteryParser.isDumpStateLog("dumpState_SM-G991B.txt"))
        assertFalse(BatteryParser.isDumpStateLog("kernel_dumpState.log"))
    }

    @Test
    fun `display name keeps just the model`() {
        assertEquals("dumpstate_G991B", BatteryParser.truncateLogDisplayName(fileName))
        assertEquals(
            "dumpstate_G9766H",
            BatteryParser.truncateLogDisplayName("dumpstate_SM-G9766HE_20231015_123045.log")
        )
    }

    @Test
    fun `display name falls back when no model is found`() {
        assertEquals("dumpstate_abc", BatteryParser.truncateLogDisplayName("dumpstate_abc_x.log"))
    }

    @Test
    fun `other names only lose the extension and trailing timestamp`() {
        assertEquals("other", BatteryParser.truncateLogDisplayName("other_20231015123045.log"))
    }

    @Test
    fun `timestamp is read from twelve digits in the file name`() {
        assertEquals(
            millis("2026-11-01 09:00:00"),
            BatteryParser.extractTimestampFromFileName(fileName)
        )
    }

    @Test
    fun `a file name without twelve digits has no timestamp`() {
        assertEquals(0L, BatteryParser.extractTimestampFromFileName("dumpState_20231015_123045.log"))
    }

    // ── Battery sections ──────────────────────────────────────────────────────

    @Test
    fun `both sections are extracted and labelled`() {
        val log = """
            unrelated header
            BatteryInfoBackUp
              mSavedBatteryAsoc: 87
              mSavedBatteryUsage: 124500
            another top level line
            DUMP OF SERVICE battery:
            Current Battery Service state:
              level: 84
            DUMP OF SERVICE batteryproperties:
            done
        """.trimIndent()
        assertEquals(
            "━━━ BatteryInfoBackUp ━━━\n\n" +
                "mSavedBatteryAsoc: 87\n  mSavedBatteryUsage: 124500\n\n" +
                "━━━ Battery Service Dump ━━━\n\n" +
                "Current Battery Service state:\n  level: 84",
            sections(log)
        )
    }

    @Test
    fun `the service dump can follow the backup section directly`() {
        val log = "BatteryInfoBackUp\n  mSavedBatteryAsoc: 87\nDUMP OF SERVICE battery:\n  level: 84\n"
        assertEquals(
            "━━━ BatteryInfoBackUp ━━━\n\nmSavedBatteryAsoc: 87\n\n" +
                "━━━ Battery Service Dump ━━━\n\nlevel: 84",
            sections(log)
        )
    }

    @Test
    fun `a section still open at the end of the file is kept`() {
        assertEquals(
            "━━━ Battery Service Dump ━━━\n\nlevel: 84",
            sections("DUMP OF SERVICE battery:\n  level: 84\n")
        )
    }

    @Test
    fun `log buffers are left out`() {
        val log = """
            DUMP OF SERVICE battery:
              level: 84
              [ChargerLogBuffer]
              12:00:01 plugged
              12:00:02 unplugged
              health: 2
            DUMP OF SERVICE other:
        """.trimIndent()
        assertEquals("━━━ Battery Service Dump ━━━\n\nlevel: 84\n  health: 2", sections(log))
    }

    @Test
    fun `a log with no battery sections gives null`() {
        assertNull(sections("just some text\nand more\n"))
    }

    // ── Live telemetry ────────────────────────────────────────────────────────

    @Test
    fun `voltage is read in millivolts`() {
        assertEquals(4382, parse("  voltage: 4382\n").voltageMv)
    }

    @Test
    fun `a zero voltage is treated as missing`() {
        assertNull(parse("  voltage: 0\n").voltageMv)
    }

    @Test
    fun `temperature is read in tenths of a degree`() {
        assertEquals(31.4f, parse("  temperature: 314\n").temperatureC!!, 0.001f)
    }

    @Test
    fun `current now is read in milliamps`() {
        assertEquals(1850, parse("  current now: 1850\n").currentNowMa)
    }

    @Test
    fun `current printed in microamps is converted`() {
        assertEquals(-1250, parse("  current now: -1250000\n").currentNowMa)
    }

    @Test
    fun `charge counter is converted from microamp hours`() {
        val info = parse("  charge counter: 3948000\n  level: 84\n")
        assertEquals(3948, info.chargeCounterMah)
    }

    @Test
    fun `capacities are read and converted`() {
        val info = parse("  Maximum capacity: 4710000\n  Design capacity: 5000000\n")
        assertEquals(4710, info.measuredCapacityMah)
        assertEquals(5000, info.designCapacityMah)
        assertFalse(info.capacityEstimated)
    }

    @Test
    fun `capacity is estimated from the charge counter and level`() {
        val info = parse("  charge counter: 3800000\n  level: 80\n")
        assertEquals(4750, info.measuredCapacityMah)
        assertTrue(info.capacityEstimated)
    }

    @Test
    fun `capacity is not estimated at a low level`() {
        val info = parse("  charge counter: 300000\n  level: 10\n")
        assertNull(info.measuredCapacityMah)
        assertFalse(info.capacityEstimated)
    }

    @Test
    fun `a full capacity beats the estimate`() {
        val info = parse(
            "  charge counter: 3800000\n  level: 80\n  Maximum capacity: 4620000\n"
        )
        assertEquals(4620, info.measuredCapacityMah)
        assertFalse(info.capacityEstimated)
    }

    @Test
    fun `charge status and power source are read`() {
        val info = parse("  status: 2\n  AC powered: true\n")
        assertEquals(ChargeStatus.CHARGING, info.chargeStatus)
        assertEquals(PowerSource.AC, info.powerSource)
    }

    @Test
    fun `inline action_battery_changed line is parsed`() {
        val log = "D BatteryService: Sending ACTION_BATTERY_CHANGED: level:94, status:3, health:2, " +
            "voltage:4089, temperature:227, cycle_count:0, current_avg:-562, cc:3643752, ac:false, " +
            "usb:false, wireless:false\n"
        val info = parse(log)
        assertEquals(4089, info.voltageMv)
        assertEquals(22.7f, info.temperatureC!!, 0.01f)
        assertEquals(-562, info.currentNowMa)
        assertEquals(3643, info.chargeCounterMah)
        assertEquals(0, info.cycleCount)
        assertEquals(ChargeStatus.DISCHARGING, info.chargeStatus)
        assertEquals(PowerSource.NONE, info.powerSource)
    }

    @Test
    fun `inline ac true sets ac power source`() {
        val info = parse("D BatteryService: Sending ACTION_BATTERY_CHANGED: level:100, status:5, " +
            "voltage:4256, temperature:259, ac:true, usb:false, wireless:false\n")
        assertEquals(PowerSource.AC, info.powerSource)
    }

    @Test
    fun `inline usb true sets usb power source`() {
        val info = parse("D BatteryService: Sending ACTION_BATTERY_CHANGED: level:100, status:2, " +
            "voltage:4200, temperature:260, usb:true, ac:false, wireless:false\n")
        assertEquals(PowerSource.USB, info.powerSource)
    }

    @Test
    fun `an out of range status is ignored`() {
        assertNull(parse("  status: 9\n").chargeStatus)
    }

    @Test
    fun `a large section is read quickly`() {
        val log = buildString {
            appendLine("DUMP OF SERVICE battery:")
            repeat(200_000) { appendLine("  line $it: some value") }
        }
        val started = System.currentTimeMillis()
        val result  = requireNotNull(sections(log))
        val elapsed = System.currentTimeMillis() - started
        assertTrue(result.endsWith("line 199999: some value"))
        assertTrue("Took ${elapsed}ms", elapsed < 10_000)
    }
}
