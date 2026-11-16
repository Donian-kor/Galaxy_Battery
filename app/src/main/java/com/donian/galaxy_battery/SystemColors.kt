package com.donian.galaxy_battery

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import android.R.color as Sys

/**
 * Android 12(API 31) 이상에서 배경화면 기반 색 구성표를 만든다.
 *
 * 안드로이드 14부터 Material 는 시스템 색 역할(`system_primary_light` 등)로
 * 색 구성표를 만든다. 그런데 일부 UI 는 배경화면 팔레트는 채우면서 이 역할들은
 * 안드로이드 기본 파랑으로 남겨 둔다는 보고가 있다. 그 경우 삼성폰에서
 * 사용자가 고른 색이 앱에 반영되지 않는다.
 *
 * 그래서 역할의 primary 가 팔레트 색상(hue)과 어긋나면, 안드로이드 12·13 방식대로
 * 시스템 팔레트에서 직접 색 구성표를 만든다.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun systemColorScheme(context: Context, darkTheme: Boolean): ColorScheme {
    val buildFromPalettes = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // 안드로이드 13 이하는 원래 팔레트 방식이다.
        false
    } else {
        // 안드로이드 14+ 에서는 역할이 팔레트를 따라오는지 확인한다.
        !rolesFollowPalette(context, darkTheme)
    }
    if (!buildFromPalettes) {
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return paletteColorScheme(
        primary        = context.palette(
            Sys.system_accent1_0, Sys.system_accent1_10, Sys.system_accent1_50,
            Sys.system_accent1_100, Sys.system_accent1_200, Sys.system_accent1_300,
            Sys.system_accent1_400, Sys.system_accent1_500, Sys.system_accent1_600,
            Sys.system_accent1_700, Sys.system_accent1_800, Sys.system_accent1_900,
            Sys.system_accent1_1000
        ),
        secondary      = context.palette(
            Sys.system_accent2_0, Sys.system_accent2_10, Sys.system_accent2_50,
            Sys.system_accent2_100, Sys.system_accent2_200, Sys.system_accent2_300,
            Sys.system_accent2_400, Sys.system_accent2_500, Sys.system_accent2_600,
            Sys.system_accent2_700, Sys.system_accent2_800, Sys.system_accent2_900,
            Sys.system_accent2_1000
        ),
        tertiary       = context.palette(
            Sys.system_accent3_0, Sys.system_accent3_10, Sys.system_accent3_50,
            Sys.system_accent3_100, Sys.system_accent3_200, Sys.system_accent3_300,
            Sys.system_accent3_400, Sys.system_accent3_500, Sys.system_accent3_600,
            Sys.system_accent3_700, Sys.system_accent3_800, Sys.system_accent3_900,
            Sys.system_accent3_1000
        ),
        neutralVariant = context.palette(
            Sys.system_neutral2_0, Sys.system_neutral2_10, Sys.system_neutral2_50,
            Sys.system_neutral2_100, Sys.system_neutral2_200, Sys.system_neutral2_300,
            Sys.system_neutral2_400, Sys.system_neutral2_500, Sys.system_neutral2_600,
            Sys.system_neutral2_700, Sys.system_neutral2_800, Sys.system_neutral2_900,
            Sys.system_neutral2_1000
        ),
        darkTheme      = darkTheme,
    )
}

/**
 * 시스템 색 역할(primary)이 배경화면 팔레트와 같은 색 계열인지 확인한다.
 * 안드로이드 14+ 에서만 의미가 있다.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun rolesFollowPalette(context: Context, darkTheme: Boolean): Boolean {
    val role    = if (darkTheme) Sys.system_primary_dark else Sys.system_primary_light
    // Material 안드로이드 12 색 구성표가 primary 로 쓰는 팔레트 톤 (다크 80, 라이트 40)
    val palette = if (darkTheme) Sys.system_accent1_200 else Sys.system_accent1_600
    return sameHue(Color(context.getColor(role)), Color(context.getColor(palette)))
}

private fun Context.palette(vararg ids: Int) = TonalRange(ids.map { Color(getColor(it)) })

/** 이보다 Oklab 채도가 낮으면 사실상 회색이므로 hue 가 의미 없다. */
private const val GRAY_CHROMA = 0.02f

/**
 * 안드로이드 17 기본값에서 역할은 모든 팔레트 스타일에 대해 3° 이내로 유지된다.
 * 따라서 15° 는 여유를 두면서도 기본 파랑 옆의 보라 팔레트를 잡아낼 수 있다.
 */
private const val MAX_HUE_DIFFERENCE = 15f

/**
 * 두 색이 Oklab 기준 같은 색 계열인지 판정한다.
 * 회색끼리는 같고, 회색과 선명한 색은 다르다.
 */
internal fun sameHue(a: Color, b: Color): Boolean {
    val (chromaA, hueA) = a.chromaAndHue()
    val (chromaB, hueB) = b.chromaAndHue()

    // 둘 중 한쪽이 회색이면, 다른 쪽도 회색에 가까워야만 같은 것으로 본다.
    // (경계에 걸친 근사 회색 쌍이 우연히 갈리지 않도록 여유를 둔다)
    if (minOf(chromaA, chromaB) < GRAY_CHROMA) {
        return maxOf(chromaA, chromaB) < 2 * GRAY_CHROMA
    }

    // hue 는 원형(0°=360°)이므로 두 방향 중 작은 각도 차를 쓴다.
    val rawDifference = abs(hueA - hueB) % 360f
    val shortestArc  = minOf(rawDifference, 360f - rawDifference)
    return shortestArc <= MAX_HUE_DIFFERENCE
}

/** 색을 Oklab 으로 변환해 (채도, 색상각)을 구한다. */
private fun Color.chromaAndHue(): Pair<Float, Float> {
    val lab = convert(ColorSpaces.Oklab)
    val hue = Math.toDegrees(atan2(lab.blue, lab.green).toDouble()).toFloat()
    return hypot(lab.green, lab.blue) to hue
}

/** 안드로이드가 제공하는 톤 팔레트 (톤 100, 99, 95, 90, 80, 70 … 10, 0). */
internal class TonalRange(private val colors: List<Color>) {
    init {
        require(colors.size == TONES.size) { "Expected ${TONES.size} tones, got ${colors.size}" }
    }

    private val lab by lazy { colors.map { it.convert(ColorSpaces.CieLab) } }

    /**
     * [tone]에 해당하는 색을 반환한다.
     *
     * 안드로이드가 일부 톤(98, 12 등)을 생략하므로, 해당 톤은
     * 명도를 그대로 두고 가장 가까운 두 톤의 색을 선형 보간한다.
     */
    fun tone(tone: Int): Color {
        val exact = TONES.indexOf(tone)
        if (exact >= 0) return colors[exact]

        // tone 보다 한 단계 밝은 쪽(인덱스가 더 작은)과 어두운 쪽을 찾아 비율을 계산한다.
        val lower = TONES.indexOfLast { it > tone }
        val upper = lower + 1
        val fraction = (tone - TONES[upper]).toFloat() / (TONES[lower] - TONES[upper])

        val greenA = lab[upper].green
        val greenB = lab[lower].green
        val blueA  = lab[upper].blue
        val blueB  = lab[lower].blue

        val g = greenA + (greenB - greenA) * fraction
        val b = blueA + (blueB - blueA) * fraction
        return Color(tone.toFloat(), g, b, 1f, ColorSpaces.CieLab).convert(ColorSpaces.Srgb)
    }

    companion object {
        val TONES = listOf(100, 99, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)
    }
}

/** 안드로이드 12·13 방식의 동적 색 구성표를 시스템 팔레트로부터 만든다. */
internal fun paletteColorScheme(
    primary: TonalRange,
    secondary: TonalRange,
    tertiary: TonalRange,
    neutralVariant: TonalRange,
    darkTheme: Boolean,
): ColorScheme {
    val p = primary::tone
    val s = secondary::tone
    val t = tertiary::tone
    val n = neutralVariant::tone
    return if (darkTheme) darkColorScheme(
        primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
        inversePrimary = p(40),
        secondary = s(80), onSecondary = s(20), secondaryContainer = s(30), onSecondaryContainer = s(90),
        tertiary = t(80), onTertiary = t(20), tertiaryContainer = t(30), onTertiaryContainer = t(90),
        background = n(6), onBackground = n(90), surface = n(6), onSurface = n(90),
        surfaceVariant = n(30), onSurfaceVariant = n(80),
        inverseSurface = n(90), inverseOnSurface = n(20),
        outline = n(60), outlineVariant = n(30), scrim = n(0),
        surfaceBright = n(24), surfaceDim = n(6),
        surfaceContainer = n(12), surfaceContainerHigh = n(17), surfaceContainerHighest = n(22),
        surfaceContainerLow = n(10), surfaceContainerLowest = n(4),
        surfaceTint = p(80),
        primaryFixed = p(90), primaryFixedDim = p(80), onPrimaryFixed = p(10), onPrimaryFixedVariant = p(30),
        secondaryFixed = s(90), secondaryFixedDim = s(80), onSecondaryFixed = s(10), onSecondaryFixedVariant = s(30),
        tertiaryFixed = t(90), tertiaryFixedDim = t(80), onTertiaryFixed = t(10), onTertiaryFixedVariant = t(30),
    ) else lightColorScheme(
        primary = p(40), onPrimary = p(100), primaryContainer = p(90), onPrimaryContainer = p(10),
        inversePrimary = p(80),
        secondary = s(40), onSecondary = s(100), secondaryContainer = s(90), onSecondaryContainer = s(10),
        tertiary = t(40), onTertiary = t(100), tertiaryContainer = t(90), onTertiaryContainer = t(10),
        background = n(98), onBackground = n(10), surface = n(98), onSurface = n(10),
        surfaceVariant = n(90), onSurfaceVariant = n(30),
        inverseSurface = n(20), inverseOnSurface = n(95),
        outline = n(50), outlineVariant = n(80), scrim = n(0),
        surfaceBright = n(98), surfaceDim = n(87),
        surfaceContainer = n(94), surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
        surfaceContainerLow = n(96), surfaceContainerLowest = n(100),
        surfaceTint = p(40),
        primaryFixed = p(90), primaryFixedDim = p(80), onPrimaryFixed = p(10), onPrimaryFixedVariant = p(30),
        secondaryFixed = s(90), secondaryFixedDim = s(80), onSecondaryFixed = s(10), onSecondaryFixedVariant = s(30),
        tertiaryFixed = t(90), tertiaryFixedDim = t(80), onTertiaryFixed = t(10), onTertiaryFixedVariant = t(30),
    )
}
