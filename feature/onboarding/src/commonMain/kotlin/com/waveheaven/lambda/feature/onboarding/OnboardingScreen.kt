package com.waveheaven.lambda.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waveheaven.lambda.core.domain.consent.MicrophoneAccess
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.feature.onboarding.resources.Res
import com.waveheaven.lambda.feature.onboarding.resources.consent_accept
import com.waveheaven.lambda.feature.onboarding.resources.consent_checkbox
import com.waveheaven.lambda.feature.onboarding.resources.consent_decline
import com.waveheaven.lambda.feature.onboarding.resources.consent_text
import com.waveheaven.lambda.feature.onboarding.resources.consent_title
import com.waveheaven.lambda.feature.onboarding.resources.microphone_allow
import com.waveheaven.lambda.feature.onboarding.resources.microphone_denied
import com.waveheaven.lambda.feature.onboarding.resources.microphone_explanation
import com.waveheaven.lambda.feature.onboarding.resources.microphone_granted
import com.waveheaven.lambda.feature.onboarding.resources.microphone_open_settings
import com.waveheaven.lambda.feature.onboarding.resources.microphone_title
import com.waveheaven.lambda.feature.onboarding.resources.onboarding_back
import com.waveheaven.lambda.feature.onboarding.resources.onboarding_continue
import com.waveheaven.lambda.feature.onboarding.resources.onboarding_finish
import com.waveheaven.lambda.feature.onboarding.resources.onboarding_step
import com.waveheaven.lambda.feature.onboarding.resources.privacy_control
import com.waveheaven.lambda.feature.onboarding.resources.privacy_never
import com.waveheaven.lambda.feature.onboarding.resources.privacy_noise
import com.waveheaven.lambda.feature.onboarding.resources.privacy_stored
import com.waveheaven.lambda.feature.onboarding.resources.privacy_title
import com.waveheaven.lambda.feature.onboarding.resources.standard_adult
import com.waveheaven.lambda.feature.onboarding.resources.standard_adult_description
import com.waveheaven.lambda.feature.onboarding.resources.standard_later
import com.waveheaven.lambda.feature.onboarding.resources.standard_sensitive
import com.waveheaven.lambda.feature.onboarding.resources.standard_sensitive_description
import com.waveheaven.lambda.feature.onboarding.resources.standard_title
import com.waveheaven.lambda.feature.onboarding.resources.welcome_body
import com.waveheaven.lambda.feature.onboarding.resources.welcome_disclaimer
import com.waveheaven.lambda.feature.onboarding.resources.welcome_start
import com.waveheaven.lambda.feature.onboarding.resources.welcome_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stateful entry point. Bridges the platform's [microphoneAccess] (which needs an activity on Android) and the
 * stateless [OnboardingScreen], and calls [onFinished] once the flow is over.
 */
@Composable
fun OnboardingRoute(
    reviewConsentOnly: Boolean,
    microphoneAccess: MicrophoneAccess,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = koinViewModel(parameters = { parametersOf(reviewConsentOnly) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.MICROPHONE && microphoneAccess.isGranted()) {
            viewModel.onAction(OnboardingAction.MicrophoneResult(granted = true))
        }
    }
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onFinished()
    }
    OnboardingScreen(
        state = state,
        onAction = viewModel::onAction,
        onRequestMicrophone = { microphoneAccess.request { granted -> viewModel.onAction(OnboardingAction.MicrophoneResult(granted)) } },
        onOpenSystemSettings = microphoneAccess::openSystemSettings,
        modifier = modifier,
    )
}

/** One onboarding step at a time, with its progress and buttons. Stateless. */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onAction: (OnboardingAction) -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canGoBack = state.stepNumber > 1
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(Res.string.onboarding_step, state.stepNumber, state.steps.size),
                style = MaterialTheme.typography.labelLarge,
            )
            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep(onAction)
                OnboardingStep.PRIVACY -> PrivacyStep(canGoBack, onAction)
                OnboardingStep.CONSENT -> ConsentStep(state.consentChecked, canGoBack, onAction)
                OnboardingStep.STANDARD -> StandardStep(state, canGoBack, onAction)
                OnboardingStep.MICROPHONE -> MicrophoneStep(state, canGoBack, onAction, onRequestMicrophone, onOpenSystemSettings)
            }
        }
    }
}
