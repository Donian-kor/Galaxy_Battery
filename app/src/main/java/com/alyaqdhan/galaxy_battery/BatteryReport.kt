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
        // 배터리 장착일 표기 방식만 로케일을 따르고, 리포트 문구는 리소스가 담당한다.
        val dateStyleKorean = context.resources.configuration.locales[0]?.language == "ko"

        fun value(text: String?): String = text?.takeIf { it.isNotBlank() } ?: none
        fun grouped(number: Int?): String? =
            number?.let { String.format(Locale.US, "%,d", it) }
        fun capacity(number: Int?): String? =
            number?.let { String.format(Locale.US, "%,d mAh", it) }

        val assessment = info.assessment

        val scoreStr = if (assessment.status == BatteryHealthStatus.OK && assessment.score != null) {
            "${assessment.score}%"
        } else {
            when (assessment.status) {
                BatteryHealthStatus.INSUFFICIENT_CYCLES -> context.getString(R.string.status_insufficient_cycles)
                BatteryHealthStatus.HEALTH_UNAVAILABLE -> context.getString(R.string.status_health_unavailable)
                BatteryHealthStatus.INVALID_DATA -> context.getString(R.string.status_invalid_data)
                BatteryHealthStatus.OK -> none
            }
        }

        val gradeStr = when (assessment.grade) {
            BatteryHealthGrade.GOOD -> context.getString(R.string.assessment_grade_good)
            BatteryHealthGrade.NORMAL -> context.getString(R.string.assessment_grade_normal)
            BatteryHealthGrade.WEAK -> context.getString(R.string.assessment_grade_weak)
            BatteryHealthGrade.BAD -> when (assessment.badReason) {
                BatteryHealthBadReason.END_OF_LIFE -> context.getString(R.string.assessment_grade_bad_end_of_life)
                BatteryHealthBadReason.EARLY_DEGRADATION -> context.getString(R.string.assessment_grade_bad_early_degradation)
                null -> context.getString(R.string.assessment_grade_bad)
            }
            BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE -> context.getString(R.string.assessment_grade_diagnostic_unavailable)
            BatteryHealthGrade.CALCULATION_UNAVAILABLE -> context.getString(R.string.assessment_grade_calculation_unavailable)
            BatteryHealthGrade.UNDETERMINED -> context.getString(R.string.assessment_grade_undetermined)
        }

        val sourceStr = when (assessment.healthSource) {
            HealthSource.BSOH -> context.getString(R.string.health_source_bsoh_basis)
            HealthSource.ASOC -> context.getString(R.string.health_source_asoc_basis)
            null -> none
        }

        val lines = mutableListOf<String>()
        lines += context.getString(R.string.report_subject)
        lines += "────────────────────────"
        lines += context.getString(
            R.string.report_score,
            value(scoreStr)
        )
        lines += context.getString(
            R.string.report_grade,
            value(gradeStr)
        )
        lines += context.getString(
            R.string.report_health_val,
            value(assessment.healthValue?.let { "$it%" })
        )
        lines += context.getString(
            R.string.report_health_source,
            value(sourceStr)
        )
        lines += context.getString(
            R.string.report_bsoh_raw,
            value(info.bsohRaw)
        )
        lines += context.getString(
            R.string.report_asoc_raw,
            value(info.asocRaw)
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
            value(formatBatteryDate(info.calibratedDateMs, dateStyleKorean))
        )
        lines += context.getString(
            R.string.report_manufacture_date,
            value(formatBatteryDate(info.manufactureDateMs, dateStyleKorean))
        )
        lines += context.getString(
            R.string.report_first_use_date,
            value(formatBatteryDate(info.firstUseDateMs, dateStyleKorean))
        )
        lines += context.getString(
            R.string.report_activation_date,
            value(formatBatteryDate(info.activationDateMs, dateStyleKorean))
        )

        return lines.joinToString("\n")
    }
}
