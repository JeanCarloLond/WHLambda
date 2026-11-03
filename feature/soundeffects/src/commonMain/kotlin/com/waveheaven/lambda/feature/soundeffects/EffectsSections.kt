package com.waveheaven.lambda.feature.soundeffects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.core.domain.effects.EffectsMode
import com.waveheaven.lambda.core.domain.effects.ReverbPreset
import com.waveheaven.lambda.core.domain.effects.SoundEffectsSettings
import com.waveheaven.lambda.feature.soundeffects.resources.Res
import com.waveheaven.lambda.feature.soundeffects.resources.effects_apps
import com.waveheaven.lambda.feature.soundeffects.resources.effects_bass_boost
import com.waveheaven.lambda.feature.soundeffects.resources.effects_detection_check
import com.waveheaven.lambda.feature.soundeffects.resources.effects_detection_note
import com.waveheaven.lambda.feature.soundeffects.resources.effects_detection_off
import com.waveheaven.lambda.feature.soundeffects.resources.effects_detection_on
import com.waveheaven.lambda.feature.soundeffects.resources.effects_effects
import com.waveheaven.lambda.feature.soundeffects.resources.effects_extra_volume
import com.waveheaven.lambda.feature.soundeffects.resources.effects_extra_volume_description
import com.waveheaven.lambda.feature.soundeffects.resources.effects_mode_global
import com.waveheaven.lambda.feature.soundeffects.resources.effects_mode_global_description
import com.waveheaven.lambda.feature.soundeffects.resources.effects_mode_per_app
import com.waveheaven.lambda.feature.soundeffects.resources.effects_mode_per_app_description
import com.waveheaven.lambda.feature.soundeffects.resources.effects_no_apps
import com.waveheaven.lambda.feature.soundeffects.resources.effects_off
import com.waveheaven.lambda.feature.soundeffects.resources.effects_reach
import com.waveheaven.lambda.feature.soundeffects.resources.effects_reverb
import com.waveheaven.lambda.feature.soundeffects.resources.effects_surround
import com.waveheaven.lambda.feature.soundeffects.resources.effects_surround_description
import com.waveheaven.lambda.feature.soundeffects.resources.effects_whole_output_on
import com.waveheaven.lambda.feature.soundeffects.resources.reverb_large_hall
import com.waveheaven.lambda.feature.soundeffects.resources.reverb_large_room
import com.waveheaven.lambda.feature.soundeffects.resources.reverb_medium_hall
import com.waveheaven.lambda.feature.soundeffects.resources.reverb_medium_room
import com.waveheaven.lambda.feature.soundeffects.resources.reverb_none
import com.waveheaven.lambda.feature.soundeffects.resources.reverb_plate
import com.waveheaven.lambda.feature.soundeffects.resources.reverb_small_room
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Bass boost, extra volume, reverb and surround (WH-091). */
@Composable
internal fun EffectsSection(
    state: SoundEffectsUiState,
    onAction: (SoundEffectsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.effects_effects))
        LabelledSlider(
            label = stringResource(Res.string.effects_bass_boost, state.bassBoostDb),
            value = state.bassBoostDb.toFloat(),
            range = SoundEffectsSettings.BASS_BOOST_RANGE_DB.toFloatRange(),
            onChange = { onAction(SoundEffectsAction.BassBoostChanged(it)) },
        )
        LabelledSlider(
            label = stringResource(Res.string.effects_extra_volume, state.extraVolumeDb),
            description = stringResource(Res.string.effects_extra_volume_description),
            value = state.extraVolumeDb.toFloat(),
            range = SoundEffectsSettings.EXTRA_VOLUME_RANGE_DB.toFloatRange(),
            onChange = { onAction(SoundEffectsAction.ExtraVolumeChanged(it)) },
        )
        SectionTitle(stringResource(Res.string.effects_reverb), modifier = Modifier.padding(top = 16.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReverbPreset.entries.forEach { reverb ->
                FilterChip(
                    selected = reverb == state.reverb,
                    onClick = { onAction(SoundEffectsAction.ReverbSelected(reverb)) },
                    label = { Text(stringResource(REVERB_NAMES.getValue(reverb))) },
                )
            }
        }
        if (state.surroundSupported) {
            LabelledSlider(
                label = stringResource(Res.string.effects_surround, state.surroundPercent),
                description = stringResource(Res.string.effects_surround_description),
                value = state.surroundPercent.toFloat(),
                range = 0f..PERCENT,
                onChange = { onAction(SoundEffectsAction.SurroundChanged(it)) },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** Per app or the whole output, what has effects now, and how to detect every app (WH-093). */
@Composable
internal fun ReachSection(
    state: SoundEffectsUiState,
    onAction: (SoundEffectsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.effects_reach))
        Column(modifier = Modifier.selectableGroup()) {
            ModeRow(EffectsMode.PER_APP, Res.string.effects_mode_per_app, Res.string.effects_mode_per_app_description, state, onAction)
            ModeRow(EffectsMode.GLOBAL, Res.string.effects_mode_global, Res.string.effects_mode_global_description, state, onAction)
        }
        Text(reachStatus(state), style = MaterialTheme.typography.bodyMedium)
        if (state.mode == EffectsMode.PER_APP) Detection(state, onAction)
    }
}

@Composable
private fun reachStatus(state: SoundEffectsUiState): String =
    when {
        !state.enabled || !state.running -> stringResource(Res.string.effects_off)
        state.wholeOutput -> stringResource(Res.string.effects_whole_output_on)
        state.apps.isNotEmpty() -> stringResource(Res.string.effects_apps, state.apps.joinToString(", "))
        else -> stringResource(Res.string.effects_no_apps)
    }

@Composable
private fun Detection(
    state: SoundEffectsUiState,
    onAction: (SoundEffectsAction) -> Unit,
) {
    val command = state.detectionCommand
    if (state.fullDetection || command == null) {
        if (state.fullDetection) Text(stringResource(Res.string.effects_detection_on), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text(stringResource(Res.string.effects_detection_off), style = MaterialTheme.typography.bodyMedium)
    // Selectable, so the user can copy it to a computer.
    SelectionContainer {
        Text(command, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
    Text(stringResource(Res.string.effects_detection_note), style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { onAction(SoundEffectsAction.CheckDetection) }) {
        Text(stringResource(Res.string.effects_detection_check))
    }
}

@Composable
private fun ModeRow(
    mode: EffectsMode,
    title: StringResource,
    description: StringResource,
    state: SoundEffectsUiState,
    onAction: (SoundEffectsAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected = state.mode == mode, role = Role.RadioButton) { onAction(SoundEffectsAction.ModeSelected(mode)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = state.mode == mode, onClick = null)
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            // Whole decibels (or tens of percent for surround).
            steps = ((range.endInclusive - range.start) / stepOf(range)).toInt() - 1,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

private fun stepOf(range: ClosedFloatingPointRange<Float>): Float = if (range.endInclusive == PERCENT) PERCENT_STEP else 1f

private fun ClosedFloatingPointRange<Double>.toFloatRange(): ClosedFloatingPointRange<Float> = start.toFloat()..endInclusive.toFloat()

private const val PERCENT = 100f
private const val PERCENT_STEP = 10f

private val REVERB_NAMES: Map<ReverbPreset, StringResource> =
    mapOf(
        ReverbPreset.NONE to Res.string.reverb_none,
        ReverbPreset.SMALL_ROOM to Res.string.reverb_small_room,
        ReverbPreset.MEDIUM_ROOM to Res.string.reverb_medium_room,
        ReverbPreset.LARGE_ROOM to Res.string.reverb_large_room,
        ReverbPreset.MEDIUM_HALL to Res.string.reverb_medium_hall,
        ReverbPreset.LARGE_HALL to Res.string.reverb_large_hall,
        ReverbPreset.PLATE to Res.string.reverb_plate,
    )
