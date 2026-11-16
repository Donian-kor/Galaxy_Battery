package com.donian.galaxy_battery

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.donian.galaxy_battery.ui.setup.FolderAccess
import com.donian.galaxy_battery.ui.setup.SetupStep
import com.donian.galaxy_battery.ui.setup.SetupStepRow
import com.donian.galaxy_battery.ui.theme.GalaxyBatteryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 방향(RTL)과 글꼴 배율에 대한 레이아웃 계약.
 *
 * 두 값은 기기 설정으로만 바뀌고, `debug.force_rtl` 은 재부팅 없이는 시스템 구성에 반영되지
 * 않는다(`dumpsys` 에서 `ldltr` 유지). 그래서 기기 설정을 건드리는 대신 **구성 값을 직접 주입**해
 * 같은 Compose 배치 경로를 검증한다. 기기 재부팅 없이 반복 실행할 수 있는 것이 장점이다.
 */
@RunWith(AndroidJUnit4::class)
class SetupLayoutUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(resId: Int): String = context.getString(resId)

    private fun setSetupContent(
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
        content: @Composable () -> Unit
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            GalaxyBatteryTheme {
                CompositionLocalProvider(
                    LocalLayoutDirection provides layoutDirection,
                    LocalDensity provides Density(density.density, fontScale)
                ) {
                    Column { content() }
                }
            }
        }
    }

    private fun banner() {
        setSetupContent {
            FolderAccess(isScanning = false, onOpenSettings = {}, onClearDataAndReload = {})
        }
    }

    /** 배너의 상태 문구 x 좌표. 노드를 합치지 않아야 Surface 가 아닌 Text 자체를 잰다. */
    private fun grantedTextLeft() =
        composeRule.onNodeWithText(text(R.string.setup_permission_granted), useUnmergedTree = true)
            .getBoundsInRoot()
            .left

    private fun settingsChevronLeft() =
        composeRule
            .onNodeWithContentDescription(text(R.string.setup_open_app_settings), useUnmergedTree = true)
            .getBoundsInRoot()
            .left

    @Test
    fun ltrBanner_putsChevronAfterTheText() {
        // LTR 기준선: 문구 다음(오른쪽)이 chevron 이다.
        banner()

        assertTrue(
            "LTR 에서는 chevron 이 문구보다 오른쪽이어야 한다",
            settingsChevronLeft() > grantedTextLeft()
        )
    }

    @Test
    fun rtlBanner_mirrorsTheChevronToTheStart() {
        // RTL 에서는 같은 Row 가 뒤집혀 chevron 이 왼쪽(시작)으로 간다.
        setSetupContent(layoutDirection = LayoutDirection.Rtl) {
            FolderAccess(isScanning = false, onOpenSettings = {}, onClearDataAndReload = {})
        }

        assertTrue(
            "RTL 에서는 chevron 이 문구보다 왼쪽이어야 한다",
            settingsChevronLeft() < grantedTextLeft()
        )
    }

    @Test
    fun largeFont_keepsStepContentAndDialButtonVisible() {
        // 글꼴 1.3배에서도 제목·설명·다이얼 버튼이 사라지거나 겹치지 않고 남아야 한다.
        setSetupContent(fontScale = 1.3f) {
            SetupStepRow(
                number = 1,
                step = SetupStep(
                    icon = R.drawable.ic_sym_folder,
                    title = TITLE,
                    desc = DESC,
                    actionLabel = DIAL,
                    onAction = {},
                    onEditAction = {}
                ),
                isLast = false
            )
        }

        composeRule.onNodeWithText(TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(DESC).assertIsDisplayed()
        composeRule.onNodeWithText(DIAL).assertIsDisplayed()
        // 편집 아이콘은 contentDescription 으로 달린다(IconButton 이 자식을 합친다).
        composeRule.onNodeWithContentDescription(text(R.string.dial_edit_title))
            .assertIsDisplayed()
    }

    private companion object {
        const val TITLE = "로그 폴더"
        const val DESC = "아래 버튼을 탭해 /log/ 폴더로 이동한 뒤 읽기 권한을 허용하세요."
        const val DIAL = "*#9900#"
    }
}
