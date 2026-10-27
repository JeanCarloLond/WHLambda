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

// The content of each onboarding step.

@Composable
internal fun WelcomeStep(onAction: (OnboardingAction) -> Unit) {
    Title(Res.string.welcome_title)
    Body(Res.string.welcome_body)
    Text(stringResource(Res.string.welcome_disclaimer), style = MaterialTheme.typography.bodySmall)
    Button(onClick = { onAction(OnboardingAction.Next) }) { Text(stringResource(Res.string.welcome_start)) }
}

@Composable
internal fun PrivacyStep(
    canGoBack: Boolean,
    onAction: (OnboardingAction) -> Unit,
) {
    Title(Res.string.privacy_title)
    listOf(Res.string.privacy_noise, Res.string.privacy_never, Res.string.privacy_stored, Res.string.privacy_control).forEach {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("•", style = MaterialTheme.typography.bodyLarge)
            Body(it)
        }
    }
    Buttons(canGoBack, onAction) {
        Button(onClick = { onAction(OnboardingAction.Next) }) { Text(stringResource(Res.string.onboarding_continue)) }
    }
}

@Composable
internal fun ConsentStep(
    checked: Boolean,
    canGoBack: Boolean,
    onAction: (OnboardingAction) -> Unit,
) {
    Title(Res.string.consent_title)
    Body(Res.string.consent_text)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onAction(OnboardingAction.ConsentChecked(it)) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(stringResource(Res.string.consent_checkbox), modifier = Modifier.padding(start = 16.dp))
    }
    Buttons(canGoBack, onAction) {
        TextButton(onClick = { onAction(OnboardingAction.DeclineConsent) }) { Text(stringResource(Res.string.consent_decline)) }
        Button(
            onClick = { onAction(OnboardingAction.AcceptConsent) },
            enabled = checked,
        ) { Text(stringResource(Res.string.consent_accept)) }
    }
}

@Composable
internal fun StandardStep(
    state: OnboardingUiState,
    canGoBack: Boolean,
    onAction: (OnboardingAction) -> Unit,
) {
    Title(Res.string.standard_title)
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StandardOption(DoseStandard.WHO_ADULT, Res.string.standard_adult, Res.string.standard_adult_description, state.standard, onAction)
        StandardOption(
            DoseStandard.WHO_SENSITIVE,
            Res.string.standard_sensitive,
            Res.string.standard_sensitive_description,
            state.standard,
            onAction,
        )
    }
    Text(stringResource(Res.string.standard_later), style = MaterialTheme.typography.bodySmall)
    val isLast = state.step == state.steps.last()
    Buttons(canGoBack, onAction) {
        Button(onClick = { onAction(OnboardingAction.Next) }) {
            Text(stringResource(if (isLast) Res.string.onboarding_finish else Res.string.onboarding_continue))
        }
    }
}

@Composable
private fun StandardOption(
    standard: DoseStandard,
    title: StringResource,
    description: StringResource,
    selected: DoseStandard,
    onAction: (OnboardingAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = standard == selected, role = Role.RadioButton) {
                    onAction(OnboardingAction.StandardSelected(standard))
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = standard == selected, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun MicrophoneStep(
    state: OnboardingUiState,
    canGoBack: Boolean,
    onAction: (OnboardingAction) -> Unit,
    onRequestMicrophone: () -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    Title(Res.string.microphone_title)
    when (state.microphone) {
        MicrophoneState.NOT_ASKED -> {
            Body(Res.string.microphone_explanation)
            Buttons(canGoBack, onAction) { Button(onClick = onRequestMicrophone) { Text(stringResource(Res.string.microphone_allow)) } }
        }

        MicrophoneState.GRANTED -> {
            Body(Res.string.microphone_granted)
            Buttons(canGoBack, onAction) {
                Button(onClick = { onAction(OnboardingAction.Next) }) { Text(stringResource(Res.string.onboarding_finish)) }
            }
        }

        MicrophoneState.DENIED -> {
            Body(Res.string.microphone_denied)
            OutlinedButton(onClick = onOpenSystemSettings) { Text(stringResource(Res.string.microphone_open_settings)) }
            Buttons(canGoBack, onAction) {
                Button(onClick = { onAction(OnboardingAction.Next) }) { Text(stringResource(Res.string.onboarding_finish)) }
            }
        }
    }
}

/** "Back" on the left when there is a previous step, the step's own buttons on the right. */
@Composable
private fun Buttons(
    canGoBack: Boolean,
    onAction: (OnboardingAction) -> Unit,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canGoBack) {
            TextButton(onClick = { onAction(OnboardingAction.Back) }) { Text(stringResource(Res.string.onboarding_back)) }
        }
        Spacer(modifier = Modifier.weight(1f))
        content()
    }
}

@Composable
private fun Title(text: StringResource) {
    Text(stringResource(text), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Body(text: StringResource) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}
