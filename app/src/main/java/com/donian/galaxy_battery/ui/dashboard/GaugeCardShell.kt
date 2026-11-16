package com.donian.galaxy_battery.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.donian.galaxy_battery.R
import com.donian.galaxy_battery.model.*
import com.donian.galaxy_battery.ui.theme.cardBorderColor
import com.donian.galaxy_battery.ui.theme.cardColor

/**
 * 판정 등급에 대응하는 문자열 리소스.
 *
 * 대시보드 카드들이 같은 등급 표기를 쓰도록 한 곳에 둔다.
 */
internal fun BatteryHealthAssessment.gradeLabelResource(): Int = when (grade) {
    BatteryHealthGrade.GOOD -> R.string.assessment_grade_good
    BatteryHealthGrade.NORMAL -> R.string.assessment_grade_normal
    BatteryHealthGrade.WEAK -> R.string.assessment_grade_weak
    BatteryHealthGrade.BAD -> when (badReason) {
        BatteryHealthBadReason.END_OF_LIFE -> R.string.assessment_grade_bad_end_of_life
        BatteryHealthBadReason.EARLY_DEGRADATION -> R.string.assessment_grade_bad_early_degradation
        null -> R.string.assessment_grade_bad
    }
    BatteryHealthGrade.DIAGNOSTIC_UNAVAILABLE -> R.string.assessment_grade_diagnostic_unavailable
    BatteryHealthGrade.CALCULATION_UNAVAILABLE -> R.string.assessment_grade_calculation_unavailable
    BatteryHealthGrade.UNDETERMINED -> R.string.assessment_grade_undetermined
}

// ── 공용 카드 껍데기 ─────────────────────────────────────────────────────────

/** 대시보드의 카드: 고정 테두리와 안쪽의 페이지 색 블록. */
@Composable
internal fun DashboardCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    // 히어로 카드의 부드러운 코너 강조(글로우).
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor())
            .border(1.dp, cardBorderColor(), RoundedCornerShape(20.dp))
    ) {
        if (glow) {
            Box(
                Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 52.dp, y = (-52).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(padding),
            content = content
        )
    }
}
