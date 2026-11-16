package com.donian.galaxy_battery

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.donian.galaxy_battery.data.BatteryCache
import com.donian.galaxy_battery.data.parser.BatteryParser
import com.donian.galaxy_battery.model.*
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 캐시 왕복(save → load) 검증.
 *
 * 실제 SharedPreferences 대신 [BatteryCache]가 읽고 쓰는 키를 흉내 낸 저장소를 주입한다.
 * 그래서 AndroidX test 환경 없이도 "무엇이 저장되고 무엇이 빠졌는지"를 JVM 단위 테스트로 고정할 수 있다.
 *
 * 이 테스트가 막으려는 회귀는 `bsohValue` 누락이다. 판정 입력 하나가 캐시에서 사라지면
 * 같은 로그인데 캐시 경로에서만 등급과 점수가 달라진다.
 */
class BatteryCacheTest {

    /** 저장된 키/값만 보관하는 최소 SharedPreferences 구현. */
    private class FakePrefs : SharedPreferences {
        val store = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = store
        override fun getString(key: String?, defValue: String?): String? =
            store[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? =
            @Suppress("UNCHECKED_CAST")
            (store[key] as? Set<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = store[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = store[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = store[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            store[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = store.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()

            override fun putString(key: String?, value: String?) = apply { pending[key!!] = value }
            override fun putStringSet(key: String?, value: Set<String>?) = apply { pending[key!!] = value }
            override fun putInt(key: String?, value: Int) = apply { pending[key!!] = value }
            override fun putLong(key: String?, value: Long) = apply { pending[key!!] = value }
            override fun putFloat(key: String?, value: Float) = apply { pending[key!!] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { pending[key!!] = value }
            override fun remove(key: String?) = apply { removed += key!! }
            override fun clear() = apply {
                pending.clear(); removed.clear(); store.clear()
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                store += pending
                removed.forEach { store.remove(it) }
            }
        }
    }

    private fun parse(log: String) =
        BatteryParser.parseInputStream(log.byteInputStream(), "dumpState_test.log")

    /** 파싱 결과를 캐시에 저장했다가 즉시 되읽는다. */
    private fun roundTrip(info: BatteryInfo): BatteryInfo {
        val prefs = FakePrefs()
        BatteryCache.cacheBatteryInfo(prefs, info)
        return requireNotNull(BatteryCache.loadCachedBatteryInfo(prefs))
    }

    private fun epochOf(compactDate: String): Long =
        requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse(compactDate)).time

    @Test
    fun `a round trip preserves the bsoh based assessment`() {
        // BSOH(94)와 ASOC(100)가 다를 때 판정은 BSOH를 쓴다.
        val parsed = parse(
            "mSavedBatteryAsoc: 100\nmSavedBatteryBsoh: 94\nmSavedBatteryUsage: 42900\n"
        )
        val restored = roundTrip(parsed)

        assertEquals(94, restored.bsohValue)
        assertEquals(94, restored.assessment.healthValue)
        assertEquals(HealthSource.BSOH, restored.assessment.healthSource)
        assertEquals(parsed.assessment, restored.assessment)
        assertEquals(95, restored.assessment.score)
        assertEquals(BatteryHealthGrade.GOOD, restored.assessment.grade)
    }

    @Test
    fun `a round trip preserves all three assessment inputs`() {
        val parsed = parse(
            "mSavedBatteryAsoc: 73\nmSavedBatteryUsage: 97034\n" +
                    "Design capacity: 4000000\nmSavedBatteryBsoh: 71\n"
        )
        val restored = roundTrip(parsed)

        assertEquals(parsed.asocValue, restored.asocValue)
        assertEquals(parsed.batteryUsageValue, restored.batteryUsageValue)
        assertEquals(parsed.bsohValue, restored.bsohValue)
        assertEquals(parsed.assessment, restored.assessment)
    }

    @Test
    fun `manufacture and calibration dates survive as separate fields`() {
        // LLB MAN(제조일)과 LLB CAL(장착·보정일)은 서로 다른 날짜다.
        val parsed = parse(
            "LLB CAL: 20190925\n" +
                    "LLB MAN: 20190924\n" +
                    "battery FirstUseDate: [20230310]\n" +
                    "mSavedBatteryUsage: 30000\n"
        )
        val restored = roundTrip(parsed)

        assertEquals(epochOf("20190924"), restored.manufactureDateMs)
        assertEquals(epochOf("20190925"), restored.calibratedDateMs)
        assertEquals(epochOf("20230310"), restored.firstUseDateMs)
    }

    @Test
    fun `negative and out of range inputs survive unchanged`() {
        val restored = roundTrip(parse("mSavedBatteryAsoc: -2\nmSavedBatteryUsage: -1\n"))

        assertEquals(-2, restored.asocValue)
        assertEquals(-1, restored.batteryUsageValue)
        assertEquals(BatteryHealthGrade.UNDETERMINED, restored.assessment.grade)
    }

    @Test
    fun `the cached display source comes back as the enum`() {
        val bsoh = roundTrip(parse("mSavedBatteryBsoh: 74\nmSavedBatteryUsage: 30000\n"))
        assertEquals(HealthSource.BSOH, bsoh.healthSource)
        assertEquals(74, bsoh.healthPercent)

        val asoc = roundTrip(parse("mSavedBatteryAsoc: 85\nmSavedBatteryUsage: 30000\n"))
        assertEquals(HealthSource.ASOC, asoc.healthSource)
    }

    @Test
    fun `a failed read is never cached`() {
        val prefs = FakePrefs()
        BatteryCache.cacheBatteryInfo(prefs, parse("unrelated line\n"))

        assertNull(BatteryCache.loadCachedBatteryInfo(prefs))
    }

    @Test
    fun `a cache written before the schema version existed is dropped`() {
        // 이전 버전이 남긴 캐시: bsohValue가 없어 등급이 ASOC 기준으로 달라진다.
        val prefs = FakePrefs()
        prefs.edit()
            .putString("cache_log_file_name", "dumpState_old.log")
            .putInt("cache_health_percent", 100)
            .putInt("cache_asoc_value", 100)
            .putInt("cache_battery_usage_value", 42900)
            .apply()

        assertNull(BatteryCache.loadCachedBatteryInfo(prefs))
    }

    @Test
    fun `clearing wipes every key the cache ever writes`() {
        val prefs = FakePrefs()
        BatteryCache.cacheBatteryInfo(prefs, parse("mSavedBatteryAsoc: 85\nmSavedBatteryUsage: 30000\n"))
        assertTrue(prefs.store.isNotEmpty())

        BatteryCache.clearBatteryInfoCache(prefs)

        assertTrue(prefs.store.isEmpty())
        assertNull(BatteryCache.loadCachedBatteryInfo(prefs))
    }
}