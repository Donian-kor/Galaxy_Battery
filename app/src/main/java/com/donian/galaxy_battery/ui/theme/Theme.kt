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

// ── 테마 컴포저블 ──────────────────────────────────────────────────────
/**
 * 대시보드는 고정된 다크 우선 브랜드 팔레트로 만들어지므로,
 * 배경화면의 동적 색은 일부러 쓰지 않는다. 쓰면 게이지와 카드가
 * 디자인에서 벗어난 색으로 다시 칠해지기 때문이다. 라이트 모드는 그 명도 대응이다.
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
            // 각 화면이 배경을 직접 칠하지 않으므로, 이것이 앱 전역의 페이지 색이다.
            Box(Modifier.fillMaxSize().background(appColors.page)) { content() }
        }
    }
}

// ── 테마 인지 색 헬퍼 (Composition 안에서 호출) ────────────────────────────
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
