package com.alyaqdhan.galaxy_battery.ui.main
import com.alyaqdhan.galaxy_battery.ui.theme.*

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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
    onNavigateRaw: () -> Unit
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

    val tp    = textPrimary()
    val muted = textMuted()

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
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(ScreenContentTop))

                // ── Header row ────────────────────────────────────────────────
                Box(Modifier.fillMaxWidth().alpha(headerAlpha)) {
                    FilledTonalIconButton(
                        onClick  = { if (!isLoadingDetail) viewModel.openLogSheet() },
                        enabled  = !isLoadingDetail,
                        modifier = Modifier.align(Alignment.CenterStart).size(40.dp),
                        colors   = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cornerButtonColor(),
                            contentColor   = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        // The arrow is mirrored in RTL, so turn it the other way to still point down.
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        Icon(
                            painterResource(R.drawable.ic_sym_arrow_back), stringResource(R.string.browse_logs),
                            modifier = Modifier.size(20.dp).rotate(if (isRtl) 90f else -90f)
                        )
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.app_name), color = tp,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(4.dp))
                        if (isLoadingDetail) {
                            Text("…", color = muted.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall)
                            Text("…", color = muted.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(2.dp))
                            Text("…", color = muted.copy(alpha = 0.3f),
                                style = MaterialTheme.typography.labelSmall)
                        } else {
                            if (effectiveInfo.logFileName.isNotEmpty()) {
                                val displayName = BatteryParser.truncateLogDisplayName(effectiveInfo.logFileName)
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(
                                        displayName, color = muted, textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (!folderAccessible) {
                                        Spacer(Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Text(
                                                stringResource(R.string.log_cached),
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                                                color    = muted.copy(alpha = 0.7f),
                                                style    = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 8.sp,
                                                    letterSpacing = 0.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            val logCreated = relativeTimeText(effectiveInfo.logTimestampMs)
                            if (logCreated.isNotEmpty())
                                Text(
                                    stringResource(R.string.log_created, logCreated),
                                    color = muted, textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            val installDate = batteryDateText(effectiveInfo.batteryDateMs)
                            if (installDate.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.installed_on, installDate),
                                    color = muted.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    FilledTonalIconButton(
                        onClick  = { if (!isLoadingDetail) onNavigateSetup() },
                        enabled  = !isLoadingDetail,
                        modifier = Modifier.align(Alignment.CenterEnd).size(40.dp),
                        colors   = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = cornerButtonColor(),
                            contentColor   = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(painterResource(R.drawable.ic_sym_info), stringResource(R.string.how_to_update_log),
                            modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(28.dp))

                val isNewBattery = !isLoadingDetail && !effectiveInfo.healthUnsupported &&
                        (effectiveInfo.healthPercent ?: 0) >= 100

                Box(Modifier.alpha(gaugeAlpha)) {
                    HealthGaugeCard(effectiveInfo, isLoadingDetail, arcAnimatable, gaugeAmplitude, gaugeReplayKey)
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.alpha(cardAlpha)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CycleCountCard(effectiveInfo, isLoadingDetail, isNewBattery, gaugeReplayKey)
                        Spacer(Modifier.height(12.dp))
                        LogSummaryCard(effectiveInfo, isLoadingDetail)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.version_label, BuildConfig.VERSION_NAME).localDigits(),
                            color = muted.copy(alpha = 0.45f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
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
                    "${allLogEntries.size}".localDigits(),
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

// ── Analysis log summary card ────────────────────────────────────────────────
@Composable
fun LogSummaryCard(
    info: BatteryInfo,
    isLoadingDetail: Boolean = false
) {
    val muted = textMuted()
    val border = cardBorderColor()
    val tp = textPrimary()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, border), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor()),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = gaugeGreen().copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_sym_check_circle), null,
                        tint = gaugeGreen(),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isLoadingDetail) "로그 분석 중…" else "덤프 상태 시스템 로그 정상 분석됨",
                    color = tp,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (isLoadingDetail) "레지스터 라인 파싱 진행 중"
                    else "배터리 드라이버 레지스터 라인 파싱 완료 · 삼성 HAL 무결성 검증 통과",
                    color = muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (info.readSuccess) gaugeGreen().copy(alpha = 0.12f)
                else gaugeGray().copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (info.readSuccess) gaugeGreen().copy(alpha = 0.4f)
                else gaugeGray().copy(alpha = 0.4f))
            ) {
                Text(
                    if (info.readSuccess) "CRC32 정상" else "대기",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (info.readSuccess) gaugeGreen() else gaugeGray(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
