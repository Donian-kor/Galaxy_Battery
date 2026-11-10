package com.alyaqdhan.galaxy_battery

import android.content.SharedPreferences
import androidx.core.content.edit

object BatteryCache {

    fun cacheBatteryInfo(prefs: SharedPreferences, info: BatteryInfo) {
        if (!info.readSuccess) return
        prefs.edit {
            putString ("cache_log_file_name",      info.logFileName)
            putLong   ("cache_log_timestamp_ms",   info.logTimestampMs)
            putInt    ("cache_health_percent",      info.healthPercent ?: -1)
            putString ("cache_health_source",      info.healthSource)
            putBoolean("cache_health_unsupported", info.healthUnsupported)
            putInt    ("cache_cycle_count",         info.cycleCount ?: -1)
            putString ("cache_asoc_raw",            info.asocRaw)
            putInt    ("cache_asoc_value",          info.asocValue ?: Int.MIN_VALUE)
            putString ("cache_bsoh_raw",            info.bsohRaw)
            putInt    ("cache_bsoh_percent",        info.bsohPercent ?: -1)
            putInt    ("cache_battery_usage_value", info.batteryUsageValue ?: Int.MIN_VALUE)
            putString ("cache_usage_raw",           info.usageRaw)
            putLong   ("cache_battery_date_ms",  info.batteryDateMs)
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
        val fileName = prefs.getString("cache_log_file_name", null) ?: return null
        if (fileName.isBlank()) return null
        return BatteryInfo(
            healthPercent     = prefs.getInt("cache_health_percent", -1).takeIf { it >= 0 },
            healthSource      = prefs.getString("cache_health_source", "") ?: "",
            healthUnsupported = prefs.getBoolean("cache_health_unsupported", false),
            cycleCount        = prefs.getInt("cache_cycle_count", -1).takeIf { it >= 0 },
            asocRaw           = prefs.getString("cache_asoc_raw", "") ?: "",
            asocValue         = prefs.getInt("cache_asoc_value", Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            bsohRaw           = prefs.getString("cache_bsoh_raw", "") ?: "",
            bsohPercent       = prefs.getInt("cache_bsoh_percent", -1).takeIf { it in 0..100 },
            batteryUsageValue = prefs.getInt("cache_battery_usage_value", Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE },
            usageRaw          = prefs.getString("cache_usage_raw", "") ?: "",
            batteryDateMs    = prefs.getLong("cache_battery_date_ms", 0L),
            logFileName       = fileName,
            logTimestampMs    = prefs.getLong("cache_log_timestamp_ms", 0L),
            readSuccess       = true,
            chargeCounterMah  = prefs.getInt("cache_charge_counter_mah", -1).takeIf { it > 0 },
            measuredCapacityMah = prefs.getInt("cache_measured_capacity", -1).takeIf { it > 0 },
            capacityEstimated = prefs.getBoolean("cache_capacity_estimated", false),
            designCapacityMah = prefs.getInt("cache_design_capacity", -1).takeIf { it > 0 }
        )
    }

    fun clearBatteryInfoCache(prefs: SharedPreferences) {
        prefs.edit {
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
            remove("cache_battery_usage_value")
            remove("cache_usage_raw")
            remove("cache_battery_date_ms")
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
