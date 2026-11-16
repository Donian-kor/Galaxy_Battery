package com.donian.galaxy_battery.ui.setup

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.donian.galaxy_battery.R
import com.donian.galaxy_battery.ui.common.MatchedIcon
import com.donian.galaxy_battery.ui.theme.accentGreenEffective
import com.donian.galaxy_battery.ui.theme.cardBorderColor
import com.donian.galaxy_battery.ui.theme.cardColor
import com.donian.galaxy_battery.ui.theme.textPrimary
import com.donian.galaxy_battery.ui.theme.textSecondary

/** 강조 패널의 배경·테두리 투명도. 배너와 다이얼 버튼이 같은 값을 쓴다. */
private const val TINT_FILL_ALPHA = 0.12f
private const val TINT_BORDER_ALPHA = 0.35f

/**
 * Setup 머리글에 붙는 앱 마크.
 *
 * 방향(좌우 반전)이 없는 가로형 이미지라 RTL 에서도 그대로 둔다.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.mipmap.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier.size(72.dp)
    )
}

/** Setup 의 각 섹션이 놓이는 카드. 대시보드의 카드와 톤을 맞춘다. */
@Composable
fun SetupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = cardColor()),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier  = modifier.fillMaxWidth()
    ) {
        content()
    }
}

data class SetupStep(
    @param:DrawableRes val icon: Int,
    val title: String,
    val desc: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onEditAction: (() -> Unit)? = null,
    val content: (@Composable () -> Unit)? = null
)

/**
 * 다이얼 코드 동작 하나.
 *
 * [SetupStep] 은 라벨과 두 콜백을 따로 들고 있어서 "셋 다 있거나 셋 다 없거나"라는 규칙이
 * 호출부마다 흔어진다. 그 셋을 한 값으로 묶어 버튼이 반쪽 상태로 그려질 수 없게 한다.
 * [SetupStep] 의 필드 구성은 그대로 두어 공개 시그니처는 바꾸지 않는다.
 */
private data class DialAction(
    val label: String,
    val onDial: () -> Unit,
    val onEdit: () -> Unit
)

/** 라벨과 두 콜백이 모두 채워졌을 때만 다이얼 동작이 성립한다. */
private fun SetupStep.dialAction(): DialAction? {
    val label  = actionLabel  ?: return null
    val onDial = onAction     ?: return null
    val onEdit = onEditAction ?: return null
    return DialAction(label, onDial, onEdit)
}

/** 단계 타임라인의 한 칸. 완료된 단계는 번호 대신 체크 표시로 바뀐다. */
@Composable
fun SetupStepRow(number: Int, step: SetupStep, isLast: Boolean, isDone: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        StepMarker(number = number, isDone = isDone, connectsToNext = !isLast)

        Spacer(Modifier.width(14.dp))

        StepBody(
            step = step,
            modifier = Modifier
                .weight(1f)
                .padding(top = 5.dp, bottom = if (isLast) 0.dp else 22.dp)
        )
    }
}

/** 왼쪽 레일. 위쪽 배지와 그 아래 세로줄을 한 컴포저블에 모았다. */
@Composable
private fun StepMarker(number: Int, isDone: Boolean, connectsToNext: Boolean) {
    Column(
        modifier = Modifier.width(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepBadge(number = number, done = isDone)
        if (connectsToNext) StepConnector()
    }
}

/** 강조 라벨 — 배지·배너·버튼이 같은 굵기를 쓴다. */
@Composable
private fun boldLabel() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)

/** 완료 표시일 때는 숫자 대신 체크 아이콘이 들어간다. */
@Composable
private fun StepBadge(number: Int, done: Boolean) {
    val green   = accentGreenEffective()
    val label   = boldLabel()
    val fill    = if (done) green else green.copy(alpha = TINT_FILL_ALPHA)

    Box(
        modifier = Modifier
            .size(32.dp)
            .background(fill, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            MatchedIcon(
                R.drawable.ic_sym_check,
                label,
                Modifier.size(18.dp),
                MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("$number", color = green, style = label)
        }
    }
}

/** 다음 칸으로 이어지는 세로줄. 행 높이의 남는 공간을 채운다. */
@Composable
private fun ColumnScope.StepConnector() {
    Box(
        modifier = Modifier
            .padding(vertical = 5.dp)
            .width(2.dp)
            .weight(1f)
            .background(cardBorderColor(), RoundedCornerShape(1.dp))
    )
}

/** 오른쪽 본문: 제목 줄 → 설명 → (있으면) 다이얼 버튼 → (있으면) 추가 컨텐츠. */
@Composable
private fun StepBody(step: SetupStep, modifier: Modifier = Modifier) {
    val green   = accentGreenEffective()
    val heading = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MatchedIcon(step.icon, heading, Modifier.size(18.dp), green)
            Spacer(Modifier.width(8.dp))
            Text(step.title, color = textPrimary(), style = heading)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            step.desc,
            color = textSecondary(),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
        )

        // 다이얼 버튼은 라벨과 두 콜백이 한 값으로 모였을 때만 그린다.
        step.dialAction()?.let { action ->
            Spacer(Modifier.height(10.dp))
            DialCodeButton(action)
        }

        step.content?.let {
            Spacer(Modifier.height(12.dp))
            it()
        }
    }
}

@Composable
private fun DialCodeButton(action: DialAction) {
    val green     = accentGreenEffective()
    val codeStyle = boldLabel().copy(textDirection = TextDirection.Ltr)
    val editLabel = stringResource(R.string.dial_edit_title)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape  = RoundedCornerShape(12.dp),
            color  = green.copy(alpha = TINT_FILL_ALPHA),
            border = BorderStroke(1.dp, green.copy(alpha = TINT_BORDER_ALPHA))
        ) {
            Row(
                Modifier
                    .combinedClickable(onClick = action.onDial, onLongClick = action.onEdit)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MatchedIcon(R.drawable.ic_sym_call, codeStyle, Modifier.size(16.dp), green)
                Spacer(Modifier.width(8.dp))
                Text(
                    action.label,
                    color    = green,
                    style    = codeStyle,
                    softWrap = false
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = action.onEdit) {
            MatchedIcon(
                R.drawable.ic_sym_edit,
                codeStyle,
                Modifier.size(18.dp).semantics { contentDescription = editLabel },
                textSecondary()
            )
        }
    }
}

/**
 * 화면 테마 전환 스위치.
 *
 * 고른 적이 없으면 기기 설정을 따르고, 한 번 고르면 그 선택이 저장되어 다음 실행에도 유지된다.
 */
@Composable
fun ThemeToggle(isDark: Boolean, onToggle: (Boolean) -> Unit) {
    val green   = accentGreenEffective()
    val heading = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MatchedIcon(R.drawable.ic_sym_tune, heading, Modifier.size(18.dp), green)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.theme_dark_title),
                color = textPrimary(),
                style = heading
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.theme_dark_desc),
                color = textSecondary(),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = isDark, onCheckedChange = onToggle)
    }
}

/**
 * 4단계 — 폴더 권한을 받은 뒤 보이는 상태 블록.
 *
 * 위쪽 상태 배너와 아래쪽 초기화 버튼은 [isScanning] 값 하나로 함께 잠긴다.
 * 스캔 중에는 배너 왼쪽이 회전 인디케이터로 바뀐다.
 */
@Composable
fun FolderAccess(
    isScanning: Boolean,
    onOpenSettings: () -> Unit,
    onClearDataAndReload: () -> Unit
) {
    val green = accentGreenEffective()

    Column {
        PermissionBanner(
            isScanning = isScanning,
            green = green,
            onOpenSettings = onOpenSettings
        )
        Spacer(Modifier.height(10.dp))
        ClearDataButton(isScanning = isScanning, onClearData = onClearDataAndReload)
    }
}

/** 권한 상태 배너. 스캔 중에는 회전 인디케이터가 상태 아이콘을 대신한다. */
@Composable
private fun PermissionBanner(isScanning: Boolean, green: Color, onOpenSettings: () -> Unit) {
    val actionStyle   = boldLabel()
    val settingsLabel = stringResource(R.string.setup_open_app_settings)
    val stateLabel    = stringResource(
        if (isScanning) R.string.setup_scanning else R.string.setup_permission_granted
    )

    Surface(
        onClick  = onOpenSettings,
        enabled  = !isScanning,
        shape    = RoundedCornerShape(12.dp),
        color    = green.copy(alpha = TINT_FILL_ALPHA),
        border   = BorderStroke(1.dp, green.copy(alpha = TINT_BORDER_ALPHA))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BannerStateIcon(isScanning = isScanning, green = green)
            Spacer(Modifier.width(8.dp))
            Text(
                stateLabel,
                modifier = Modifier.weight(1f),
                color = green,
                style = actionStyle
            )
            if (!isScanning) {
                MatchedIcon(
                    R.drawable.ic_sym_chevron_right,
                    actionStyle,
                    Modifier.size(18.dp).semantics { contentDescription = settingsLabel },
                    green
                )
            }
        }
    }
}

/** 배너 왼쪽 아이콘 자리. 스캔 중이 아니면 체크 표시가 들어간다. */
@Composable
private fun BannerStateIcon(isScanning: Boolean, green: Color) {
    if (isScanning) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = green,
            strokeWidth = 2.dp
        )
    } else {
        MatchedIcon(
            R.drawable.ic_sym_check_circle,
            boldLabel(),
            Modifier.size(16.dp),
            green
        )
    }
}

/** 캐시를 지우고 다시 읽는 버튼. 스캔 중에는 비활성이다. */
@Composable
private fun ClearDataButton(isScanning: Boolean, onClearData: () -> Unit) {
    val danger = MaterialTheme.colorScheme.error

    OutlinedButton(
        onClick = onClearData,
        enabled = !isScanning,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        border = BorderStroke(1.dp, danger.copy(alpha = if (isScanning) 0.3f else 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = danger)
    ) {
        Text(
            stringResource(R.string.clear_data_confirm),
            style = boldLabel(),
            textAlign = TextAlign.Center
        )
    }
}
