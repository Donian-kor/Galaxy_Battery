package com.alyaqdhan.galaxy_battery.viewmodel

import com.alyaqdhan.galaxy_battery.BatteryInfo
import com.alyaqdhan.galaxy_battery.ErrorDialog

/**
 * Snapshot of battery/scan state owned by [com.alyaqdhan.galaxy_battery.AppViewModel].
 * (Phase 3f — state grouping; full logic extraction to a separate ViewModel is a follow-up.)
 */
data class BatteryState(
    val batteryInfo: BatteryInfo? = null,
    val isRefreshing: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val errorDialog: ErrorDialog? = null
)