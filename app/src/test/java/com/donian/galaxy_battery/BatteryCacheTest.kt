package com.donian.galaxy_battery

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 캐시 왕복 검증.
 *
 * SharedPreferences를 직접 주고받는 대신 [BatteryCache]가 읽고 쓰는 키를 그대로 흉내 내므로,
 * 실제 SharedPreferences 구현(AndroidX test) 없이도 "무엇이 저장되고 무엇이 빠졌는지"를
 * JVM 단위 테스트로 고정할 수 있다. `bsohValue` 누락이 등급을 바꿨던 회귀를 막는 것이 목적이다.
 */
class BatteryCacheTest {

    /** 저장된 키/값만 보관하는 최소 SharedPreferences 흉내. */
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

    private fun roundTrip(info: BatteryInfo): BatteryInfo {
        val prefs = FakePrefs()
        BatteryCache.cacheBatteryInfo(prefs, info)
        return requireNotNull(BatteryCache.loadCachedBatteryInfo(prefs))
    }

    @Test
    fun `cache round trip keeps the bsoh based assessment`() {
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
    fun `cache round trip keeps every assessment input`() {
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
    fun `llb cal and man survive the round trip separately`() {
        // 제조일(LLB MAN)과 장착·보정일(LLB CAL)은 별개 필드다.
        val parsed = parse(
            "LLB CAL: 20190925\n" +
                    "LLB MAN: 20190924\n" +
                    "battery FirstUseDate: [20230310]\n" +
                    "mSavedBatteryUsage: 30000\n"
        )
        val restored = roundTrip(parsed)

        assertEquals(
            requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse("20190924")).time,
            restored.manufactureDateMs
        )
        assertEquals(
            requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse("20190925")).time,
            restored.calibratedDateMs
        )
        assertEquals(
            requireNotNull(SimpleDateFormat("yyyyMMdd", Locale.US).parse("20230310")).time,
            restored.firstUseDateMs
        )
    }

    @Test
    fun `negative and out of range inputs survive the round trip`() {
        val restored = roundTrip(parse("mSavedBatteryAsoc: -2\nmSavedBatteryUsage: -1\n"))

        assertEquals(-2, restored.asocValue)
        assertEquals(-1, restored.batteryUsageValue)
        assertEquals(BatteryHealthGrade.UNDETERMINED, restored.assessment.grade)
    }

    @Test
    fun `cached display source uses the enum`() {
        val bsoh = roundTrip(parse("mSavedBatteryBsoh: 74\nmSavedBatteryUsage: 30000\n"))
        assertEquals(HealthSource.BSOH, bsoh.healthSource)
        assertEquals(74, bsoh.healthPercent)

        val asoc = roundTrip(parse("mSavedBatteryAsoc: 85\nmSavedBatteryUsage: 30000\n"))
        assertEquals(HealthSource.ASOC, asoc.healthSource)
    }

    @Test
    fun `failed reads are not cached`() {
        val prefs = FakePrefs()
        BatteryCache.cacheBatteryInfo(prefs, parse("unrelated line\n"))

        assertNull(BatteryCache.loadCachedBatteryInfo(prefs))
    }

    @Test
    fun `legacy cache without a schema version is discarded`() {
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
    fun `clearing removes every key the cache writes`() {
        val prefs = FakePrefs()
        BatteryCache.cacheBatteryInfo(prefs, parse("mSavedBatteryAsoc: 85\nmSavedBatteryUsage: 30000\n"))
        assertTrue(prefs.store.isNotEmpty())

        BatteryCache.clearBatteryInfoCache(prefs)

        assertTrue(prefs.store.isEmpty())
        assertNull(BatteryCache.loadCachedBatteryInfo(prefs))
    }
}