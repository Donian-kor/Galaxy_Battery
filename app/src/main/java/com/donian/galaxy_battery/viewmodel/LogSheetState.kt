package com.donian.galaxy_battery.viewmodel

import com.donian.galaxy_battery.DocEntry

/**
 * Snapshot of the log-sheet state owned by [com.donian.galaxy_battery.AppViewModel].
 * (Phase 3d)
 */
data class LogSheetState(
    val allLogEntries: List<DocEntry> = emptyList(),
    val showLogSheet: Boolean = false
)