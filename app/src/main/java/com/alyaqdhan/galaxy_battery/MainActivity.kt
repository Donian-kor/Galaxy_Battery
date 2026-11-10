package com.alyaqdhan.galaxy_battery
import com.alyaqdhan.galaxy_battery.ui.theme.*

import com.alyaqdhan.galaxy_battery.ui.main.BatteryDashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        // Status-bar / nav-bar appearance tracks theme
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

        // A restored Activity can land in a new process, where the ViewModel starts out empty.
        val needsLoad = savedInstanceState == null || viewModel.folderUri == null

        // First-run: no folder URI saved → open Setup, finish this Activity
        if (needsLoad && !viewModel.initialize()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        // Attach Compose content to the ComposeView declared in activity_main.xml
        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GalaxyBatteryTheme {
                    BatteryDashboard(
                        viewModel       = viewModel,
                        onNavigateSetup = {
                            startActivity(Intent(this@MainActivity, SetupActivity::class.java)
                                .putExtra(SetupActivity.EXTRA_SHOW_BACK, true))
                        },
                        onNavigateRaw   = {
                            startActivity(Intent(this@MainActivity, LogRawActivity::class.java))
                        },
                        onExportReport  = { shareBatteryReport() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appViewModel.checkOnResume()
    }

    /** Opens the system share sheet with the diagnostic report the dashboard exported. */
    private fun shareBatteryReport() {
        val info = appViewModel.batteryInfo ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_subject))
            putExtra(Intent.EXTRA_TEXT, BatteryReport.build(this@MainActivity, info))
        }
        startActivity(Intent.createChooser(send, getString(R.string.report_share)))
    }
}

