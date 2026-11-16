package com.donian.galaxy_battery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import com.donian.galaxy_battery.ui.rawlog.LogRawScreen
import com.donian.galaxy_battery.ui.theme.GalaxyBatteryTheme

class LogRawActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_log_raw)

        val viewModel = appViewModel

        // 원시 로그 텍스트는 메모리에만 존재하므로, 재시작 후에는 보여줄 내용이 없다.
        if (viewModel.selectedLogFileName.isEmpty()) {
            finish()
            return
        }

        val composeView = findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_view)
        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GalaxyBatteryTheme(darkTheme = galaxyApp.isDarkTheme) {
                    // 테마 값이 바뀌면 상태바·내비바 아이콘 대비도 같이 다시 맞춘다.
                    val dark = galaxyApp.isDarkTheme
                    SideEffect {
                        WindowCompat.getInsetsController(window, window.decorView).apply {
                            isAppearanceLightStatusBars     = !dark
                            isAppearanceLightNavigationBars = !dark
                        }
                    }
                    LogRawScreen(
                        fileName  = viewModel.selectedLogFileName,
                        text      = viewModel.rawLogText,
                        isLoading = viewModel.isLoadingRaw,
                        onBack    = { finish() }
                    )
                }
            }
        }
    }
}
