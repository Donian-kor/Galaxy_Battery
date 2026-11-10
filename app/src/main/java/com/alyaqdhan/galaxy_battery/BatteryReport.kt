package com.alyaqdhan.galaxy_battery

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the plain-text diagnostic report that the dashboard's export button shares.
 * It only carries values the parser actually read; anything missing is written as
 * "정보 없음" rather than invented.
 */
object BatteryReport {

    fun build(context: Context, info: BatteryInfo): String {
        val none   = context.getString(R.string.report_none)
        val arabic = context.resources.configuration.locales[0]?.language == "ar"

        fun value(text: String?): String = text?.takeIf { it.isNotBlank() } ?: none
        fun grouped(number: Int?): String? =
            number?.let { String.format(Locale.US, "%,d", it) }
        fun capacity(number: Int?): String? =
            number?.let { String.format(Locale.US, "%,d mAh", it) }

        val lines = mutableListOf<String>()
        lines += context.getString(R.string.report_subject)
        lines += "────────────────────────"
        lines += context.getString(
            R.string.report_health,
            value(info.healthPercent?.let { "$it%" })
        )
        lines += context.getString(
            R.string.report_health_source,
            value(info.healthSource.uppercase(Locale.US))
        )
        lines += context.getString(R.string.report_cycles, value(grouped(info.cycleCount)))
        lines += context.getString(
            R.string.report_design_capacity,
            value(capacity(info.designCapacityMah))
        )
        lines += context.getString(
            if (info.capacityEstimated) R.string.report_measured_capacity_estimated
            else R.string.report_measured_capacity,
            value(capacity(info.measuredCapacityMah))
        )
        lines += context.getString(
            R.string.report_voltage,
            value(info.voltageMv?.let { String.format(Locale.US, "%,d mV", it) })
        )
        lines += context.getString(
            R.string.report_temperature,
            value(info.temperatureC?.let { String.format(Locale.US, "%.1f ℃", it) })
        )
        lines += context.getString(
            R.string.report_current,
            value(info.currentNowMa?.let { String.format(Locale.US, "%+,d mA", it) })
        )
        lines += context.getString(
            R.string.report_log_file,
            value(info.logFileName)
        )
        lines += context.getString(
            R.string.report_log_time,
            value(
                if (info.logTimestampMs == 0L) ""
                else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(Date(info.logTimestampMs))
            )
        )
        lines += context.getString(
            R.string.report_install_date,
            value(formatBatteryDate(info.batteryDateMs, arabic))
        )

        return lines.joinToString("\n")
    }
}
