package com.donian.galaxy_battery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

// Material 3's default scale is used; the dashboard only swaps in a monospace family for
// the telemetry readouts (the design sets those in JetBrains Mono).
val AppTypography = Typography()

/** Reads as a measurement: fixed-width digits, so a changing value doesn't shift its row. */
val TelemetryFontFamily: FontFamily = FontFamily.Monospace
