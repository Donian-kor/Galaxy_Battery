package com.donian.galaxy_battery.viewmodel

import android.net.Uri

/**
 * Snapshot of the folder/permission state owned by [com.donian.galaxy_battery.AppViewModel].
 *
 * Grouping these four flags into one value lets Compose observe them as a single
 * unit and keeps the ViewModel's mutable state count manageable (Phase 3a).
 */
data class FolderState(
    val folderUri: Uri? = null,
    val alreadyHasPerm: Boolean = false,
    val folderAccessible: Boolean = true,
    val hasEverScanned: Boolean = false
)