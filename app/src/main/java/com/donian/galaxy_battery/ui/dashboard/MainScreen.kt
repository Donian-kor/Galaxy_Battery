package com.donian.galaxy_battery.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.donian.galaxy_battery.AppViewModel
import com.donian.galaxy_battery.BuildConfig
import com.donian.galaxy_battery.R
import com.donian.galaxy_battery.data.parser.BatteryParser
import com.donian.galaxy_battery.model.*
import com.donian.galaxy_battery.ui.common.ErrorDialogHost
import com.donian.galaxy_battery.ui.common.RescanPullToRefreshBox
import com.donian.galaxy_battery.ui.common.ScreenContentTop
import com.donian.galaxy_battery.ui.common.logMomentText
import com.donian.galaxy_battery.ui.theme.cardBorderColor
import com.donian.galaxy_battery.ui.theme.textMuted
import com.donian.galaxy_battery.ui.theme.textPrimary
import com.donian.galaxy_battery.ui.theme.textSecondary

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
            onRescan = { viewModel.rescan() },
            modifier = Modifier.fillMaxSize()
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
