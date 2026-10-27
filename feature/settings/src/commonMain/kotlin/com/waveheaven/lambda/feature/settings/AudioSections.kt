package com.waveheaven.lambda.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.feature.settings.resources.Res
import com.waveheaven.lambda.feature.settings.resources.settings_calibration_description
import com.waveheaven.lambda.feature.settings.resources.settings_calibration_reset
import com.waveheaven.lambda.feature.settings.resources.settings_calibration_title
import com.waveheaven.lambda.feature.settings.resources.settings_calibration_value
import com.waveheaven.lambda.feature.settings.resources.settings_hearing_description
import com.waveheaven.lambda.feature.settings.resources.settings_hearing_open
import com.waveheaven.lambda.feature.settings.resources.settings_hearing_title
import com.waveheaven.lambda.feature.settings.resources.settings_media_app_description
import com.waveheaven.lambda.feature.settings.resources.settings_media_app_none
import com.waveheaven.lambda.feature.settings.resources.settings_media_app_refresh
import com.waveheaven.lambda.feature.settings.resources.settings_media_app_title
import org.jetbrains.compose.resources.stringResource

/** Desktop only: which application's volume WaveHeaven adapts (ADR-0009, P3). */
@Composable
internal fun MediaAppSection(
    selected: String?,
    running: List<String>,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The chosen app stays listed even when it is not playing right now.
    val apps = (listOfNotNull(selected) + running).distinct()
    Column(modifier = modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.settings_media_app_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(Res.string.settings_media_app_description), style = MaterialTheme.typography.bodyMedium)
        if (apps.isEmpty()) Text(stringResource(Res.string.settings_media_app_none), style = MaterialTheme.typography.bodyMedium)
        apps.forEach { app ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = app == selected, role = Role.RadioButton) { onAction(SettingsAction.MediaAppSelected(app)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = app == selected, onClick = null)
                Text(app, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
            }
        }
        OutlinedButton(
            onClick = { onAction(SettingsAction.RefreshRunningApps) },
        ) { Text(stringResource(Res.string.settings_media_app_refresh)) }
    }
}

/** The user's correction to the microphone's nominal calibration, against a reference meter (WH-004, spike #3). */
@Composable
internal fun CalibrationSection(
    adjustmentDb: Int,
    onAction: (SettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = stringResource(Res.string.settings_calibration_value, if (adjustmentDb > 0) "+$adjustmentDb" else "$adjustmentDb")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.settings_calibration_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(Res.string.settings_calibration_description), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        val range = UserPreferences.MICROPHONE_ADJUSTMENT_RANGE_DB
        Slider(
            value = adjustmentDb.toFloat(),
            onValueChange = { onAction(SettingsAction.MicrophoneAdjustmentChanged(it)) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = (range.endInclusive - range.start).toInt() - 1,
            modifier = Modifier.semantics { contentDescription = value },
        )
        TextButton(onClick = { onAction(SettingsAction.MicrophoneAdjustmentReset) }, enabled = adjustmentDb != 0) {
            Text(stringResource(Res.string.settings_calibration_reset))
        }
    }
}

/** Entry to the hearing check (#19), which lives in its own feature. */
@Composable
internal fun HearingSection(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.settings_hearing_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(Res.string.settings_hearing_description), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onOpen) { Text(stringResource(Res.string.settings_hearing_open)) }
    }
}
