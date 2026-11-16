package com.donian.galaxy_battery.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.donian.galaxy_battery.R
import com.donian.galaxy_battery.model.*
import com.donian.galaxy_battery.ui.theme.appColors
import com.donian.galaxy_battery.ui.theme.cardBorderColor
import com.donian.galaxy_battery.ui.theme.gaugeBlue
import com.donian.galaxy_battery.ui.theme.gaugeGray
import com.donian.galaxy_battery.ui.theme.gaugeGreen
import com.donian.galaxy_battery.ui.theme.gaugeRed
import com.donian.galaxy_battery.ui.theme.textMuted
import com.donian.galaxy_battery.ui.theme.textPrimary

// ── 분석 요약 카드 ───────────────────────────────────────────────────────────

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
    val asocRaw = info.displayAsocRaw

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
