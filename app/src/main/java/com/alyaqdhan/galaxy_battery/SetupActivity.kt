package com.alyaqdhan.galaxy_battery
import com.alyaqdhan.galaxy_battery.ui.theme.*

import com.alyaqdhan.galaxy_battery.ui.setup.SetupScreen

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

class SetupActivity : ComponentActivity() {

    companion object {
        /** Pass true when opening Setup from the Dashboard (back button visible). */
        const val EXTRA_SHOW_BACK = "show_back"
    }

    // ── Folder picker ─────────────────────────────────────────────────────────
    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        val viewModel = appViewModel
        val showBack  = intent.getBooleanExtra(EXTRA_SHOW_BACK, false)

        // onFolderPicked now validates the folder and calls back with success/failure.
        // We only navigate away on success — on failure we stay here and show the
        // WrongFolder error dialog (set by the ViewModel).
        viewModel.onFolderPicked(uri) { success ->
            if (success) {
                if (showBack) {
                    // Opened from Dashboard → go back; MainActivity is still alive
                    finish()
                } else {
                    // First-run → launch Dashboard, clear back stack so user can't
                    // return to Setup with the back button
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
            }
            // On failure: ViewModel has set errorDialog — the Compose UI will show it.
            // We intentionally do NOT navigate; the user must pick a correct folder.
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_setup)

        window.decorView.post {
            val isDark = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars     = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }

        val viewModel = appViewModel
        val showBack  = intent.getBooleanExtra(EXTRA_SHOW_BACK, false)

        // Restored in a new process: the ViewModel starts out empty, so reload the saved folder.
        if (savedInstanceState != null && viewModel.folderUri == null) viewModel.initialize()

        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GalaxyBatteryTheme {
                    SetupScreen(
                        viewModel               = viewModel,
                        showBackButton          = showBack,
                        onPickFolder            = { folderPicker.launch(null) },
                        onOpenSettings          = { openAppSettings() },
                        onRemoveAccess          = {
                            viewModel.removeFolderAccess()
                            // The Dashboard has nothing left to show, so start over from first run.
                            startActivity(
                                Intent(this@SetupActivity, SetupActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            )
                            finish()
                        },
                        onClearDataAndReload = {
                            viewModel.clearAllData(keepUri = false)
                            // After clearing, re-pick the log folder so the user can reload their data.
                            folderPicker.launch(null)
                        },
                        onBack         = { finish() },
                        onDialCode     = { openDialer(viewModel.dialCode) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Access can be revoked in Settings while this screen is open. Skip while a folder
        // pick is being scanned: its result arrives just before this and handles errors itself.
        val viewModel = appViewModel
        if (!viewModel.isLoadingDetail) viewModel.checkOnResume()
    }

    /** Not every device has a phone app, a Wi-Fi tablet for example. */
    private fun openDialer(code: String) {
        // Dial code may come from user input (including non-Latin digits already
        // normalized). Sanitize so it can't break out of the tel: URI scheme.
        val sanitized = code.filter { it.isDigit() || it == '*' || it == '#' || it == '+' }
        if (sanitized.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", sanitized, null)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_dialer, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Android gives third-party apps no way to open their Permissions page directly,
     * so this opens App info, which is one tap away from it.
     */
    private fun openAppSettings() {
        val appInfo = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        try {
            startActivity(appInfo)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

