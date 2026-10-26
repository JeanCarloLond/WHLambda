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
            setContent { DoseExplorerScreen(state = DoseExplorerUiState.at(94f), onAction = {}, onOpenAbout = {}, onOpenSettings = {}) }

            onNodeWithText("Level: 94 dB(A)").assertIsDisplayed()
            onNodeWithText("1 h").assertIsDisplayed() // NIOSH: 94 dB(A) is allowed 1 h per day
        }

    @Test
    fun `the settings button reports the intent`() =
        runComposeUiTest {
            var opened = 0
            setContent {
                DoseExplorerScreen(
                    state = DoseExplorerUiState.at(85f),
                    onAction = {},
                    onOpenAbout = {},
                    onOpenSettings = { opened++ },
                )
            }

            onNodeWithContentDescription("Settings").performClick()

            assertEquals(1, opened)
        }

    @Test
    fun `the about button reports the intent`() =
        runComposeUiTest {
            var opened = 0
            setContent {
                DoseExplorerScreen(
                    state = DoseExplorerUiState.at(85f),
                    onAction = {},
                    onOpenAbout = { opened++ },
                    onOpenSettings = {},
                )
            }

            onNodeWithContentDescription("About WaveHeaven").performClick()

            assertEquals(1, opened)
        }
}
