package com.waveheaven.lambda.feature.exposure

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
class DoseExplorerScreenTest {
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
    fun `renders the safe times of the given state`() =
        runComposeUiTest {
            setContent { DoseExplorerScreen(state = DoseExplorerUiState.at(94f), onAction = {}, onBack = {}) }

            onNodeWithText("Level: 94 dB(A)").assertIsDisplayed()
            onNodeWithText("1 h").assertIsDisplayed() // NIOSH: 94 dB(A) is allowed 1 h per day
        }

    @Test
    fun `the back button reports the intent`() =
        runComposeUiTest {
            var backs = 0
            setContent { DoseExplorerScreen(state = DoseExplorerUiState.at(85f), onAction = {}, onBack = { backs++ }) }

            onNodeWithContentDescription("Back").performClick()

            assertEquals(1, backs)
        }
}
