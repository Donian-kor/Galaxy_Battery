package com.donian.galaxy_battery

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.donian.galaxy_battery.ui.setup.FolderAccess
import com.donian.galaxy_battery.ui.setup.SetupStep
import com.donian.galaxy_battery.ui.setup.SetupStepRow
import com.donian.galaxy_battery.ui.theme.GalaxyBatteryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Setup 화면 조각들의 렌더링 계약.
 *
 * 이 파일들은 단위 테스트로 덮을 수 없는 Compose 컴포저블이고, 화면 전체 테스트
 * ([SetupScreenUiTest])는 실제 폴더 권한 상태에 따라 내용이 달라진다. 그래서 조각을
 * 직접 띄워 "무엇이 보이고, 무엇이 눌리며, 어떤 콜백이 불리는가"만 고정한다.
 * 본문을 다시 쓰더라도 이 계약이 깨지면 여기서 먼저 드러난다.
 */
@RunWith(AndroidJUnit4::class)
class SetupComponentsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(resId: Int): String = context.getString(resId)

    private fun setSetupContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            GalaxyBatteryTheme {
                Column { content() }
            }
        }
    }

    private fun step(
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        onEditAction: (() -> Unit)? = null,
    ) = SetupStep(
        icon = R.drawable.ic_sym_folder,
        title = TITLE,
        desc = DESC,
        actionLabel = actionLabel,
        onAction = onAction,
        onEditAction = onEditAction,
        content = null
    )

    // ── 타임라인 ─────────────────────────────────────────────────────────────

    @Test
    fun pendingStep_showsItsNumber() {
        setSetupContent { SetupStepRow(number = 1, step = step(), isLast = false) }

        composeRule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun doneStep_replacesTheNumberWithACheck() {
        setSetupContent { SetupStepRow(number = 1, step = step(), isLast = false, isDone = true) }

        composeRule.onNodeWithText("1").assertDoesNotExist()
    }

    @Test
    fun stepBody_showsTitleAndDescription() {
        setSetupContent { SetupStepRow(number = 2, step = step(), isLast = false) }

        composeRule.onNodeWithText(TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(DESC).assertIsDisplayed()
    }

    // ── 다이얼 버튼 ───────────────────────────────────────────────────────────

    @Test
    fun dialButton_staysHiddenWhileAnyCallbackIsMissing() {
        // 라벨만 있고 콜백이 없으면 그리지 않는다 — 예전에는 여기서 NPE가 났다.
        setSetupContent { SetupStepRow(number = 3, step = step(actionLabel = DIAL), isLast = false) }

        composeRule.onNodeWithText(DIAL).assertDoesNotExist()
    }

    @Test
    fun dialButton_clickDials_longPressAndEditIconEdit() {
        var dials = 0
        var edits = 0
        setSetupContent {
            SetupStepRow(
                number = 4,
                step = step(
                    actionLabel = DIAL,
                    onAction = { dials++ },
                    onEditAction = { edits++ }
                ),
                isLast = false
            )
        }

        composeRule.onNodeWithText(DIAL).performClick()
        assertEquals("탭은 다이얼 호출", 1, dials)
        assertEquals("탭은 편집을 부르지 않는다", 0, edits)

        composeRule.onNodeWithText(DIAL).performTouchInput { longClick() }
        assertEquals("길게 누르면 편집", 1, edits)

        composeRule.onNodeWithContentDescription(text(R.string.dial_edit_title)).performClick()
        assertEquals("편집 아이콘도 편집", 2, edits)
    }

    // ── 폴더 권한 블록 ────────────────────────────────────────────────────────

    @Test
    fun scanning_showsProgressAndLocksTheActions() {
        setSetupContent {
            FolderAccess(isScanning = true, onOpenSettings = {}, onClearDataAndReload = {})
        }

        composeRule.onNodeWithText(text(R.string.setup_scanning)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.clear_data_confirm)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(text(R.string.setup_open_app_settings))
            .assertDoesNotExist()
    }

    @Test
    fun idle_showsGrantedAndClearDataRuns() {
        var clears = 0
        setSetupContent {
            FolderAccess(
                isScanning = false,
                onOpenSettings = {},
                onClearDataAndReload = { clears++ }
            )
        }

        composeRule.onNodeWithText(text(R.string.setup_permission_granted)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.clear_data_confirm))
            .assertIsEnabled()
            .performClick()

        assertEquals(1, clears)
    }

    @Test
    fun idle_bannerOpensAppSettings() {
        var opens = 0
        setSetupContent {
            FolderAccess(
                isScanning = false,
                onOpenSettings = { opens++ },
                onClearDataAndReload = {}
            )
        }

        composeRule.onNodeWithContentDescription(text(R.string.setup_open_app_settings))
            .assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.setup_permission_granted)).performClick()
        assertEquals(1, opens)
    }

    private companion object {
        const val TITLE = "로그 폴더"
        const val DESC = "설명 문장"
        const val DIAL = "*#9900#"
    }
}
