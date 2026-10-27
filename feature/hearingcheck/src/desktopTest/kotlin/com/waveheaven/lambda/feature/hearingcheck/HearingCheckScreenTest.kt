package com.waveheaven.lambda.feature.hearingcheck

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.core.domain.hearing.HearingResult.Assessment
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HearingCheckScreenTest {
    private lateinit var systemLocale: Locale
    private val result =
        ResultUi(
            date = "2026-10-26",
            rows = listOf(ThresholdRow(1000, -45, -40), ThresholdRow(6000, -45, null)),
            assessment = Assessment.ASYMMETRIC,
        )

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
    fun `the intro says it is not a diagnosis`() =
        runComposeUiTest {
            setContent { Screen(HearingCheckUiState()) }

            onNodeWithText("This is not a medical diagnosis", substring = true).assertIsDisplayed()
        }

    @Test
    fun `testing shows the ear and a big button, nothing about the tone`() =
        runComposeUiTest {
            val actions = mutableListOf<HearingCheckAction>()
            setContent { Screen(HearingCheckUiState(step = HearingStep.TESTING, ear = Ear.LEFT, progress = 0.5f), actions::add) }

            onNodeWithText("Left ear").assertIsDisplayed()
            onNodeWithText("I hear it").performClick()

            assertEquals(listOf<HearingCheckAction>(HearingCheckAction.Heard), actions)
        }

    @Test
    fun `the result is a readable table with referral advice`() =
        runComposeUiTest {
            setContent { Screen(HearingCheckUiState(step = HearingStep.RESULT, result = result)) }

            onNodeWithText("Your result (2026-10-26)").assertIsDisplayed()
            onNodeWithText("Please see an audiologist", substring = true).assertIsDisplayed()
            onNodeWithContentDescription("6000 Hz: Right -45 dB, Left Not heard").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `the result can be saved as a profile, retaken or deleted`() =
        runComposeUiTest {
            val actions = mutableListOf<HearingCheckAction>()
            setContent { Screen(HearingCheckUiState(step = HearingStep.RESULT, result = result), actions::add) }

            onNodeWithText("Save as listening profile").performScrollTo().performClick()
            onNodeWithText("Take it again").performScrollTo().performClick()
            onNodeWithText("Delete my results").performScrollTo().performClick()

            assertEquals(
                listOf(HearingCheckAction.SaveAsProfile(""), HearingCheckAction.Begin, HearingCheckAction.DeleteResults),
                actions,
            )
        }

    @Test
    fun `is translated to Spanish`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("es"))
            setContent { Screen(HearingCheckUiState(step = HearingStep.TESTING, ear = Ear.RIGHT)) }

            onNodeWithText("Oído derecho").assertIsDisplayed()
            onNodeWithText("Lo oigo").assertIsDisplayed()
        }

    @androidx.compose.runtime.Composable
    private fun Screen(
        state: HearingCheckUiState,
        onAction: (HearingCheckAction) -> Unit = {},
    ) = HearingCheckScreen(state, onAction, onBack = {})
}
