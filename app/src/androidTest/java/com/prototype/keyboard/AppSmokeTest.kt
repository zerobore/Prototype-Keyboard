package com.prototype.keyboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prototype.keyboard.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test (IT-01): app launches and shows the Setup screen.
 * Run on CI emulator via :app:connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesShowingSetup() {
        composeRule.onNodeWithText("Prototype Keyboard").assertIsDisplayed()
        composeRule.onNodeWithText("Setup").assertIsDisplayed()
    }

    @Test
    fun settingsTabOpens() {
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
