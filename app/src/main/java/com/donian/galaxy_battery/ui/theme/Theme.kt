package com.donian.galaxy_battery.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// ── Theme composable ──────────────────────────────────────────────────────────
/**
 * The dashboard is built on a fixed dark-first brand palette, so the wallpaper's
 * dynamic colors are deliberately not used: they would recolor the gauges and cards
 * away from the design. Light mode is the derived tonal counterpart.
 */
@Composable
fun GalaxyBatteryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = if (darkTheme) ClassicDarkScheme else ClassicLightScheme
    val appColors: AppColors     = if (darkTheme) ClassicDarkColors else ClassicLightColors

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalAppColors provides appColors) {
            // No screen paints its own background, so this is the page color app-wide.
            Box(Modifier.fillMaxSize().background(appColors.page)) { content() }
        }
    }
}

// ── Theme-aware color helpers (call inside Composition) ───────────────────────
@Composable fun appColors()             = LocalAppColors.current
@Composable fun cardColor()             = appColors().card
@Composable fun cardBorderColor()       = appColors().cardBorder
@Composable fun cornerButtonColor()     = appColors().cornerButton
@Composable fun textPrimary()           = MaterialTheme.colorScheme.onBackground
@Composable fun textSecondary()         = MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun textMuted()             = appColors().textMuted
@Composable fun accentGreenEffective()  = MaterialTheme.colorScheme.primary
@Composable fun warningColor()          = appColors().warning
@Composable fun refreshIndicatorColor() = appColors().refreshIndicator

@Composable fun gaugeGreen()  = appColors().gaugeGreen
@Composable fun gaugeYellow() = appColors().gaugeYellow
@Composable fun gaugeOrange() = appColors().gaugeOrange
@Composable fun gaugeRed()    = appColors().gaugeRed
@Composable fun gaugeBlue()   = appColors().gaugeBlue
@Composable fun gaugeGray()   = appColors().gaugeGray
