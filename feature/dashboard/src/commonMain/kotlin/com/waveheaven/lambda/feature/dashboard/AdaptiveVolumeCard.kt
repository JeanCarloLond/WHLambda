package com.waveheaven.lambda.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeProblem
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.feature.dashboard.resources.Res
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_hint_above_limit
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_hint_limited
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_hint_manual
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_microphone_denied
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_off
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_open_settings
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_pause
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_paused
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_problem_microphone
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_resume
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_running
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_running_no_output
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_start
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_stop
import com.waveheaven.lambda.feature.dashboard.resources.adaptive_title
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
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.adaptive_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            // Read out when it changes, so screen-reader users hear the loop react without looking.
            Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(statusText(adaptive), style = MaterialTheme.typography.bodyMedium)
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
            Controls(adaptive.state, canStart, onAction)
        }
    }
}

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
        },
    )
