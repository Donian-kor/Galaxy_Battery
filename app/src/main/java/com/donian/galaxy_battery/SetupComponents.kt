package com.donian.galaxy_battery

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.donian.galaxy_battery.ui.theme.accentGreenEffective
import com.donian.galaxy_battery.ui.theme.cardBorderColor
import com.donian.galaxy_battery.ui.theme.cardColor
import com.donian.galaxy_battery.ui.theme.textPrimary
import com.donian.galaxy_battery.ui.theme.textSecondary

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
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        colors    = CardDefaults.elevatedCardColors(containerColor = cardColor()),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        content   = content
    )
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

/** 단계 타임라인의 한 칸. 완료된 단계는 번호 대신 체크 표시로 바뀐다. */
@Composable
fun SetupStepRow(number: Int, step: SetupStep, isLast: Boolean, isDone: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StepBadge(number = number, isDone = isDone)
            if (!isLast) {
                StepConnector()
            }
        }

        Spacer(Modifier.width(14.dp))

        StepContentBody(
            step = step,
            isLast = isLast,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StepBadge(number: Int, isDone: Boolean) {
    val green = accentGreenEffective()
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(if (isDone) green else green.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isDone) {
            MatchedIcon(
                res = R.drawable.ic_sym_check,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(
                text = "$number",
                color = green,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

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

@Composable
private fun StepContentBody(
    step: SetupStep,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val green = accentGreenEffective()
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

    Column(
        modifier = modifier.padding(top = 5.dp, bottom = if (isLast) 0.dp else 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MatchedIcon(step.icon, titleStyle, Modifier.size(18.dp), green)
            Spacer(Modifier.width(8.dp))
            Text(step.title, color = textPrimary(), style = titleStyle)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            step.desc,
            color = textSecondary(),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
        )
        if (step.actionLabel != null && step.onAction != null && step.onEditAction != null) {
            Spacer(Modifier.height(10.dp))
            DialCodeButton(step.actionLabel, step.onAction, step.onEditAction)
        }
        step.content?.let {
            Spacer(Modifier.height(12.dp))
            it()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialCodeButton(label: String, onDial: () -> Unit, onEdit: () -> Unit) {
    val green     = accentGreenEffective()
    val codeStyle = MaterialTheme.typography.labelLarge.copy(
        fontWeight    = FontWeight.Bold,
        textDirection = TextDirection.Ltr
    )
    val editLabel = stringResource(R.string.dial_edit_title)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape  = RoundedCornerShape(12.dp),
            color  = green.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, green.copy(alpha = 0.35f))
        ) {
            Row(
                Modifier
                    .combinedClickable(onClick = onDial, onLongClick = onEdit)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MatchedIcon(R.drawable.ic_sym_call, codeStyle, Modifier.size(16.dp), green)
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    color    = green,
                    style    = codeStyle,
                    softWrap = false
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onEdit) {
            MatchedIcon(
                R.drawable.ic_sym_edit,
                codeStyle,
                Modifier.size(18.dp).semantics { contentDescription = editLabel },
                textSecondary()
            )
        }
    }
}

/** 4단계 — 폴더 권한이 주어진 뒤: 현재 상태와 변경 방법을 보여준다. */
@Composable
fun FolderAccess(
    isScanning: Boolean,
    onOpenSettings: () -> Unit,
    onClearDataAndReload: () -> Unit
) {
    val green         = accentGreenEffective()
    val actionStyle   = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    val settingsLabel = stringResource(R.string.setup_open_app_settings)
    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    Column {
        Surface(
            onClick = onOpenSettings,
            enabled = !isScanning,
            shape   = RoundedCornerShape(12.dp),
            color   = green.copy(alpha = 0.12f),
            border  = BorderStroke(1.dp, green.copy(alpha = 0.35f))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isScanning) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = green, strokeWidth = 2.dp)
                } else {
                    MatchedIcon(R.drawable.ic_sym_check_circle, actionStyle, Modifier.size(16.dp), green)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (isScanning) R.string.setup_scanning else R.string.setup_permission_granted),
                    modifier = Modifier.weight(1f),
                    color    = green,
                    style    = actionStyle
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
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick        = onClearDataAndReload,
            enabled        = !isScanning,
            modifier       = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape          = RoundedCornerShape(12.dp),
            contentPadding = buttonPadding,
            border         = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (isScanning) 0.3f else 0.6f)),
            colors         = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.clear_data_confirm), style = actionStyle, textAlign = TextAlign.Center)
        }
    }
}
