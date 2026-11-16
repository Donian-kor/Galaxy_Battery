package com.donian.galaxy_battery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import com.donian.galaxy_battery.ui.main.BatteryDashboard
import com.donian.galaxy_battery.ui.theme.GalaxyBatteryTheme

class MainActivity : ComponentActivity() {

    // ── 생명주기 ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        // 상태바/내비바 외형을 테마에 맞춘다.
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

        // 복원된 Activity는 새 프로세스에서 시작될 수 있으며, 이때 ViewModel은 비어 있다.
        val needsLoad = savedInstanceState == null || viewModel.folderUri == null

        // 최초 실행: 저장된 폴더 URI가 없음 → Setup 실행 후 현재 Activity 종료
        if (needsLoad && !viewModel.initialize()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        // activity_main.xml에 선언된 ComposeView에 Compose 콘텐츠를 연결한다.
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

    /** 대시보드가 내보낸 진단 리포트를 시스템 공유 시트로 연다. */
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
