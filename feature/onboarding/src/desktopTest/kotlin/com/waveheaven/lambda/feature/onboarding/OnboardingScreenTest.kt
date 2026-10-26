package com.waveheaven.lambda.feature.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OnboardingScreenTest {
    private lateinit var systemLocale: Locale
    private val steps = OnboardingStep.entries.toList()

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
    fun `privacy step says what is never recorded`() =
        runComposeUiTest {
            setContent { Screen(OnboardingUiState(steps, OnboardingStep.PRIVACY)) }

            onNodeWithText("Step 2 of 5").assertIsDisplayed()
            onNodeWithText("Nothing is recorded, saved or sent", substring = true).assertIsDisplayed()
        }

    @Test
    fun `consent can only be accepted once the box is ticked`() =
        runComposeUiTest {
            val actions = mutableListOf<OnboardingAction>()
            setContent { Screen(OnboardingUiState(steps, OnboardingStep.CONSENT), actions::add) }

            onNodeWithText("Accept").performScrollTo().assertIsNotEnabled()
            onNodeWithText("I agree to WaveHeaven processing this data as described.").performScrollTo().performClick()
            onNodeWithText("Not now").performScrollTo().performClick()

            assertEquals(listOf(OnboardingAction.ConsentChecked(true), OnboardingAction.DeclineConsent), actions)
        }

    @Test
    fun `a ticked box enables accepting`() =
        runComposeUiTest {
            setContent { Screen(OnboardingUiState(steps, OnboardingStep.CONSENT, consentChecked = true)) }

            onNodeWithText("Accept").performScrollTo().assertIsEnabled()
        }

    @Test
    fun `the standard step offers both WHO standards`() =
        runComposeUiTest {
            val actions = mutableListOf<OnboardingAction>()
            setContent { Screen(OnboardingUiState(steps, OnboardingStep.STANDARD), actions::add) }

            onNodeWithText("WHO sensitive").performClick()

            assertEquals(listOf<OnboardingAction>(OnboardingAction.StandardSelected(DoseStandard.WHO_SENSITIVE)), actions)
        }

    @Test
    fun `the microphone step asks the system only on request`() =
        runComposeUiTest {
            var requests = 0
            setContent { Screen(OnboardingUiState(steps, OnboardingStep.MICROPHONE), onRequestMicrophone = { requests++ }) }

            onNodeWithText("Allow microphone").performClick()

            assertEquals(1, requests)
        }

    @Test
    fun `a denied microphone explains the alternative and links to settings`() =
        runComposeUiTest {
            var opened = 0
            setContent {
                Screen(
                    OnboardingUiState(steps, OnboardingStep.MICROPHONE, microphone = MicrophoneState.DENIED),
                    onOpenSettings = { opened++ },
                )
            }

            onNodeWithText("Without the microphone", substring = true).assertIsDisplayed()
            onNodeWithText("Open system settings").performClick()

            assertEquals(1, opened)
        }

    @Test
    fun `is translated to Spanish`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("es"))
            setContent { Screen(OnboardingUiState(steps, OnboardingStep.WELCOME)) }

            onNodeWithText("Te damos la bienvenida a WaveHeaven").assertIsDisplayed()
            onNodeWithText("Paso 1 de 5").assertIsDisplayed()
        }

    @androidx.compose.runtime.Composable
    private fun Screen(
        state: OnboardingUiState,
        onAction: (OnboardingAction) -> Unit = {},
        onRequestMicrophone: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) = OnboardingScreen(state, onAction, onRequestMicrophone, onOpenSettings)
}
