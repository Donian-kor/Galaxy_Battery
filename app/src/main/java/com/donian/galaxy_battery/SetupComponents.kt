package com.donian.galaxy_battery
import com.donian.galaxy_battery.ui.theme.*

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.mipmap.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier.size(72.dp)
    )
}

/** The card every Setup section sits in, matching the Dashboard's cards. */
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

/** One stop on the steps timeline. A finished step swaps its number for a tick. */
@Composable
fun SetupStepRow(number: Int, step: SetupStep, isLast: Boolean, isDone: Boolean = false) {
    val green      = accentGreenEffective()
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(if (isDone) green else green.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    MatchedIcon(
                        R.drawable.ic_sym_check,
                        MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        Modifier.size(18.dp),
                        MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "$number",
                        color = green,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .padding(vertical = 5.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(cardBorderColor(), RoundedCornerShape(1.dp))
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f).padding(top = 5.dp, bottom = if (isLast) 0.dp else 22.dp)) {
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

/** Step 4 once the folder is granted: how things stand, and how to change them. */
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

