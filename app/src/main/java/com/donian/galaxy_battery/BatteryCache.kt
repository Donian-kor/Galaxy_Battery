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
    private const val KEY_SCHEMA_VERSION = "cache_schema_version"

    private const val MISSING_INT = Int.MIN_VALUE

    fun cacheBatteryInfo(prefs: SharedPreferences, info: BatteryInfo) {
        if (!info.readSuccess) return
        prefs.edit {
            putInt    (KEY_SCHEMA_VERSION,          SCHEMA_VERSION)
            putString ("cache_log_file_name",      info.logFileName)
            putLong   ("cache_log_timestamp_ms",   info.logTimestampMs)
            putInt    ("cache_health_percent",      info.healthPercent ?: -1)
            putString ("cache_health_source",      info.healthSource?.name ?: "")
            putBoolean("cache_health_unsupported", info.healthUnsupported)
            putInt    ("cache_cycle_count",         info.cycleCount ?: -1)
            putString ("cache_asoc_raw",            info.asocRaw)
            putInt    ("cache_asoc_value",          info.asocValue ?: MISSING_INT)
            putString ("cache_bsoh_raw",            info.bsohRaw)
            putInt    ("cache_bsoh_percent",        info.bsohPercent ?: -1)
            putInt    ("cache_bsoh_value",          info.bsohValue ?: MISSING_INT)
            putInt    ("cache_battery_usage_value", info.batteryUsageValue ?: MISSING_INT)
            putString ("cache_usage_raw",           info.usageRaw)
            putLong   ("cache_manufacture_date_ms", info.manufactureDateMs)
            putLong   ("cache_calibrated_date_ms", info.calibratedDateMs)
            putLong   ("cache_first_use_date_ms", info.firstUseDateMs)
            putLong   ("cache_activation_date_ms", info.activationDateMs)
            putInt    ("cache_charge_counter_mah",  info.chargeCounterMah ?: -1)
            putInt    ("cache_measured_capacity",   info.measuredCapacityMah ?: -1)
            putBoolean("cache_capacity_estimated",  info.capacityEstimated)
            putInt    ("cache_design_capacity",     info.designCapacityMah ?: -1)
            remove("cache_voltage_mv")
            remove("cache_temperature_c")
            remove("cache_current_ma")
            remove("cache_charge_status")
            remove("cache_power_source")
        }
    }

    fun loadCachedBatteryInfo(prefs: SharedPreferences): BatteryInfo? {
        if (prefs.getInt(KEY_SCHEMA_VERSION, 0) != SCHEMA_VERSION) return null
        val fileName = prefs.getString("cache_log_file_name", null) ?: return null
        if (fileName.isBlank()) return null
        return BatteryInfo(
            healthPercent     = prefs.getInt("cache_health_percent", -1).takeIf { it >= 0 },
            healthSource      = prefs.getString("cache_health_source", "")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { HealthSource.valueOf(it) }.getOrNull() },
            healthUnsupported = prefs.getBoolean("cache_health_unsupported", false),
            cycleCount        = prefs.getInt("cache_cycle_count", -1).takeIf { it >= 0 },
            asocRaw           = prefs.getString("cache_asoc_raw", "") ?: "",
            asocValue         = prefs.getInt("cache_asoc_value", MISSING_INT)
                .takeIf { it != MISSING_INT },
            bsohRaw           = prefs.getString("cache_bsoh_raw", "") ?: "",
            bsohPercent       = prefs.getInt("cache_bsoh_percent", -1).takeIf { it in 0..100 },
            bsohValue         = prefs.getInt("cache_bsoh_value", MISSING_INT)
                .takeIf { it != MISSING_INT },
            batteryUsageValue = prefs.getInt("cache_battery_usage_value", MISSING_INT)
                .takeIf { it != MISSING_INT },
            usageRaw          = prefs.getString("cache_usage_raw", "") ?: "",
            manufactureDateMs = prefs.getLong("cache_manufacture_date_ms", 0L),
            calibratedDateMs  = prefs.getLong("cache_calibrated_date_ms", 0L),
            firstUseDateMs    = prefs.getLong("cache_first_use_date_ms", 0L),
            activationDateMs  = prefs.getLong("cache_activation_date_ms", 0L),
            logFileName       = fileName,
            logTimestampMs    = prefs.getLong("cache_log_timestamp_ms", 0L),
            readSuccess       = true,
            chargeCounterMah  = prefs.getInt("cache_charge_counter_mah", -1).takeIf { it > 0 },
            measuredCapacityMah = prefs.getInt("cache_measured_capacity", -1).takeIf { it > 0 },
            capacityEstimated = prefs.getBoolean("cache_capacity_estimated", false),
            designCapacityMah = prefs.getInt("cache_design_capacity", -1).takeIf { it > 0 },
            assessment = BatteryHealthClassifier.assess(
                asoc = prefs.getInt("cache_asoc_value", MISSING_INT).takeIf { it != MISSING_INT },
                batteryUsage = prefs.getInt("cache_battery_usage_value", MISSING_INT)
                    .takeIf { it != MISSING_INT },
                bsoh = prefs.getInt("cache_bsoh_value", MISSING_INT).takeIf { it != MISSING_INT }
            )
        )
    }

    fun clearBatteryInfoCache(prefs: SharedPreferences) {
        prefs.edit {
            remove(KEY_SCHEMA_VERSION)
            remove("cache_log_file_name")
            remove("cache_log_timestamp_ms")
            remove("cache_health_percent")
            remove("cache_health_source")
            remove("cache_health_unsupported")
            remove("cache_cycle_count")
            remove("cache_asoc_raw")
            remove("cache_asoc_value")
            remove("cache_bsoh_raw")
            remove("cache_bsoh_percent")
            remove("cache_bsoh_value")
            remove("cache_battery_usage_value")
            remove("cache_usage_raw")
            remove("cache_manufacture_date_ms")
            remove("cache_calibrated_date_ms")
            remove("cache_first_use_date_ms")
            remove("cache_activation_date_ms")
            remove("cache_voltage_mv")
            remove("cache_temperature_c")
            remove("cache_current_ma")
            remove("cache_charge_counter_mah")
            remove("cache_measured_capacity")
            remove("cache_capacity_estimated")
            remove("cache_design_capacity")
            remove("cache_charge_status")
            remove("cache_power_source")
        }
    }
}