package com.donian.galaxy_battery

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * 마지막 분석 결과를 SharedPreferences에 보관한다.
 *
 * 판정에 쓰이는 모든 원본 값(`asocValue`, `bsohValue`, `batteryUsageValue`)을
 * 함께 저장해야 캐시 경로에서도 파싱 직후와 같은 등급·점수가 나온다.
 * 하나라도 빠지면 화면에는 값이 보이는데 점수만 다른 기준으로 계산된다.
 */
object BatteryCache {

    /**
     * 캐시 스키마 버전. 저장 항목이 바뀌면 올려서 이전 스키마로 쓰인 캐시를
     * 무효화한다. (버전이 없는 구 캐시는 판정 입력 일부가 비어 있어 버려야 한다)
     */
    private const val SCHEMA_VERSION = 5

    /** "값 없음"을 Int로 저장하기 위한 센티널. */
    private const val MISSING_INT = Int.MIN_VALUE

    /** 이 앱이 쓰는 모든 캐시 키. 저장·복원·삭제가 이 목록 하나로 일관되게 맞물린다. */
    private enum class K(val key: String) {
        SCHEMA("cache_schema_version"),
        LOG_FILE_NAME("cache_log_file_name"),
        LOG_TIMESTAMP("cache_log_timestamp_ms"),
        HEALTH_PERCENT("cache_health_percent"),
        HEALTH_SOURCE("cache_health_source"),
        HEALTH_UNSUPPORTED("cache_health_unsupported"),
        CYCLE_COUNT("cache_cycle_count"),
        ASOC_RAW("cache_asoc_raw"),
        ASOC_VALUE("cache_asoc_value"),
        BSOH_RAW("cache_bsoh_raw"),
        BSOH_PERCENT("cache_bsoh_percent"),
        BSOH_VALUE("cache_bsoh_value"),
        BATTERY_USAGE_VALUE("cache_battery_usage_value"),
        USAGE_RAW("cache_usage_raw"),
        MANUFACTURE_DATE("cache_manufacture_date_ms"),
        CALIBRATED_DATE("cache_calibrated_date_ms"),
        FIRST_USE_DATE("cache_first_use_date_ms"),
        ACTIVATION_DATE("cache_activation_date_ms"),
        CHARGE_COUNTER("cache_charge_counter_mah"),
        MEASURED_CAPACITY("cache_measured_capacity"),
        CAPACITY_ESTIMATED("cache_capacity_estimated"),
        DESIGN_CAPACITY("cache_design_capacity"),

        // 이전 버전에서 쓰다 만 폐기 키 (읽지 않고 지우기만 한다)
        LEGACY_VOLTAGE("cache_voltage_mv"),
        LEGACY_TEMPERATURE("cache_temperature_c"),
        LEGACY_CURRENT("cache_current_ma"),
        LEGACY_CHARGE_STATUS("cache_charge_status"),
        LEGACY_POWER_SOURCE("cache_power_source"),
    }

    fun cacheBatteryInfo(prefs: SharedPreferences, info: BatteryInfo) {
        if (!info.readSuccess) return
        prefs.edit {
            putInt   (K.SCHEMA.key,            SCHEMA_VERSION)
            putString(K.LOG_FILE_NAME.key,     info.logFileName)
            putLong  (K.LOG_TIMESTAMP.key,     info.logTimestampMs)
            putInt   (K.HEALTH_PERCENT.key,    info.healthPercent ?: -1)
            putString(K.HEALTH_SOURCE.key,     info.healthSource?.name ?: "")
            putBoolean(K.HEALTH_UNSUPPORTED.key, info.healthUnsupported)
            putInt   (K.CYCLE_COUNT.key,       info.cycleCount ?: -1)
            putString(K.ASOC_RAW.key,          info.asocRaw)
            putInt   (K.ASOC_VALUE.key,        info.asocValue ?: MISSING_INT)
            putString(K.BSOH_RAW.key,          info.bsohRaw)
            putInt   (K.BSOH_PERCENT.key,      info.bsohPercent ?: -1)
            putInt   (K.BSOH_VALUE.key,        info.bsohValue ?: MISSING_INT)
            putInt   (K.BATTERY_USAGE_VALUE.key, info.batteryUsageValue ?: MISSING_INT)
            putString(K.USAGE_RAW.key,         info.usageRaw)
            putLong  (K.MANUFACTURE_DATE.key,  info.manufactureDateMs)
            putLong  (K.CALIBRATED_DATE.key,   info.calibratedDateMs)
            putLong  (K.FIRST_USE_DATE.key,    info.firstUseDateMs)
            putLong  (K.ACTIVATION_DATE.key,   info.activationDateMs)
            putInt   (K.CHARGE_COUNTER.key,    info.chargeCounterMah ?: -1)
            putInt   (K.MEASURED_CAPACITY.key, info.measuredCapacityMah ?: -1)
            putBoolean(K.CAPACITY_ESTIMATED.key, info.capacityEstimated)
            putInt   (K.DESIGN_CAPACITY.key,   info.designCapacityMah ?: -1)

            // 이전 스키마 잔재는 저장 시 함께 쓸려 낸다.
            K.entries.filter { it.name.startsWith("LEGACY_") }.forEach { remove(it.key) }
        }
    }

    fun loadCachedBatteryInfo(prefs: SharedPreferences): BatteryInfo? {
        if (prefs.getInt(K.SCHEMA.key, 0) != SCHEMA_VERSION) return null
        val fileName = prefs.getString(K.LOG_FILE_NAME.key, null) ?: return null
        if (fileName.isBlank()) return null

        // 판정 입력 3종: 하나라도 없으면 등급이 조용히 달라지므로 센티널로 구분해 넘긴다.
        val asoc          = prefs.getInt(K.ASOC_VALUE.key, MISSING_INT).orNull()
        val batteryUsage  = prefs.getInt(K.BATTERY_USAGE_VALUE.key, MISSING_INT).orNull()
        val bsoh          = prefs.getInt(K.BSOH_VALUE.key, MISSING_INT).orNull()

        return BatteryInfo(
            healthPercent     = prefs.getInt(K.HEALTH_PERCENT.key, -1).takeIf { it >= 0 },
            healthSource      = prefs.getString(K.HEALTH_SOURCE.key, "")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { HealthSource.valueOf(it) }.getOrNull() },
            healthUnsupported = prefs.getBoolean(K.HEALTH_UNSUPPORTED.key, false),
            cycleCount        = prefs.getInt(K.CYCLE_COUNT.key, -1).takeIf { it >= 0 },
            asocRaw           = prefs.getString(K.ASOC_RAW.key, "") ?: "",
            asocValue         = asoc,
            bsohRaw           = prefs.getString(K.BSOH_RAW.key, "") ?: "",
            bsohPercent       = prefs.getInt(K.BSOH_PERCENT.key, -1).takeIf { it in 0..100 },
            bsohValue         = bsoh,
            batteryUsageValue = batteryUsage,
            usageRaw          = prefs.getString(K.USAGE_RAW.key, "") ?: "",
            manufactureDateMs = prefs.getLong(K.MANUFACTURE_DATE.key, 0L),
            calibratedDateMs  = prefs.getLong(K.CALIBRATED_DATE.key, 0L),
            firstUseDateMs    = prefs.getLong(K.FIRST_USE_DATE.key, 0L),
            activationDateMs  = prefs.getLong(K.ACTIVATION_DATE.key, 0L),
            logFileName       = fileName,
            logTimestampMs    = prefs.getLong(K.LOG_TIMESTAMP.key, 0L),
            readSuccess       = true,
            chargeCounterMah  = prefs.getInt(K.CHARGE_COUNTER.key, -1).takeIf { it > 0 },
            measuredCapacityMah = prefs.getInt(K.MEASURED_CAPACITY.key, -1).takeIf { it > 0 },
            capacityEstimated = prefs.getBoolean(K.CAPACITY_ESTIMATED.key, false),
            designCapacityMah = prefs.getInt(K.DESIGN_CAPACITY.key, -1).takeIf { it > 0 },
            assessment = BatteryHealthClassifier.assess(
                asoc = asoc,
                batteryUsage = batteryUsage,
                bsoh = bsoh
            )
        )
    }

    /** Int 센티널([MISSING_INT])을 실제 값이 있었던 경우로만 바꾼다. */
    private fun Int.orNull(): Int? = takeIf { it != MISSING_INT }

    fun clearBatteryInfoCache(prefs: SharedPreferences) {
        prefs.edit {
            // 현재 스키마 키 + 폐기 키 전부 제거
            K.entries.forEach { remove(it.key) }
        }
    }
}
