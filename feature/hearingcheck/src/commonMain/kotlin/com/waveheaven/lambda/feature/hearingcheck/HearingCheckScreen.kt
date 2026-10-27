package com.waveheaven.lambda.feature.hearingcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.waveheaven.lambda.core.designsystem.icon.WhIcons
import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.feature.hearingcheck.resources.Res
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_begin
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_cancel
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_ear_left
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_ear_right
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_heard
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_instructions
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_not_diagnosis
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_play_reference
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_profile_name
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_start
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_title
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_volume_text
import com.waveheaven.lambda.feature.hearingcheck.resources.hearing_volume_title
import com.waveheaven.lambda.feature.hearingcheck.resources.navigate_back
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stateful entry point: connects [HearingCheckViewModel] to the stateless [HearingCheckScreen]. */
@Composable
fun HearingCheckRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HearingCheckViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profileName = stringResource(Res.string.hearing_profile_name)
    HearingCheckScreen(
        state = state,
        onAction = { action ->
            // The new profile's name comes from the screen, in the user's language.
            viewModel.onAction(if (action is HearingCheckAction.SaveAsProfile) HearingCheckAction.SaveAsProfile(profileName) else action)
        },
        onBack = {
            viewModel.onAction(HearingCheckAction.Cancel)
            onBack()
        },
        modifier = modifier,
    )
}

/** The hearing check, one step at a time (WH-220 – WH-222). Stateless. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearingCheckScreen(
    state: HearingCheckUiState,
    onAction: (HearingCheckAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.hearing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(WhIcons.ArrowBack, contentDescription = stringResource(Res.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.step) {
                HearingStep.INTRO -> Intro(state, onAction)
                HearingStep.VOLUME -> Volume(onAction)
                HearingStep.TESTING -> Testing(state, onAction)
                HearingStep.RESULT -> state.result?.let { ResultSection(it, state.savedAsProfile, onAction) }
            }
        }
    }
}

@Composable
private fun Intro(
    state: HearingCheckUiState,
    onAction: (HearingCheckAction) -> Unit,
) {
    Text(stringResource(Res.string.hearing_not_diagnosis), style = MaterialTheme.typography.bodyLarge)
    Text(stringResource(Res.string.hearing_instructions), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = { onAction(HearingCheckAction.Begin) }) { Text(stringResource(Res.string.hearing_begin)) }
    state.result?.let { ResultSection(it, state.savedAsProfile, onAction, showRetake = false) }
}

@Composable
private fun Volume(onAction: (HearingCheckAction) -> Unit) {
    Title(Res.string.hearing_volume_title)
    Text(stringResource(Res.string.hearing_volume_text), style = MaterialTheme.typography.bodyLarge)
    OutlinedButton(onClick = { onAction(HearingCheckAction.PlayReference) }) { Text(stringResource(Res.string.hearing_play_reference)) }
    Button(onClick = { onAction(HearingCheckAction.StartTest) }) { Text(stringResource(Res.string.hearing_start)) }
}

@Composable
private fun Testing(
    state: HearingCheckUiState,
    onAction: (HearingCheckAction) -> Unit,
) {
    Title(if (state.ear == Ear.LEFT) Res.string.hearing_ear_left else Res.string.hearing_ear_right)
    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
    // A large target: the user may be looking away and listening.
    Button(
        onClick = { onAction(HearingCheckAction.Heard) },
        modifier = Modifier.fillMaxWidth().heightIn(min = HEARD_BUTTON_HEIGHT.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        Text(stringResource(Res.string.hearing_heard), style = MaterialTheme.typography.headlineSmall)
    }
    TextButton(onClick = { onAction(HearingCheckAction.Cancel) }) { Text(stringResource(Res.string.hearing_cancel)) }
}

@Composable
internal fun Title(text: StringResource) {
    Text(stringResource(text), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

private const val HEARD_BUTTON_HEIGHT = 160
