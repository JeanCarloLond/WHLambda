package com.waveheaven.lambda.feature.onboarding

import com.waveheaven.lambda.core.domain.consent.Consent
import com.waveheaven.lambda.core.domain.consent.InMemoryUserPreferences
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.feature.onboarding.OnboardingStep.CONSENT
import com.waveheaven.lambda.feature.onboarding.OnboardingStep.MICROPHONE
import com.waveheaven.lambda.feature.onboarding.OnboardingStep.PRIVACY
import com.waveheaven.lambda.feature.onboarding.OnboardingStep.STANDARD
import com.waveheaven.lambda.feature.onboarding.OnboardingStep.WELCOME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// setMain is still experimental, but it is the supported way to give viewModelScope a test dispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val preferences = InMemoryUserPreferences()
    private val now = Instant.parse("2026-10-26T15:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `explains what is measured before consent and the microphone`() {
        val viewModel = OnboardingViewModel(reviewConsentOnly = false, preferences, clock)

        assertEquals(listOf(WELCOME, PRIVACY, CONSENT, STANDARD, MICROPHONE), viewModel.state.value.steps)
        assertEquals(WELCOME, viewModel.state.value.step)
    }

    @Test
    fun `accepting stores consent for the current text with its time`() =
        runTest {
            val viewModel = OnboardingViewModel(reviewConsentOnly = false, preferences, clock)
            repeat(2) { viewModel.onAction(OnboardingAction.Next) }

            viewModel.onAction(OnboardingAction.AcceptConsent) // not checked yet: ignored
            assertNull(preferences.consent.first())

            viewModel.onAction(OnboardingAction.ConsentChecked(true))
            viewModel.onAction(OnboardingAction.AcceptConsent)

            assertEquals(Consent(Consent.CURRENT_VERSION, now), preferences.consent.first())
            assertEquals(STANDARD, viewModel.state.value.step)
        }

    @Test
    fun `declining skips the microphone and still finishes onboarding`() =
        runTest {
            val viewModel = OnboardingViewModel(reviewConsentOnly = false, preferences, clock)
            repeat(2) { viewModel.onAction(OnboardingAction.Next) }

            viewModel.onAction(OnboardingAction.DeclineConsent)
            assertEquals(listOf(WELCOME, PRIVACY, CONSENT, STANDARD), viewModel.state.value.steps)
            assertEquals(STANDARD, viewModel.state.value.step)

            viewModel.onAction(OnboardingAction.Next)
            assertTrue(viewModel.state.value.isFinished)
            assertTrue(preferences.onboardingCompleted.first())
            assertNull(preferences.consent.first())
        }

    @Test
    fun `the chosen standard is saved at once`() =
        runTest {
            val viewModel = OnboardingViewModel(reviewConsentOnly = false, preferences, clock)

            viewModel.onAction(OnboardingAction.StandardSelected(DoseStandard.WHO_SENSITIVE))

            assertEquals(DoseStandard.WHO_SENSITIVE, viewModel.state.value.standard)
            assertEquals(DoseStandard.WHO_SENSITIVE, preferences.weeklyStandard.first())
        }

    @Test
    fun `a denied microphone is explained and onboarding can still finish`() =
        runTest {
            val viewModel = OnboardingViewModel(reviewConsentOnly = false, preferences, clock)
            repeat(2) { viewModel.onAction(OnboardingAction.Next) }
            viewModel.onAction(OnboardingAction.ConsentChecked(true))
            viewModel.onAction(OnboardingAction.AcceptConsent)
            viewModel.onAction(OnboardingAction.Next)
            assertEquals(MICROPHONE, viewModel.state.value.step)

            viewModel.onAction(OnboardingAction.MicrophoneResult(granted = false))
            assertEquals(MicrophoneState.DENIED, viewModel.state.value.microphone)

            viewModel.onAction(OnboardingAction.Next)
            assertTrue(viewModel.state.value.isFinished)
        }

    @Test
    fun `back goes to the previous step and stops at the first`() {
        val viewModel = OnboardingViewModel(reviewConsentOnly = false, preferences, clock)
        viewModel.onAction(OnboardingAction.Next)

        viewModel.onAction(OnboardingAction.Back)
        viewModel.onAction(OnboardingAction.Back)

        assertEquals(WELCOME, viewModel.state.value.step)
        assertEquals(1, viewModel.state.value.stepNumber)
    }

    @Test
    fun `reviewing consent asks only what is needed and leaves onboarding as it was`() =
        runTest {
            val viewModel = OnboardingViewModel(reviewConsentOnly = true, preferences, clock)
            assertEquals(listOf(PRIVACY, CONSENT, MICROPHONE), viewModel.state.value.steps)

            viewModel.onAction(OnboardingAction.Next)
            viewModel.onAction(OnboardingAction.ConsentChecked(true))
            viewModel.onAction(OnboardingAction.AcceptConsent)
            viewModel.onAction(OnboardingAction.MicrophoneResult(granted = true))
            viewModel.onAction(OnboardingAction.Next)

            assertTrue(viewModel.state.value.isFinished)
            assertFalse(preferences.onboardingCompleted.first())
            assertEquals(Consent.CURRENT_VERSION, preferences.consent.first()!!.version)
        }

    @Test
    fun `starts from the stored standard`() =
        runTest {
            val stored = InMemoryUserPreferences(weeklyStandard = DoseStandard.WHO_SENSITIVE)

            val viewModel = OnboardingViewModel(reviewConsentOnly = false, stored, clock)

            assertEquals(DoseStandard.WHO_SENSITIVE, viewModel.state.value.standard)
        }
}
