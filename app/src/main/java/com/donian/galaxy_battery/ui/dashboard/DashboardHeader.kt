package com.donian.galaxy_battery.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.donian.galaxy_battery.ui.theme.cardBorderColor
import com.donian.galaxy_battery.ui.theme.cornerButtonColor
import com.donian.galaxy_battery.ui.theme.textMuted
import com.donian.galaxy_battery.ui.theme.textPrimary

// ── Dashboard chrome ──────────────────────────────────────────────────────────

@Composable
internal fun DashboardHeader(
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
