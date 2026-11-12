package com.alyaqdhan.galaxy_battery

data class BatteryInfo(
    val healthPercent: Int?,
    val healthSource: String = "",
    val healthUnsupported: Boolean = false,
    val cycleCount: Int?,
    val asocRaw: String = "",
    val asocValue: Int? = null,
    val bsohRaw: String = "",
    val bsohPercent: Int? = null,
    val bsohValue: Int? = null,
    val usageRaw: String = "",
    val batteryUsageValue: Int? = null,
    val llbType: String = "",
    val batteryDateMs: Long = 0L,
    val logFileName: String = "",
    val logTimestampMs: Long = 0L,
    val readSuccess: Boolean = false,
    val errorMessage: String = "",
    val chargeCounterMah: Int? = null,
    val measuredCapacityMah: Int? = null,
    val capacityEstimated: Boolean = false,
    val designCapacityMah: Int? = null
)

data class DocEntry(
    val id: String,
    val name: String,
    val mime: String,
    val lastModified: Long = 0L
)

/** Overlay error dialogs — replaces the three full-screen error routes. */
sealed class ErrorDialog {
    data class WrongFolder(val errorMessage: String) : ErrorDialog()
    object FolderDeleted  : ErrorDialog()
    object PermissionLost : ErrorDialog()
}
