package com.donian.galaxy_battery.ui.setup
import com.donian.galaxy_battery.ui.theme.*

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

import com.donian.galaxy_battery.*
import com.donian.galaxy_battery.R
import com.donian.galaxy_battery.AppViewModel

// ── SetupScreen composable ────────────────────────────────────────────────────

@Composable
fun SetupScreen(
    viewModel: AppViewModel,
    showBackButton: Boolean,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearDataAndReload: () -> Unit,
    onBack: () -> Unit,
    onDialCode: () -> Unit
) {
    var showEditDialog   by remember { mutableStateOf(false) }
    var editCodeDraft    by remember { mutableStateOf("") }
    var showClearDialog  by remember { mutableStateOf(false) }

    val dialCodeLabel = viewModel.dialCode

    // isLoadingDetail is true while the ViewModel is scanning after folder pick
    val isScanning        = viewModel.isLoadingDetail
    val hasLivePermission = viewModel.alreadyHasPerm

    val steps = listOf(
        SetupStep(
            icon         = R.drawable.ic_sym_call,
            title        = stringResource(R.string.step_dial_title),
            desc         = stringResource(R.string.step_dial_desc),
            actionLabel  = dialCodeLabel,
            onAction     = onDialCode,
            onEditAction = {
                editCodeDraft = dialCodeLabel
                showEditDialog = true
            }
        ),
        SetupStep(R.drawable.ic_sym_play_arrow, stringResource(R.string.step_run_title), stringResource(R.string.step_run_desc)),
        SetupStep(R.drawable.ic_sym_sd_card, stringResource(R.string.step_copy_title), stringResource(R.string.step_copy_desc)),
        SetupStep(
            icon    = R.drawable.ic_sym_folder,
            title   = stringResource(R.string.step_grant_title),
            desc    = stringResource(R.string.step_grant_desc),
            content = if (hasLivePermission) {
                {
                    FolderAccess(
                        isScanning          = isScanning,
                        onOpenSettings      = onOpenSettings,
                        onClearDataAndReload = { showClearDialog = true }
                    )
                }
            } else {
                null
            }
        )
    )
    val green             = accentGreenEffective()
    val page              = appColors().page
    val tp                = textPrimary()
    val ts                = textSecondary()
    val muted             = MaterialTheme.colorScheme.onSurfaceVariant
    val cardActionStyle   = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)

    // Show error dialogs (wrong folder, etc.)
    ErrorDialogHost(
        errorDialog = viewModel.errorDialog,
        onDismiss   = { viewModel.dismissErrorDialog() }
    )

    val scroll = rememberScrollState()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(Brush.verticalGradient(listOf(green.copy(alpha = 0.10f), Color.Transparent)))
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp)
            ) {
                // ── Header ────────────────────────────────────────────────────
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = ScreenContentTop - 9.dp, bottom = 20.dp), // Level with the Dashboard's title
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                    ) {
                        AppLogo()
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(
                                stringResource(R.string.app_name), color = tp,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.setup_subtitle), color = ts,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (showBackButton) {
                        // Same place and style as the Dashboard's corner buttons
                        FilledTonalIconButton(
                            onClick  = onBack,
                            modifier = Modifier.padding(top = ScreenContentTop + 23.dp).size(40.dp),
                            colors   = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = cornerButtonColor(),
                                contentColor   = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_sym_chevron_left),
                                stringResource(R.string.setup_back_to_dashboard),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Column {
                    // ── Steps ─────────────────────────────────────────────────
                    SetupCard {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                stringResource(R.string.setup_how_to), color = tp,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(Modifier.height(18.dp))
                            steps.forEachIndexed { i, step ->
                                SetupStepRow(
                                    number = i + 1,
                                    step   = step,
                                    isLast = i == steps.lastIndex,
                                    isDone = i == steps.lastIndex && hasLivePermission
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // Keeps scrolled content off the status bar icons, without covering the top at rest
            Column(Modifier.graphicsLayer { alpha = (scroll.value / 48.dp.toPx()).coerceIn(0f, 1f) }) {
                Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(page))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(Brush.verticalGradient(listOf(page, Color.Transparent)))
                )
            }
        }

        // ── Bottom actions ────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(page)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!hasLivePermission) {
                Button(
                    onClick   = onPickFolder,
                    enabled   = !isScanning,
                    modifier  = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape     = RoundedCornerShape(14.dp),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor         = green,
                        contentColor           = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor   = muted
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            color       = muted,
                            strokeWidth = 2.dp
                        )
                    } else {
                        MatchedIcon(R.drawable.ic_sym_folder, cardActionStyle, Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(
                            if (isScanning) R.string.setup_scanning else R.string.setup_grant_permission
                        ),
                        style = cardActionStyle
                    )
                }
            }
        }
    }



    // ── Clear data dialog ─────────────────────────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_data_title)) },
            text  = { Text(stringResource(R.string.clear_data_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearDataAndReload()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor   = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.clear_data_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.clear_data_cancel)) }
            }
        )
    }

    // ── Edit dial code dialog ─────────────────────────────────────────────────
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.dial_edit_title)) },
            text  = {
                // A dial code has no letters to set its direction, and it reads left to right.
                val codeStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
                OutlinedTextField(
                    value         = editCodeDraft,
                    onValueChange = { editCodeDraft = it },
                    singleLine    = true,
                    textStyle     = codeStyle,
                    label         = { Text(stringResource(R.string.dial_edit_label)) },
                    placeholder   = { Text(stringResource(R.string.dial_edit_placeholder), style = codeStyle) }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveDialCode(editCodeDraft)
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = green,
                        contentColor   = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
