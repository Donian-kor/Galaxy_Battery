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
            putLong   ("cache_battery_date_ms",  info.batteryDateMs)
            putInt    ("cache_voltage_mv",          info.voltageMv ?: -1)
            putFloat  ("cache_temperature_c",       info.temperatureC ?: -999f)
            putInt    ("cache_current_ma",          info.currentNowMa ?: Int.MIN_VALUE)
            putInt    ("cache_charge_counter_mah",  info.chargeCounterMah ?: -1)
            putInt    ("cache_measured_capacity",   info.measuredCapacityMah ?: -1)
            putBoolean("cache_capacity_estimated",  info.capacityEstimated)
            putInt    ("cache_design_capacity",     info.designCapacityMah ?: -1)
            putInt    ("cache_charge_status",       info.chargeStatus ?: -1)
            putString ("cache_power_source",        info.powerSource.name)
        }
    }

    fun loadCachedBatteryInfo(prefs: SharedPreferences): BatteryInfo? {
        val fileName = prefs.getString("cache_log_file_name", null) ?: return null
        if (fileName.isBlank()) return null
        val current = prefs.getInt("cache_current_ma", Int.MIN_VALUE)
        return BatteryInfo(
            healthPercent     = prefs.getInt("cache_health_percent", -1).takeIf { it >= 0 },
            healthSource      = prefs.getString("cache_health_source", "") ?: "",
            healthUnsupported = prefs.getBoolean("cache_health_unsupported", false),
            cycleCount        = prefs.getInt("cache_cycle_count", -1).takeIf { it >= 0 },
            batteryDateMs    = prefs.getLong("cache_battery_date_ms", 0L),
            logFileName       = fileName,
            logTimestampMs    = prefs.getLong("cache_log_timestamp_ms", 0L),
            readSuccess       = true,
            voltageMv         = prefs.getInt("cache_voltage_mv", -1).takeIf { it > 0 },
            temperatureC      = prefs.getFloat("cache_temperature_c", -999f).takeIf { it > -999f },
            currentNowMa      = current.takeIf { it != Int.MIN_VALUE },
            chargeCounterMah  = prefs.getInt("cache_charge_counter_mah", -1).takeIf { it > 0 },
            measuredCapacityMah = prefs.getInt("cache_measured_capacity", -1).takeIf { it > 0 },
            capacityEstimated = prefs.getBoolean("cache_capacity_estimated", false),
            designCapacityMah = prefs.getInt("cache_design_capacity", -1).takeIf { it > 0 },
            chargeStatus      = prefs.getInt("cache_charge_status", -1).takeIf { it in 1..5 },
            powerSource       = prefs.getString("cache_power_source", null)
                ?.let { name -> runCatching { PowerSource.valueOf(name) }.getOrNull() }
                ?: PowerSource.NONE
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
