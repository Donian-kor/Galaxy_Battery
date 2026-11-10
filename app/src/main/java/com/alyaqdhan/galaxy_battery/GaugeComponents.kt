package com.alyaqdhan.galaxy_battery
import com.alyaqdhan.galaxy_battery.ui.theme.*
import androidx.compose.ui.res.pluralStringResource

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

// ── Shared card shell ─────────────────────────────────────────────────────────

/** The dashboard's card: fixed border, recessed page-colored blocks inside it. */
@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    // The hero card's soft corner highlight, straight from the design.
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor())
            .border(1.dp, cardBorderColor(), RoundedCornerShape(20.dp))
    ) {
        if (glow) {
            Box(
                Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 52.dp, y = (-52).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(padding),
            content = content
        )
    }
}

/** The design's "live" dot: a solid core with a fading, expanding ring. */
@Composable
private fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "gauge_dot")
    val ringAlpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "dot_alpha"
    )
    val ringScale by transition.animateFloat(
        initialValue = 1f,
        targetValue  = 2.2f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "dot_scale"
    )

    Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(8.dp)
                .scale(ringScale)
                .alpha(ringAlpha)
                .background(color, CircleShape)
        )
        Box(Modifier.size(8.dp).background(color, CircleShape))
    }
}

// ── SOH hero card ─────────────────────────────────────────────────────────────

@Composable
fun HealthGaugeCard(
    info: BatteryInfo,
    isLoadingDetail: Boolean = false,
    arcAnimatable: Animatable<Float, AnimationVector1D>,
    amplitude: Float,
    gaugeReplayKey: Int = 0
) {
    val isUnsupported = info.healthUnsupported
    val targetPct =
        if (isUnsupported) 100f else (info.healthPercent ?: 0).coerceIn(0, 100).toFloat()

    LaunchedEffect(isLoadingDetail, targetPct, gaugeReplayKey) {
        if (isLoadingDetail) {
            arcAnimatable.animateTo(
                targetValue   = 1f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        } else {
            if (arcAnimatable.value >= 0.02f) arcAnimatable.snapTo(0f)
            arcAnimatable.animateTo(
                targetValue   = targetPct / 100f,
                animationSpec = tween(1400, easing = FastOutSlowInEasing)
            )
        }
    }

    val animPct = arcAnimatable.value * 100f

    val gaugeColor by animateColorAsState(
        targetValue = when {
            isLoadingDetail   -> gaugeBlue()
            isUnsupported     -> gaugeGray()
            animPct >= 80f    -> gaugeGreen()
            animPct >= 60f    -> gaugeOrange()
            else              -> gaugeRed()
        },
        animationSpec = tween(300),
        label = "gauge_color"
    )

    val statusLabel = stringResource(
        when {
            isLoadingDetail  -> R.string.status_loading
            isUnsupported    -> R.string.status_unsupported
            animPct >= 99.9f -> R.string.health_new
            animPct >= 80f   -> R.string.health_good
            animPct >= 60f   -> R.string.health_fair
            else             -> R.string.health_poor
        }
    )

    val border = cardBorderColor()
    val inset  = appColors().inset

    DashboardCard(glow = true) {
        // Header: live dot + title only (source chip moved to bottom analysis card)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulsingDot(gaugeColor)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.gauge_title_soh),
                color = textPrimary(),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val lowTitle = stringResource(R.string.gauge_scale_low_title)
            val lowDescription = stringResource(R.string.gauge_scale_low_desc)
            val midTitle = stringResource(R.string.gauge_scale_mid_title)
            val midDescription = stringResource(R.string.gauge_scale_mid_desc)
            val highTitle = stringResource(R.string.gauge_scale_high_title)
            val highDescription = stringResource(R.string.gauge_scale_high_desc)
            val compactGaugeSize = minOf(maxWidth, 208.dp)

            if (maxWidth < 360.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HealthGaugeDial(
                        modifier = Modifier.size(compactGaugeSize),
                        animPct = animPct,
                        isLoadingDetail = isLoadingDetail,
                        isUnsupported = isUnsupported,
                        amplitude = amplitude,
                        statusLabel = statusLabel,
                        gaugeColor = gaugeColor,
                        inset = inset,
                        border = border
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScaleMarker(Modifier.weight(1f), lowTitle, lowDescription, gaugeRed())
                        ScaleMarker(Modifier.weight(1f), midTitle, midDescription, gaugeOrange())
                        ScaleMarker(Modifier.weight(1f), highTitle, highDescription, gaugeGreen())
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HealthGaugeDial(
                        modifier = Modifier.weight(1f).size(208.dp),
                        animPct = animPct,
                        isLoadingDetail = isLoadingDetail,
                        isUnsupported = isUnsupported,
                        amplitude = amplitude,
                        statusLabel = statusLabel,
                        gaugeColor = gaugeColor,
                        inset = inset,
                        border = border
                    )
                    Column(
                        Modifier.weight(0.4f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ScaleMarker(Modifier.fillMaxWidth(), lowTitle, lowDescription, gaugeRed())
                        Spacer(Modifier.height(8.dp))
                        ScaleMarker(Modifier.fillMaxWidth(), midTitle, midDescription, gaugeOrange())
                        Spacer(Modifier.height(8.dp))
                        ScaleMarker(Modifier.fillMaxWidth(), highTitle, highDescription, gaugeGreen())
                    }
                }
            }
        }

    }
}

@Composable
private fun HealthGaugeDial(
    modifier: Modifier,
    animPct: Float,
    isLoadingDetail: Boolean,
    isUnsupported: Boolean,
    amplitude: Float,
    statusLabel: String,
    gaugeColor: Color,
    inset: Color,
    border: Color
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        GaugeArc(
            progress = animPct / 100f,
            trackColor = border,
            fillColor = gaugeColor,
            pulse = if (isLoadingDetail) amplitude else 1f,
            modifier = Modifier.fillMaxSize()
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (isLoadingDetail || isUnsupported) "—" else animPct.toInt().toString(),
                    color = textPrimary(),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = TelemetryFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 42.sp
                    )
                )
                Text(
                    stringResource(R.string.gauge_percent_sign),
                    color = gaugeColor,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = TelemetryFontFamily,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = CircleShape,
                color = inset.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, gaugeColor.copy(alpha = 0.4f))
            ) {
                Row(
                    Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).background(gaugeColor, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        statusLabel,
                        color = gaugeColor,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ScaleMarker(
    modifier: Modifier,
    title: String,
    description: String,
    color: Color
) {
    Column(
        modifier
            .background(appColors().inset.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            description,
            color = textMuted(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Draws the design's open 270° dial: a recessed track plus a layered glowing arc. */
@Composable
private fun GaugeArc(
    progress: Float,
    trackColor: Color,
    fillColor: Color,
    pulse: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        // The design's SVG uses r=64 inside a 160 box with a 9-wide stroke.
        val ringDiameter = size.minDimension * 0.856f
        val strokeWidth  = size.minDimension * 0.058f
        val topLeft = Offset(
            (size.width - ringDiameter) / 2f,
            (size.height - ringDiameter) / 2f
        )
        val arcSize = Size(ringDiameter, ringDiameter)
        val start   = 135f
        val full    = 270f

        drawArc(
            color      = trackColor,
            startAngle = start,
            sweepAngle = full,
            useCenter  = false,
            topLeft    = topLeft,
            size       = arcSize,
            style      = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        val sweep = full * progress.coerceIn(0f, 1f)
        if (sweep > 0.1f) {
            val glow = 0.06f + 0.10f * pulse.coerceIn(0f, 1f)
            // Cheap glow: a few wider, fainter strokes behind the crisp arc.
            drawArc(fillColor.copy(alpha = glow * 0.45f), start, sweep, false, topLeft, arcSize,
                style = Stroke(width = strokeWidth * 3.2f, cap = StrokeCap.Round))
            drawArc(fillColor.copy(alpha = glow * 0.7f), start, sweep, false, topLeft, arcSize,
                style = Stroke(width = strokeWidth * 2.1f, cap = StrokeCap.Round))
            drawArc(fillColor.copy(alpha = glow), start, sweep, false, topLeft, arcSize,
                style = Stroke(width = strokeWidth * 1.45f, cap = StrokeCap.Round))
            drawArc(fillColor, start, sweep, false, topLeft, arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }
    }
}

// ── Telemetry card: cycles, capacity and the vital readouts ───────────────────

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

        // Metric tiles
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

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = border.copy(alpha = 0.6f))
        Spacer(Modifier.height(10.dp))

        // Vital telemetry rows
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val voltage = info.voltageMv
            VitalRow(
                icon       = R.drawable.ic_sym_speed,
                label      = stringResource(R.string.telemetry_voltage),
                value      = if (voltage == null) stringResource(R.string.telemetry_no_data)
                else stringResource(R.string.telemetry_mv, voltage.groupedDigits()),
                valueColor = textPrimary()
            )

            val temperature = info.temperatureC
            val tempChip = when {
                temperature == null -> null
                temperature >= 45f  -> stringResource(R.string.telemetry_temp_warm)
                temperature <= 10f  -> stringResource(R.string.telemetry_temp_cold)
                else                -> stringResource(R.string.telemetry_temp_normal)
            }
            val tempChipColor = when {
                temperature == null -> muted
                temperature >= 45f  -> gaugeRed()
                temperature <= 10f  -> gaugeBlue()
                else                -> gaugeGreen()
            }
            VitalRow(
                icon        = R.drawable.ic_sym_thermostat,
                label       = stringResource(R.string.telemetry_temperature),
                value       = if (temperature == null) stringResource(R.string.telemetry_no_data)
                else stringResource(
                    R.string.telemetry_celsius,
                    String.format(Locale.US, "%.1f", temperature)
                ),
                valueColor  = textPrimary(),
                chip        = tempChip,
                chipColor   = tempChipColor
            )

            val current    = info.currentNowMa
            val charging   = info.chargeStatus == ChargeStatus.CHARGING ||
                    (info.powerSource != PowerSource.NONE &&
                            info.chargeStatus != ChargeStatus.DISCHARGING &&
                            info.chargeStatus != ChargeStatus.FULL)
            val currentChip = when {
                info.chargeStatus == ChargeStatus.FULL          -> stringResource(R.string.telemetry_full)
                info.powerSource == PowerSource.AC              -> stringResource(R.string.telemetry_charging_ac)
                info.powerSource == PowerSource.USB             -> stringResource(R.string.telemetry_charging_usb)
                info.powerSource == PowerSource.WIRELESS        -> stringResource(R.string.telemetry_charging_wireless)
                info.chargeStatus == ChargeStatus.DISCHARGING   -> stringResource(R.string.telemetry_discharging)
                else                                            -> null
            }
            val currentChipColor = when {
                info.chargeStatus == ChargeStatus.FULL -> gaugeGreen()
                charging                               -> gaugeGreen()
                else                                   -> muted
            }
            VitalRow(
                icon        = R.drawable.ic_sym_input,
                label       = stringResource(
                    if (charging) R.string.telemetry_current_charge
                    else R.string.telemetry_current_discharge
                ),
                value       = if (current == null) stringResource(R.string.telemetry_no_data)
                else stringResource(
                    R.string.telemetry_ma,
                    String.format(Locale.US, "%+,d", current)
                ),
                valueColor  = if (charging) gaugeGreen() else textSecondary(),
                chip        = currentChip,
                chipColor   = currentChipColor
            )
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

@Composable
private fun BatteryMetaSection(
    info: BatteryInfo,
    isLoading: Boolean
) {
    val installedDate = batteryDateText(info.batteryDateMs)
    val monthsUsed = if (isLoading) null else monthsSince(info.batteryDateMs)
    val muted = textMuted()
    val border = cardBorderColor()
    val inset = appColors().inset

    val installedLabel = when {
        isLoading -> stringResource(R.string.log_pill_loading)
        installedDate.isNotEmpty() -> stringResource(R.string.battery_installed_on, installedDate)
        else -> stringResource(R.string.battery_install_unknown)
    }
    val monthsLabel = monthsUsed?.let {
        pluralStringResource(R.plurals.battery_months_used, it, it)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(inset.copy(alpha = 0.6f))
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        if (maxWidth < 360.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryDateIcon()
                Spacer(Modifier.width(8.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BatteryDateLabel(installedLabel, muted, Modifier.fillMaxWidth())
                    monthsLabel?.let { BatteryMonthsBadge(it, border) }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryDateIcon()
                Spacer(Modifier.width(6.dp))
                BatteryDateLabel(installedLabel, muted, Modifier.weight(1f))
                monthsLabel?.let {
                    Spacer(Modifier.width(8.dp))
                    BatteryMonthsBadge(it, border)
                }
            }
        }
    }
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

@Composable
private fun BatteryMonthsBadge(text: String, border: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, border.copy(alpha = 0.4f))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun VitalRow(
    icon: Int,
    label: String,
    value: String,
    valueColor: Color,
    chip: String? = null,
    chipColor: Color = Color.Unspecified
) {
    val muted  = textMuted()
    val border = cardBorderColor()

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(appColors().inset.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .border(1.dp, border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        if (maxWidth < 320.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VitalRowLabel(icon, label, chip, chipColor, muted, Modifier.fillMaxWidth())
                Text(
                    value,
                    color = valueColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = TelemetryFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VitalRowLabel(icon, label, chip, chipColor, muted, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    value,
                    color = valueColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = TelemetryFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun VitalRowLabel(
    icon: Int,
    label: String,
    chip: String?,
    chipColor: Color,
    muted: Color,
    modifier: Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = muted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = textSecondary(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (chip != null) {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = chipColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, chipColor.copy(alpha = 0.3f))
            ) {
                Text(
                    chip,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = chipColor,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

// ── Analysis summary card ─────────────────────────────────────────────────────

@Composable
fun LogSummaryCard(
    info: BatteryInfo,
    isLoadingDetail: Boolean = false
) {
    val ok      = info.readSuccess && !isLoadingDetail
    val accent  = when {
        isLoadingDetail -> gaugeBlue()
        info.readSuccess -> gaugeGreen()
        else             -> gaugeGray()
    }
    val muted   = textMuted()
    val border  = cardBorderColor()
    val inset   = appColors().inset

    val title = stringResource(
        when {
            isLoadingDetail    -> R.string.analysis_title_loading
            info.readSuccess   -> R.string.analysis_title_ok
            else               -> R.string.analysis_title_failed
        }
    )
    val footerEnd = when {
        isLoadingDetail          -> "…"
        info.readSuccess         -> stringResource(R.string.analysis_no_error)
        info.errorMessage.isNotBlank() -> stringResource(R.string.analysis_error, info.errorMessage)
        else                     -> stringResource(R.string.analysis_no_error)
    }
    val asocRaw = info.asocRaw.ifBlank {
        if (info.healthSource.equals("asoc", ignoreCase = true)) {
            info.healthPercent?.toString() ?: "—"
        } else {
            "—"
        }
    }

    DashboardCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_sym_verified),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        color = textPrimary(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape  = RoundedCornerShape(6.dp),
                        color  = inset,
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(5.dp).background(accent, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(
                                    if (ok) R.string.analysis_badge_ok else R.string.analysis_badge_wait
                                ),
                                color = accent,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = border.copy(alpha = 0.6f))
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.ic_sym_terminal),
                contentDescription = null,
                tint = muted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(
                    R.string.analysis_raw,
                    if (isLoadingDetail) "…" else asocRaw,
                    if (isLoadingDetail) "…" else info.bsohRaw.ifBlank { "—" }
                ),
                color = muted,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                footerEnd,
                color = when {
                    !ok && !isLoadingDetail && info.errorMessage.isNotBlank() -> gaugeRed()
                    else -> muted
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1
            )
        }
    }
}
