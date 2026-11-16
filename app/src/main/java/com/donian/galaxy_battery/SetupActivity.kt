package com.donian.galaxy_battery
import com.donian.galaxy_battery.ui.theme.*

import com.donian.galaxy_battery.ui.setup.SetupScreen

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
        /** 대시보드에서 Setup을 열 때 true (뒤로가기 버튼 표시). */
        const val EXTRA_SHOW_BACK = "show_back"
    }

    // ── 폴더 선택기 ──────────────────────────────────────────────────────
    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        val viewModel = appViewModel
        val showBack  = intent.getBooleanExtra(EXTRA_SHOW_BACK, false)

        // onFolderPicked가 이제 폴더를 검증하고 성공/실패로 콜백한다.
        // 성공 시에만 화면을 넘기고, 실패하면 여기 머무르며 ViewModel이
        // 설정한 WrongFolder 오류 다이얼로그를 보여준다.
        viewModel.onFolderPicked(uri) { success ->
            if (success) {
                if (showBack) {
                    // 대시보드에서 열림 → 뒤로 돌아간다; MainActivity가 아직 살아 있다
                    finish()
                } else {
                    // 최초 실행 → 대시보드를 실행하고 백스택을 지워 사용자가
                    // 뒤로가기로 Setup에 돌아올 수 없게 한다
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
            }
            // 실패 시: ViewModel이 errorDialog를 설정했다 — Compose UI가 표시한다.
            // 일부러 이동하지 않으며, 사용자가 올바른 폴더를 골라야 한다.
        }
    }

    // ── 생명주기 ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_setup)

        val viewModel = appViewModel
        val showBack  = intent.getBooleanExtra(EXTRA_SHOW_BACK, false)

        // 새 프로세스에서 복원된 경우: ViewModel이 비어 시작하므로 저장된 폴더를 다시 로드한다.
        if (savedInstanceState != null && viewModel.folderUri == null) viewModel.initialize()

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
                    SetupScreen(
                        viewModel               = viewModel,
                        showBackButton          = showBack,
                        isDarkTheme             = dark,
                        onToggleTheme           = { galaxyApp.applyTheme(it) },
                        onPickFolder            = { folderPicker.launch(null) },
                        onOpenSettings          = { openAppSettings() },
                        onClearDataAndReload = {
                            viewModel.clearAllData(keepUri = false)
                            // 초기화 후, 사용자가 데이터를 다시 불러오도록 로그 폴더를 다시 고르게 한다.
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
        // 이 화면이 열린 동안 설정에서 접근 권한이 회수될 수 있다. 폴더 선택을
        // 스캔 중이라면 건너뛴다: 그 결과가 바로 직전에 도착해 오류를 직접 처리한다.
        val viewModel = appViewModel
        if (!viewModel.isLoadingDetail) viewModel.checkOnResume()
    }

    /** Wi-Fi 태블릿처럼 전화 앱이 없는 기기도 있다. */
    private fun openDialer(code: String) {
        // 다이얼 코드는 사용자 입력에서 올 수 있으므로, ASCII 숫자와 다이얼러가 받는
        // 기호만 남기도록 정제한다 — 그 외는 tel: URI 스킴을 깰 수 있다.
        val sanitized = code.filter { it in '0'..'9' || it == '*' || it == '#' || it == '+' }
        if (sanitized.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", sanitized, null)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_dialer, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Android는 서드파티 앱이 자신의 권한 페이지를 직접 열 방법을 제공하지 않는다.
     * 그래서 앱 정보(App info)를 연다 — 권한 페이지에서 한 탭 거리다.
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

