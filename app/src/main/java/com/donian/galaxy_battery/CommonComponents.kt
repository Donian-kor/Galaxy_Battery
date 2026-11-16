package com.donian.galaxy_battery

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.donian.galaxy_battery.ui.theme.refreshIndicatorColor

/**
 * 화면마다 제목이 같은 높이에서 시작하도록 창 위쪽에 두는 고정 여백.
 *
 * 상태 표시줄 높이는 기기마다 달라서 그 값을 쓰면 화면을 옮길 때마다
 * 제목이 위아래로 흔들린다. 그래서 기기와 무관한 값을 쓴다.
 */
internal val ScreenContentTop: Dp = 52.dp

/**
 * 스크롤 손잡이가 트랙의 어디쯤 있는지를 0~1 로 환산한다.
 *
 * 콘텐츠 배치가 끝나기 전에는 [maxValue]가 0이라 나눗셈이 성립하지 않는다.
 * 그 순간에는 손잡이를 트랙 맨 위에 두는 것으로 본다.
 */
internal fun scrollbarFraction(value: Int, maxValue: Int): Float {
    if (maxValue <= 0) return 0f
    return (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
}

/**
 * 당겨서 새로고침 제스처만 받아내는 콘텐츠 래퍼.
 *
 * 인디케이터는 화면에 드러내지 않는다. 당김 자체가 [onRescan] 을 부르는
 * 신호일 뿐이고, 진행 표시는 화면 안의 다른 요소가 맡는다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescanPullToRefreshBox(
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    val indicatorColor = refreshIndicatorColor()

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRescan,
        state = state,
        modifier = modifier,
        indicator = { InvisibleIndicator(state, indicatorColor) }
    ) {
        content()
    }
}

/** 인디케이터 자리는 차지하되 그림은 그리지 않는다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.InvisibleIndicator(state: PullToRefreshState, color: Color) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = false,
        modifier = Modifier.align(Alignment.TopCenter),
        color = color
    )
}
