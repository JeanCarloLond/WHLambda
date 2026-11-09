package com.waveheaven.lambda.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.core.designsystem.component.Readout
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeProblem
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.feature.dashboard.resources.Res
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_choose_app
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_hint_above_limit
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_hint_limited
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_hint_manual
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_hint_speaker
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_label_music
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_label_noise
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_microphone_denied
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_off
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_open_settings
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_pause
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_paused
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_problem_media_app
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_problem_microphone
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_resume
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_running
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_running_no_output
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_start
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_stop
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_title
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_unit
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_waiting
import com.waveheaven.lambda.feature.dashboard.resources.dashboard_level
import org.jetbrains.compose.resources.stringResource

/**
 * Start, pause, resume and stop the adaptive-volume loop, with what it measures and why the volume is where it is
 * (WH-014). Starting is only possible with consent (the consent card explains otherwise).
 */
@Composable
internal fun AdaptiveVolumeCard(
    adaptive: AdaptiveUi,
    canStart: Boolean,
    onAction: (DashboardAction) -> Unit,
    onOpenMicrophoneSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = adaptive.state == AdaptiveVolumeState.RUNNING
    Card(
        modifier = modifier.fillMaxWidth(),
        // While it adapts, the card takes the brand blue so the running state is visible at a glance.
        colors =
            if (live) {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                CardDefaults.cardColors()
            },
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(Res.string.adaptive_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            val showReadouts = live && adaptive.output != null
            if (showReadouts) Readouts(adaptive, description = statusText(adaptive))
            // Read out when it changes, so screen-reader users hear the loop react without looking.
            Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // With the readouts on screen the sentence would repeat them; screen readers get it from the readouts.
                if (!showReadouts) Text(statusText(adaptive), style = MaterialTheme.typography.bodyMedium)
                adaptive.hint?.let { Text(hintText(it), style = MaterialTheme.typography.bodyMedium) }
            }
            if (adaptive.microphoneDenied || adaptive.problem == AdaptiveVolumeProblem.MICROPHONE_UNAVAILABLE) {
                Text(
                    stringResource(
                        if (adaptive.microphoneDenied) Res.string.adaptive_microphone_denied else Res.string.adaptive_problem_microphone,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onOpenMicrophoneSettings) { Text(stringResource(Res.string.adaptive_open_settings)) }
            }
            if (adaptive.problem == AdaptiveVolumeProblem.NO_MEDIA_APP) {
                Text(stringResource(Res.string.adaptive_problem_media_app), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(Res.string.adaptive_choose_app)) }
            }
            Controls(adaptive.state, canStart, onAction)
        }
    }
}

/** Noise and music in large type, so the loop can be read from arm's length. */
@Composable
private fun Readouts(
    adaptive: AdaptiveUi,
    description: String,
) {
    val unit = stringResource(Res.string.adaptive_unit)
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Readout(
            label = stringResource(Res.string.adaptive_label_noise),
            value = adaptive.ambientDb?.toString() ?: NO_VALUE,
            unit = unit,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        adaptive.output?.let { output ->
            Readout(
                label = stringResource(Res.string.adaptive_label_music),
                value = output.decibels.toString(),
                unit = unit,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private const val NO_VALUE = "–"

@Composable
private fun Controls(
    state: AdaptiveVolumeState,
    canStart: Boolean,
    onAction: (DashboardAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (state) {
            // The card is not shown when unavailable; Start stays disabled anyway.
            AdaptiveVolumeState.STOPPED, AdaptiveVolumeState.UNAVAILABLE -> {
                Button(
                    onClick = { onAction(DashboardAction.Start) },
                    enabled = canStart && state == AdaptiveVolumeState.STOPPED,
                ) { Text(stringResource(Res.string.adaptive_start)) }
            }

            AdaptiveVolumeState.RUNNING, AdaptiveVolumeState.WAITING_FOR_MUSIC -> {
                Button(onClick = { onAction(DashboardAction.Pause) }) { Text(stringResource(Res.string.adaptive_pause)) }
                OutlinedButton(onClick = { onAction(DashboardAction.Stop) }) { Text(stringResource(Res.string.adaptive_stop)) }
            }

            AdaptiveVolumeState.PAUSED -> {
                Button(onClick = { onAction(DashboardAction.Resume) }) { Text(stringResource(Res.string.adaptive_resume)) }
                OutlinedButton(onClick = { onAction(DashboardAction.Stop) }) { Text(stringResource(Res.string.adaptive_stop)) }
            }
        }
    }
}

@Composable
private fun statusText(adaptive: AdaptiveUi): String =
    when (adaptive.state) {
        AdaptiveVolumeState.RUNNING -> {
            val ambient = adaptive.ambientDb ?: 0
            adaptive.output?.let { output ->
                stringResource(
                    Res.string.adaptive_running,
                    ambient,
                    stringResource(Res.string.dashboard_level, output.decibels, output.marginDb),
                )
            } ?: stringResource(Res.string.adaptive_running_no_output, ambient)
        }

        AdaptiveVolumeState.WAITING_FOR_MUSIC -> {
            stringResource(Res.string.adaptive_waiting)
        }

        AdaptiveVolumeState.PAUSED -> {
            stringResource(Res.string.adaptive_paused)
        }

        AdaptiveVolumeState.STOPPED, AdaptiveVolumeState.UNAVAILABLE -> {
            stringResource(Res.string.adaptive_off)
        }
    }

@Composable
private fun hintText(hint: AdaptiveHint): String =
    stringResource(
        when (hint) {
            AdaptiveHint.LIMITED_BY_DOSE -> Res.string.adaptive_hint_limited
            AdaptiveHint.MANUAL_OVERRIDE -> Res.string.adaptive_hint_manual
            AdaptiveHint.ABOVE_LIMIT_BY_USER -> Res.string.adaptive_hint_above_limit
            AdaptiveHint.SPEAKER_OUTPUT -> Res.string.adaptive_hint_speaker
        },
    )
