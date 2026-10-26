package com.waveheaven.lambda.feature.onboarding

import androidx.navigation3.runtime.NavKey
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import kotlinx.serialization.Serializable

/**
 * Navigation destination for onboarding.
 *
 * @property reviewConsentOnly skip the welcome and the standard: used when consent is missing or outdated after
 *   onboarding, to explain and ask again (WH-072).
 */
@Serializable
data class OnboardingKey(
    val reviewConsentOnly: Boolean = false,
) : NavKey

enum class OnboardingStep {
    WELCOME,

    /** What is measured and what is never recorded, before any prompt (WH-070). */
    PRIVACY,

    /** Explicit, versioned consent for health data (WH-071). */
    CONSENT,

    /** WHO adult or WHO sensitive (WH-075). */
    STANDARD,

    /** Explains, then shows the system microphone prompt (WH-070, WH-074). */
    MICROPHONE,
}

enum class MicrophoneState {
    NOT_ASKED,
    GRANTED,
    DENIED,
}

/** Everything the onboarding screen renders. Immutable; produced only by [OnboardingViewModel]. */
data class OnboardingUiState(
    val steps: List<OnboardingStep>,
    val step: OnboardingStep,
    val consentChecked: Boolean = false,
    val standard: DoseStandard = DoseStandard.WHO_ADULT,
    val microphone: MicrophoneState = MicrophoneState.NOT_ASKED,
    /** Onboarding is over; the route leaves the screen once it sees this. */
    val isFinished: Boolean = false,
) {
    /** 1-based position for "Step 2 of 5". */
    val stepNumber: Int
        get() = steps.indexOf(step) + 1
}

/** User intents (and platform answers) the onboarding understands. */
sealed interface OnboardingAction {
    data object Next : OnboardingAction

    data object Back : OnboardingAction

    data class ConsentChecked(
        val checked: Boolean,
    ) : OnboardingAction

    data object AcceptConsent : OnboardingAction

    data object DeclineConsent : OnboardingAction

    data class StandardSelected(
        val standard: DoseStandard,
    ) : OnboardingAction

    /** The microphone permission is known: checked on arrival at the step, or answered by the system prompt. */
    data class MicrophoneResult(
        val granted: Boolean,
    ) : OnboardingAction
}
