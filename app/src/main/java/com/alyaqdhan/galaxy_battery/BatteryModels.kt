package com.alyaqdhan.galaxy_battery

/** Where the phone is drawing power from, as reported by the battery service dump. */
enum class PowerSource { NONE, AC, USB, WIRELESS }

/** BatteryManager.BATTERY_STATUS_* values, kept as plain numbers so the parser stays pure. */
object ChargeStatus {
    const val UNKNOWN      = 1
    const val CHARGING     = 2
    const val DISCHARGING  = 3
    const val NOT_CHARGING = 4
    const val FULL         = 5
}

data class BatteryInfo(
    val healthPercent: Int?,
    val healthSource: String = "",
    val healthUnsupported: Boolean = false,
    val cycleCount: Int?,
    val asocRaw: String = "",
    val bsohRaw: String = "",
    val usageRaw: String = "",
    val llbType: String = "",
    val batteryDateMs: Long = 0L,
    val logFileName: String = "",
    val logTimestampMs: Long = 0L,
    val readSuccess: Boolean = false,
    val errorMessage: String = "",
    // Live telemetry from the "Current Battery Service state" block. Null when the log
    // doesn't report it, so the dashboard can show a dash instead of a made-up number.
    val voltageMv: Int? = null,
    val temperatureC: Float? = null,
    val currentNowMa: Int? = null,
    val chargeCounterMah: Int? = null,
    val measuredCapacityMah: Int? = null,
    val capacityEstimated: Boolean = false,
    val designCapacityMah: Int? = null,
    val chargeStatus: Int? = null,
    val powerSource: PowerSource = PowerSource.NONE
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
