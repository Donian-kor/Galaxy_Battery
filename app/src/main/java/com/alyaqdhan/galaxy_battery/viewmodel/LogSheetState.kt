package com.alyaqdhan.galaxy_battery.viewmodel

import com.alyaqdhan.galaxy_battery.DocEntry

/**
 * Snapshot of the log-sheet state owned by [com.alyaqdhan.galaxy_battery.AppViewModel].
 * (Phase 3d)
 */
data class LogSheetState(
    val allLogEntries: List<DocEntry> = emptyList(),
    val showLogSheet: Boolean = false
)