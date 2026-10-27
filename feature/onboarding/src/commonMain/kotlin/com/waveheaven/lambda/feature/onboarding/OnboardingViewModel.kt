package com.waveheaven.lambda.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waveheaven.lambda.core.domain.consent.Consent
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Onboarding (WH-070 – WH-075): explains what is measured before any prompt, asks for explicit consent, lets the user
 * choose the weekly standard and only then asks for the microphone. Declining consent skips the microphone: the app
 * still works as a dose explorer (WH-074).
 */
class OnboardingViewModel(
    private val reviewConsentOnly: Boolean,
    private val preferences: UserPreferences,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            OnboardingUiState(steps = stepsFor(consentAccepted = true), step = stepsFor(consentAccepted = true).first()),
        )

    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val standard = preferences.weeklyStandard.first()
            mutableState.update { it.copy(standard = standard) }
        }
    }

    fun onAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.Next -> {
                next()
            }

            OnboardingAction.Back -> {
                back()
            }

            is OnboardingAction.ConsentChecked -> {
                mutableState.update { it.copy(consentChecked = action.checked) }
            }

            OnboardingAction.AcceptConsent -> {
                acceptConsent()
            }

            OnboardingAction.DeclineConsent -> {
                declineConsent()
            }

            is OnboardingAction.StandardSelected -> {
                selectStandard(action.standard)
            }

            is OnboardingAction.MicrophoneResult -> {
                mutableState.update { it.copy(microphone = if (action.granted) MicrophoneState.GRANTED else MicrophoneState.DENIED) }
            }
        }
    }

    private fun next() {
        val current = mutableState.value
        val nextStep = current.steps.getOrNull(current.steps.indexOf(current.step) + 1)
        if (nextStep == null) finish() else mutableState.update { it.copy(step = nextStep) }
    }

    private fun back() {
        mutableState.update { state ->
            state.steps.getOrNull(state.steps.indexOf(state.step) - 1)?.let { state.copy(step = it) } ?: state
        }
    }

    private fun acceptConsent() {
        if (!mutableState.value.consentChecked) return
        viewModelScope.launch {
            preferences.giveConsent(Consent.CURRENT_VERSION, clock.now())
            mutableState.update { it.copy(steps = stepsFor(consentAccepted = true)) }
            next()
        }
    }

    private fun declineConsent() {
        mutableState.update { it.copy(steps = stepsFor(consentAccepted = false), consentChecked = false) }
        next()
    }

    private fun selectStandard(standard: DoseStandard) {
        mutableState.update { it.copy(standard = standard) }
        viewModelScope.launch { preferences.setWeeklyStandard(standard) }
    }

    private fun finish() {
        viewModelScope.launch {
            if (!reviewConsentOnly) preferences.completeOnboarding()
            mutableState.update { it.copy(isFinished = true) }
        }
    }

    private fun stepsFor(consentAccepted: Boolean): List<OnboardingStep> =
        buildList {
            if (!reviewConsentOnly) add(OnboardingStep.WELCOME)
            add(OnboardingStep.PRIVACY)
            add(OnboardingStep.CONSENT)
            if (!reviewConsentOnly) add(OnboardingStep.STANDARD)
            if (consentAccepted) add(OnboardingStep.MICROPHONE)
        }
}
