package com.donian.galaxy_battery.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

import com.donian.galaxy_battery.R
import com.donian.galaxy_battery.model.*
import com.donian.galaxy_battery.ui.common.batteryDateText
import com.donian.galaxy_battery.ui.common.groupedDigits
import com.donian.galaxy_battery.ui.theme.TelemetryFontFamily
import com.donian.galaxy_battery.ui.theme.appColors
import com.donian.galaxy_battery.ui.theme.cardBorderColor
import com.donian.galaxy_battery.ui.theme.gaugeGreen
import com.donian.galaxy_battery.ui.theme.gaugeOrange
import com.donian.galaxy_battery.ui.theme.gaugeRed
import com.donian.galaxy_battery.ui.theme.textMuted
import com.donian.galaxy_battery.ui.theme.textPrimary
import com.donian.galaxy_battery.ui.theme.textSecondary

// ── 텔레메트리 카드: 사이클·용량·주요 판독값 ─────────────────────────────────

@Composable
fun TelemetryCard(
    info: BatteryInfo,
    isLoadingDetail: Boolean = false,
    gaugeReplayKey: Int = 0
) {
    val cycles     = info.cycleCount
    val measured   = info.measuredCapacityMah
    val design     = info.designCapacityMah
    val health     = info.healthPercent
    val cyclesAnim = remember { Animatable(0f) }
    val capAnim    = remember { Animatable(0f) }

    LaunchedEffect(gaugeReplayKey, isLoadingDetail, cycles) {
        if (isLoadingDetail) {
            cyclesAnim.snapTo(0f)
        } else {
            cyclesAnim.snapTo(0f)
            delay(200)
            cyclesAnim.animateTo(
                (cycles ?: 0).toFloat(),
                tween(1200, easing = FastOutSlowInEasing)
            )
        }
    }
    LaunchedEffect(gaugeReplayKey, isLoadingDetail, measured) {
        if (isLoadingDetail) {
            capAnim.snapTo(0f)
        } else {
            capAnim.snapTo(0f)
            delay(200)
            capAnim.animateTo(
                (measured ?: 0).toFloat(),
                tween(1200, easing = FastOutSlowInEasing)
            )
        }
    }

    val muted  = textMuted()
    val border = cardBorderColor()

    DashboardCard {
        BatteryMetaSection(info, isLoadingDetail)
        Spacer(Modifier.height(14.dp))

        // 지표 타일들
        val cyclesFraction = (cyclesAnim.value / 800f).coerceIn(0f, 1f)
        val cyclesMetric = MetricTileData(
            title = stringResource(R.string.cycles_metric_title),
            value = if (isLoadingDetail || cycles == null) "—"
            else cyclesAnim.value.toInt().toString(),
            unit = stringResource(R.string.cycles_metric_unit),
            fraction = if (isLoadingDetail) 0f else cyclesFraction,
            barColor = when {
                cyclesFraction < 0.6f -> gaugeGreen()
                cyclesFraction < 0.9f -> gaugeOrange()
                else -> gaugeRed()
            },
            footerStart = if (isLoadingDetail) "…"
            else stringResource(
                R.string.cycles_progress,
                String.format(Locale.US, "%.1f", cyclesFraction * 100f)
            ),
            footerEnd = if (cycles == null) "—"
            else stringResource(R.string.cycles_warranty, "800"),
            footerEndColor = muted
        )

        val capacityFraction = when {
            measured != null && design != null && design > 0 ->
                (measured.toFloat() / design).coerceIn(0f, 1f)
            health != null ->
                health / 100f
            else ->
                0f
        }
        val capacityBarColor = when {
            capacityFraction >= 0.9f -> gaugeGreen()
            capacityFraction >= 0.6f -> gaugeOrange()
            else -> gaugeRed()
        }
        val delta = if (measured != null && design != null) measured - design else null

        val capacityMetric = MetricTileData(
            title = stringResource(
                if (info.capacityEstimated) R.string.capacity_metric_title_estimated
                else R.string.capacity_metric_title
            ),
            value = if (isLoadingDetail || measured == null) "—"
            else capAnim.value.toInt().groupedDigits(),
            unit = stringResource(R.string.capacity_unit),
            fraction = if (isLoadingDetail) 0f else capacityFraction,
            barColor = capacityBarColor,
            footerStart = when {
                isLoadingDetail -> "…"
                design != null -> stringResource(R.string.capacity_design, design.groupedDigits())
                else -> stringResource(R.string.capacity_no_design)
            },
            footerEnd = when {
                delta == null -> ""
                delta == 0 -> "0"
                else -> stringResource(
                    R.string.capacity_delta,
                    String.format(Locale.US, "%+,d", delta)
                )
            },
            footerEndColor = when {
                delta == null -> muted
                delta < 0 -> gaugeOrange()
                else -> gaugeGreen()
            }
        )

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 360.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(Modifier.fillMaxWidth(), cyclesMetric)
                    MetricTile(Modifier.fillMaxWidth(), capacityMetric)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricTile(Modifier.weight(1f).fillMaxHeight(), cyclesMetric)
                    MetricTile(Modifier.weight(1f).fillMaxHeight(), capacityMetric)
                }
            }
        }

    }
}

private data class MetricTileData(
    val title: String,
    val value: String,
    val secondaryValue: String? = null,
    val unit: String,
    val fraction: Float,
    val barColor: Color,
    val footerStart: String,
    val footerEnd: String,
    val footerEndColor: Color
)

@Composable
private fun MetricTile(
    modifier: Modifier,
    data: MetricTileData
) {
    val muted  = textMuted()
    val border = cardBorderColor()

    Column(
        modifier
            .background(appColors().inset.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            data.title,
            color = muted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                data.value,
                color = textPrimary(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = TelemetryFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 22.sp
                ),
                maxLines = 1
            )
            if (data.secondaryValue != null) {
                Text(
                    data.secondaryValue,
                    color = textSecondary(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = TelemetryFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize   = 14.sp
                    ),
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                data.unit,
                color = muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(border, RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(data.fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(data.barColor, RoundedCornerShape(3.dp))
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                data.footerStart,
                color = muted,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                data.footerEnd,
                color = data.footerEndColor,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1
            )
        }
    }
}

/**
 * 배터리 날짜.
 *
 * 배터리 날짜 정보.
 *
 * 배터리 장착일로 쓸 수 있는 값이 없다. `LLB CAL`은 조건부로만 장착일을 의미하고
 * 최신 기종에서는 비어 있어, 사용자에게 단정적으로 보여줄 근거가 되지 못한다.
 * 그래서 제조일(`LLB MAN`)과 최초 통화일(`ril.actdate`)만 보여준다.
 *
 * `battery FirstUseDate`는 기기(메인보드) 최초 사용일이라 배터리 날짜가 아니다.
 * 폭이 좁으면 한 줄에 겹치므로 두 줄로 나눠 표시한다.
 */
@Composable
private fun BatteryMetaSection(
    info: BatteryInfo,
    isLoading: Boolean
) {
    val manufactureDate = batteryDateText(info.manufactureDateMs)
    val activationDate = batteryDateText(info.activationDateMs)

    val primaryLabel = when {
        isLoading -> stringResource(R.string.log_pill_loading)
        manufactureDate.isNotEmpty() -> stringResource(R.string.battery_manufactured_on, manufactureDate)
        else -> stringResource(R.string.battery_date_unknown)
    }
    // 제조일이 기본 줄에 없을 때만 최초 통화일을 보조 줄에 덧붙인다.
    val secondaryLabel = activationDate
        .takeIf { it.isNotEmpty() && it != manufactureDate }
        ?.let { stringResource(R.string.device_activated_on, it) }

    val muted = textMuted()
    val inset = appColors().inset

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(inset.copy(alpha = 0.6f))
            .border(1.dp, cardBorderColor(), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        BatteryDateIcon()
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BatteryDateLabel(primaryLabel, muted, Modifier.fillMaxWidth())
            secondaryLabel?.let { BatterySecondaryLabel(it, muted) }
        }
    }
}

@Composable
private fun BatterySecondaryLabel(text: String, color: Color) {
    Text(
        text,
        color = color.copy(alpha = 0.75f),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun BatteryDateIcon() {
    Icon(
        painterResource(R.drawable.ic_sym_calendar_month),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(14.dp)
    )
}

@Composable
private fun BatteryDateLabel(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
