package com.alyaqdhan.galaxy_battery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupScreenUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<SetupActivity>()

    @Test
    fun grantPermissionButton_isDisplayed() {
        composeRule.onNodeWithText("Grant Permission to Log Folder").assertIsDisplayed()
    }

    @Test
    fun appName_isDisplayed() {
        composeRule.onNodeWithText("갤럭시 배터리 정보").assertIsDisplayed()
    }
}