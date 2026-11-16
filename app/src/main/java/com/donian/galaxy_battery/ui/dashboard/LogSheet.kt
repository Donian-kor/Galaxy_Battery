package com.donian.galaxy_battery.ui.dashboard

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.donian.galaxy_battery.R
import com.donian.galaxy_battery.data.parser.BatteryParser
import com.donian.galaxy_battery.model.*
import com.donian.galaxy_battery.ui.common.ScreenContentTop
import com.donian.galaxy_battery.ui.common.relativeTimeText
import com.donian.galaxy_battery.ui.common.scrollbarFraction
import com.donian.galaxy_battery.ui.theme.accentGreenEffective
import com.donian.galaxy_battery.ui.theme.appColors
import com.donian.galaxy_battery.ui.theme.textMuted

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
