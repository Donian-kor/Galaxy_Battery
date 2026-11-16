package com.donian.galaxy_battery

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.donian.galaxy_battery.ui.theme.accentGreenEffective
import com.donian.galaxy_battery.ui.theme.textPrimary
import com.donian.galaxy_battery.ui.theme.textSecondary
import com.donian.galaxy_battery.ui.theme.warningColor

/**
 * 로그를 못 찾았음을 알리는 값.
 *
 * 폴더가 틀린 것과 로그가 없는 것은 사용자가 할 일이 다르므로,
 * 오류색과 경고색을 갈라서 보여준다.
 */
private const val NO_LOG_FOUND = "NO_LOG_FOUND"

/**
 * 다이얼로그 한 종류가 쓰는 아이콘·색·문구 묶음.
 *
 * 껍데기(배경·여백·버튼)는 모든 종류가 같고, 이 묶음만 갈아 끼운다.
 */
private data class DialogLook(
    @DrawableRes val icon: Int,
    val tint: Color,
    @StringRes val title: Int,
    @StringRes val body: Int
)

/** 현재 오류 상태를 종류에 맞는 다이얼로그 하나로 그린다. */
@Composable
fun ErrorDialogHost(errorDialog: ErrorDialog?, onDismiss: () -> Unit) {
    val look = when (errorDialog) {
        is ErrorDialog.WrongFolder -> wrongFolderLook(errorDialog.errorMessage)
        ErrorDialog.FolderDeleted -> folderDeletedLook()
        ErrorDialog.PermissionLost -> permissionLostLook()
        null -> return
    }
    ErrorDialogScaffold(look, onDismiss)
}

@Composable
private fun wrongFolderLook(errorMessage: String) = DialogLook(
    icon = R.drawable.ic_sym_warning,
    tint = if (errorMessage == NO_LOG_FOUND) warningColor()
           else MaterialTheme.colorScheme.error,
    title = R.string.error_wrong_folder_title,
    body = R.string.error_wrong_folder_body
)

@Composable
private fun folderDeletedLook() = DialogLook(
    icon = R.drawable.ic_sym_delete,
    tint = MaterialTheme.colorScheme.error,
    title = R.string.error_folder_missing_title,
    body = R.string.error_folder_missing_body
)

@Composable
private fun permissionLostLook() = DialogLook(
    icon = R.drawable.ic_sym_warning,
    tint = warningColor(),
    title = R.string.error_permission_lost_title,
    body = R.string.error_permission_lost_body
)

/** 모든 오류 다이얼로그가 공유하는 껍데기. 가운데 정렬된 카드 하나를 띄운다. */
@Composable
private fun ErrorDialogScaffold(look: DialogLook, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f).wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BadgeIcon(painterResource(look.icon), look.tint)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(look.title),
                    color = textPrimary(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(look.body),
                    color = textSecondary(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp)
                )
                Spacer(Modifier.height(20.dp))
                ConfirmButton(onDismiss)
            }
        }
    }
}

/** 색을 옅게 깐 원 안에 아이콘을 얹는다. */
@Composable
private fun BadgeIcon(icon: Painter, tint: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(tint.copy(alpha = 0.1f), CircleShape)
            .border(1.5.dp, tint.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun ConfirmButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentGreenEffective(),
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sym_check_circle),
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.action_got_it),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}
