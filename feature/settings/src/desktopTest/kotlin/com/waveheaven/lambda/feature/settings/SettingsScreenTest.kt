package com.waveheaven.lambda.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    private lateinit var systemLocale: Locale

    @BeforeTest
    fun useEnglish() {
        systemLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    @Test
    fun `the delete button reports the intent`() =
        runComposeUiTest {
            val actions = mutableListOf<SettingsAction>()
            setContent { SettingsScreen(state = SettingsUiState(), onAction = { actions += it }, onBack = {}) }

            onNodeWithText("Delete my listening data").performClick()

            assertEquals(listOf<SettingsAction>(SettingsAction.DeleteDataClicked), actions)
        }

    @Test
    fun `the confirmation dialog confirms or cancels`() =
        runComposeUiTest {
            val actions = mutableListOf<SettingsAction>()
            setContent { SettingsScreen(state = SettingsUiState(isConfirmingDeletion = true), onAction = { actions += it }, onBack = {}) }

            onNodeWithText("Delete all listening data?").assertIsDisplayed()
            onNodeWithText("Cancel").performClick()
            onNodeWithText("Delete").performClick()

            assertEquals(listOf(SettingsAction.DeleteDataDismissed, SettingsAction.DeleteDataConfirmed), actions)
        }

    @Test
    fun `a finished deletion is announced`() =
        runComposeUiTest {
            val actions = mutableListOf<SettingsAction>()
            setContent { SettingsScreen(state = SettingsUiState(showDeletionNotice = true), onAction = { actions += it }, onBack = {}) }

            onNodeWithText("Listening data deleted").assertIsDisplayed()
        }

    @Test
    fun `the back button reports the intent`() =
        runComposeUiTest {
            var backs = 0
            setContent { SettingsScreen(state = SettingsUiState(), onAction = {}, onBack = { backs++ }) }

            onNodeWithContentDescription("Back").performClick()

            assertEquals(1, backs)
        }
}
