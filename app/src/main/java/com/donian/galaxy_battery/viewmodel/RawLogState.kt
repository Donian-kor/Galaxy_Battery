package com.donian.galaxy_battery.viewmodel

/**
 * Snapshot of the raw-log screen state owned by [com.donian.galaxy_battery.AppViewModel].
 * (Phase 3e)
 */
data class RawLogState(
    val rawLogText: String = "",
    val isLoadingRaw: Boolean = false,
    val selectedLogFileName: String = ""
)