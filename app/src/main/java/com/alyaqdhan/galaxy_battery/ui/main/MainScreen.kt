package com.alyaqdhan.galaxy_battery.ui.main
import com.alyaqdhan.galaxy_battery.ui.theme.*

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.alyaqdhan.galaxy_battery.*
import com.alyaqdhan.galaxy_battery.AppViewModel
import com.alyaqdhan.galaxy_battery.R

// ── BatteryDashboard composable ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryDashboard(
    viewModel: AppViewModel,
    onNavigateSetup: () -> Unit,
    onNavigateRaw: () -> Unit,
    onExportReport: () -> Unit
) {
    val info             = viewModel.batteryInfo
    val isLoadingDetail  = viewModel.isLoadingDetail || info == null
    val isRefreshing     = viewModel.isRefreshing
    val folderAccessible = viewModel.alreadyHasPerm && viewModel.folderAccessible
    val showLogSheet     = viewModel.showLogSheet
    val allLogEntries    = viewModel.allLogEntries
    val gaugeAmplitude   = viewModel.gaugeAmplitude
    val gaugeReplayKey   = viewModel.gaugeReplayKey

    val arcAnimatable = remember { Animatable(0f) }

    LaunchedEffect(isLoadingDetail) {
        if (isLoadingDetail) viewModel.startGaugePulse()
        else viewModel.stopGaugePulse()
    }

    val alreadyHasData = remember { info?.readSuccess == true }
    var shown by remember { mutableStateOf(alreadyHasData) }
    LaunchedEffect(Unit) { shown = true }

    val headerAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(400), label = "ha")
    val gaugeAlpha  by animateFloatAsState(
        if (shown) 1f else 0f, tween(450, delayMillis = 140), label = "ga")
    val cardAlpha   by animateFloatAsState(
        if (shown) 1f else 0f, tween(400, delayMillis = 280), label = "ca")

    val effectiveInfo = info ?: BatteryInfo(
        healthPercent = null, cycleCount = null, healthUnsupported = false, readSuccess = false
    )

    ErrorDialogHost(
        errorDialog = viewModel.errorDialog,
        onDismiss   = { viewModel.dismissErrorDialog() }
    )

    Box(Modifier.fillMaxSize()) {
        RescanPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRescan     = { viewModel.rescan() },
            modifier     = Modifier.fillMaxSize()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(ScreenContentTop))

                // ── Top app bar ───────────────────────────────────────────────
                Box(Modifier.alpha(headerAlpha)) {
                    DashboardHeader(
                        logFileName = effectiveInfo.logFileName,
                        logMoment   = logMomentText(effectiveInfo.logTimestampMs),
                        isLoading   = isLoadingDetail,
                        isCached    = !folderAccessible,
                        onOpenLogs  = { if (!isLoadingDetail) viewModel.openLogSheet() },
                        onOpenSetup = { if (!isLoadingDetail) onNavigateSetup() }
                    )
                }

                Spacer(Modifier.height(16.dp))

                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Box(Modifier.alpha(gaugeAlpha)) {
                            HealthGaugeCard(
                                info           = effectiveInfo,
                                isLoadingDetail = isLoadingDetail,
                                arcAnimatable  = arcAnimatable,
                                amplitude      = gaugeAmplitude,
                                gaugeReplayKey = gaugeReplayKey
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.alpha(cardAlpha)) {
                            Column(Modifier.fillMaxWidth()) {
                                TelemetryCard(
                                    info = effectiveInfo,
                                    isLoadingDetail = isLoadingDetail,
                                    gaugeReplayKey = gaugeReplayKey
                                )
                                Spacer(Modifier.height(12.dp))
                                LogSummaryCard(effectiveInfo, isLoadingDetail)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.alpha(cardAlpha)) {
                            ExportReportButton(
                                enabled = !isLoadingDetail,
                                onClick = onExportReport
                            )
                        }
                    }

                Spacer(Modifier.height(24.dp))
                DashboardFooter(versionName = BuildConfig.VERSION_NAME)
            }
        }

        // Interaction-blocking overlay while loading detail
        if (isLoadingDetail) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick           = {},
                        indication        = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    )
            )
        }

        // Log sheet scrim
        if (showLogSheet && !isLoadingDetail) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        onClick           = { viewModel.dismissLogSheet() },
                        indication        = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    )
            )
        }

        // ── Log sheet drop-down ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = showLogSheet && !isLoadingDetail,
            enter   = slideInVertically(tween(400, easing = TrayEnterEasing)) { -it } +
                    fadeIn(tween(200)),
            exit    = slideOutVertically(tween(220, easing = TrayExitEasing)) { -it } +
                    fadeOut(tween(220, easing = TrayExitEasing))
        ) {
            LogSheet(
                allLogEntries   = allLogEntries,
                activeFileName  = effectiveInfo.logFileName,
                onDismiss       = { viewModel.dismissLogSheet() },
                onSelectEntry   = { viewModel.selectLogEntry(it) },
                onReadRaw       = { entry ->
                    viewModel.openRawLog(entry)
                    onNavigateRaw()
                },
                extractTs       = { BatteryParser.extractTimestampFromFileName(it) }
            )
        }
    }
}

// ── Dashboard chrome ──────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(
    logFileName: String,
    logMoment: String,
    isLoading: Boolean,
    isCached: Boolean,
    onOpenLogs: () -> Unit,
    onOpenSetup: () -> Unit
) {
    val muted  = textMuted()
    val border = cardBorderColor()

    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderIconButton(
            icon        = R.drawable.ic_sym_description,
            description = stringResource(R.string.browse_logs),
            enabled     = !isLoading,
            onClick     = onOpenLogs
        )

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.app_name),
                color = textPrimary(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            val cacheSuffix =
                if (isCached) " · ${stringResource(R.string.log_cached)}" else ""
            val pillText = when {
                isLoading -> stringResource(R.string.log_pill_loading)
                logFileName.isNotEmpty() && logMoment.isNotEmpty() ->
                    "$logFileName · $logMoment$cacheSuffix"
                logFileName.isNotEmpty() -> "$logFileName$cacheSuffix"
                logMoment.isNotEmpty() -> "$logMoment$cacheSuffix"
                else -> stringResource(R.string.log_pill_loading)
            }
            Surface(
                shape  = CircleShape,
                color  = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, border.copy(alpha = 0.4f))
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(
                                if (isLoading) muted else MaterialTheme.colorScheme.primary,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        pillText,
                        color = muted,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HeaderIconButton(
            icon        = R.drawable.ic_sym_tune,
            description = stringResource(R.string.how_to_update_log),
            enabled     = !isLoading,
            onClick     = onOpenSetup
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val border = cardBorderColor()
    IconButton(
        onClick  = onClick,
        enabled  = enabled,
        modifier = Modifier
            .size(40.dp)
            .background(cornerButtonColor(), CircleShape)
            .border(1.dp, border.copy(alpha = 0.4f), CircleShape)
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = if (enabled) textMuted() else textMuted().copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}



@Composable
private fun ExportReportButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick        = onClick,
        enabled        = enabled,
        modifier       = Modifier.fillMaxWidth().height(48.dp),
        shape          = CircleShape,
        colors         = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_sym_file_download),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.export_report),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DashboardFooter(versionName: String) {
    val muted  = textMuted()
    val border = cardBorderColor()

    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = border.copy(alpha = 0.3f))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.footer_title, versionName),
                color = textSecondary(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.footer_subtitle),
                color = muted,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.footer_privacy),
                    color = muted.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                )
                Spacer(Modifier.width(8.dp))
                Text("•", color = muted.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.footer_read_only),
                    color = muted.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                )
            }
        }
    }
}

// ── Log sheet ─────────────────────────────────────────────────────────────────

// Material's emphasized curves: the tray arrives fast and settles, then leaves without lingering.
val TrayEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val TrayExitEasing  = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

@Composable
fun LogSheet(
    allLogEntries: List<DocEntry>,
    activeFileName: String,
    onDismiss: () -> Unit,
    onSelectEntry: (DocEntry) -> Unit,
    onReadRaw: (DocEntry) -> Unit,
    extractTs: (String) -> Long
) {
    Surface(
        modifier        = Modifier
            .fillMaxWidth()
            .clickable(onClick = {}, indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
        color           = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        shape           = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = ScreenContentTop)
                    .height(40.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.log_files_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${allLogEntries.size}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color    = textMuted().copy(alpha = 0.5f)
            )

            val logsScrollState = rememberScrollState()
            val logsScope       = rememberCoroutineScope()

            Box(Modifier.fillMaxWidth().heightIn(max = 200.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(logsScrollState)
                        .padding(end = 12.dp, top = 4.dp, bottom = 20.dp)
                ) {
                    if (allLogEntries.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_log_files),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        allLogEntries.forEachIndexed { idx, entry ->
                            LogEntry(
                                entry         = entry,
                                isActive      = entry.name == activeFileName,
                                relTime       = relativeTimeText(
                                    extractTs(entry.name).takeIf { it != 0L } ?: entry.lastModified
                                ),
                                onSelectEntry = onSelectEntry,
                                onReadRaw     = onReadRaw
                            )
                            if (idx < allLogEntries.lastIndex) {
                                HorizontalDivider(
                                    color    = textMuted().copy(alpha = 0.3f),
                                    modifier = Modifier.padding(start = 38.dp, end = 16.dp)
                                )
                            }
                        }
                    }
                }

                if (logsScrollState.maxValue > 0) {
                    val trackHeight    = 200.dp
                    val thumbMinHeight = 32.dp
                    val density        = LocalDensity.current
                    val trackPx        = with(density) { trackHeight.toPx() }
                    val thumbMinPx     = with(density) { thumbMinHeight.toPx() }
                    val thumbPx        = (trackPx * 0.35f).coerceAtLeast(thumbMinPx)
                    val thumbColor     = appColors().scrollbarThumb
                    val trackColor     = appColors().scrollbarTrack

                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .width(4.dp)
                            .height(trackHeight)
                            .background(trackColor, RoundedCornerShape(2.dp))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val draggableRange = trackPx - thumbPx
                                    if (draggableRange > 0f) {
                                        val delta = (dragAmount.y / draggableRange) * logsScrollState.maxValue.toFloat()
                                        logsScope.launch { logsScrollState.scrollBy(delta) }
                                    }
                                }
                            }
                    ) {
                        Box(
                            Modifier
                                .offset {
                                    val fraction = scrollbarFraction(logsScrollState.value, logsScrollState.maxValue)
                                    IntOffset(0, (fraction * (trackPx - thumbPx)).roundToInt())
                                }
                                .width(4.dp)
                                .height(with(density) { thumbPx.toDp() })
                                .background(thumbColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntry(
    entry: DocEntry,
    isActive: Boolean,
    relTime: String,
    onSelectEntry: (DocEntry) -> Unit,
    onReadRaw: (DocEntry) -> Unit
) {
    val green       = accentGreenEffective()
    val displayName = BatteryParser.truncateLogDisplayName(entry.name)

    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(if (isActive) green else Color.Transparent, CircleShape))
        Spacer(Modifier.width(12.dp))

        Surface(
            onClick  = { if (!isActive) onSelectEntry(entry) },
            enabled  = !isActive,
            modifier = Modifier.weight(1f),
            color    = Color.Transparent,
            shape    = RoundedCornerShape(8.dp)
        ) {
            Row(
                Modifier.padding(vertical = 13.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    displayName,
                    color    = if (isActive) green else MaterialTheme.colorScheme.onSurface,
                    style    = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (relTime.isNotEmpty()) "($relTime)" else "",
                    color    = if (isActive) green.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    style    = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Surface(
            onClick  = { onReadRaw(entry) },
            modifier = Modifier.size(40.dp),
            shape    = RoundedCornerShape(10.dp),
            color    = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_sym_arrow_forward_ios),
                    contentDescription = stringResource(R.string.view_raw),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
