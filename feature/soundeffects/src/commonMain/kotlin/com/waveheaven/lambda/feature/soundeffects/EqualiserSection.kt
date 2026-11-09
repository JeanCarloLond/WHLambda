package com.waveheaven.lambda.feature.soundeffects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.waveheaven.lambda.core.designsystem.component.EqCurve
import com.waveheaven.lambda.core.designsystem.component.WhSlider
import com.waveheaven.lambda.core.domain.effects.EqBands
import com.waveheaven.lambda.core.domain.effects.EqPreset
import com.waveheaven.lambda.feature.soundeffects.resources.Res
import com.waveheaven.lambda.feature.soundeffects.resources.effects_band
import com.waveheaven.lambda.feature.soundeffects.resources.effects_equaliser
import com.waveheaven.lambda.feature.soundeffects.resources.effects_hz
import com.waveheaven.lambda.feature.soundeffects.resources.effects_khz
import com.waveheaven.lambda.feature.soundeffects.resources.effects_profile_eq
import com.waveheaven.lambda.feature.soundeffects.resources.effects_style
import com.waveheaven.lambda.feature.soundeffects.resources.preset_acoustic
import com.waveheaven.lambda.feature.soundeffects.resources.preset_bass
import com.waveheaven.lambda.feature.soundeffects.resources.preset_classical
import com.waveheaven.lambda.feature.soundeffects.resources.preset_custom
import com.waveheaven.lambda.feature.soundeffects.resources.preset_electronic
import com.waveheaven.lambda.feature.soundeffects.resources.preset_flat
import com.waveheaven.lambda.feature.soundeffects.resources.preset_hip_hop
import com.waveheaven.lambda.feature.soundeffects.resources.preset_jazz
import com.waveheaven.lambda.feature.soundeffects.resources.preset_pop
import com.waveheaven.lambda.feature.soundeffects.resources.preset_rock
import com.waveheaven.lambda.feature.soundeffects.resources.preset_treble
import com.waveheaven.lambda.feature.soundeffects.resources.preset_vocal
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Presets and the ten band sliders (WH-090), and the note when the active profile has its own curve (WH-092). */
@Composable
internal fun EqualiserSection(
    state: SoundEffectsUiState,
    onAction: (SoundEffectsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(Res.string.effects_equaliser))
        state.profileWithEq?.let { Text(stringResource(Res.string.effects_profile_eq, it), style = MaterialTheme.typography.bodyMedium) }
        Text(stringResource(Res.string.effects_style), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Custom is reached by moving a band, so it is shown only while selected.
            EqPreset.entries.filter { it != EqPreset.CUSTOM || state.preset == EqPreset.CUSTOM }.forEach { preset ->
                FilterChip(
                    selected = preset == state.preset,
                    onClick = { onAction(SoundEffectsAction.PresetSelected(preset)) },
                    label = { Text(stringResource(PRESET_NAMES.getValue(preset))) },
                )
            }
        }
        CurveBox(state.bandGainsDb)
        state.bandGainsDb.forEachIndexed { band, gain -> BandSlider(band = band, gainDb = gain, onAction = onAction) }
    }
}

/** The response of the current curve, with the lowest, middle and highest band written under it. */
@Composable
private fun CurveBox(gainsDb: List<Int>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .padding(16.dp),
    ) {
        EqCurve(
            gainsDb = gainsDb.map { it.toFloat() },
            rangeDb = CURVE_RANGE_DB,
            modifier = Modifier.fillMaxWidth().height(CURVE_HEIGHT),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, EqBands.COUNT / 2, EqBands.COUNT - 1).forEach { band ->
                Text(
                    bandName(EqBands.CENTERS_HZ[band]),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BandSlider(
    band: Int,
    gainDb: Int,
    onAction: (SoundEffectsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.effects_band, bandName(EqBands.CENTERS_HZ[band]), signed(gainDb))
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = gainDb.toFloat(),
            onValueChange = { onAction(SoundEffectsAction.BandChanged(band, it)) },
            valueRange = EqBands.GAIN_RANGE_DB.start.toFloat()..EqBands.GAIN_RANGE_DB.endInclusive.toFloat(),
            steps = BAND_STEPS,
            colors = WhSlider.colors(),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun bandName(frequencyHz: Double): String =
    if (frequencyHz >= KILO) {
        stringResource(Res.string.effects_khz, (frequencyHz / KILO).roundToInt())
    } else {
        stringResource(Res.string.effects_hz, frequencyHz.roundToInt())
    }

/** "+3", "0", "-6": the sign makes a boost and a cut tell apart without colour. */
internal fun signed(value: Int): String = if (value > 0) "+$value" else "$value"

private const val KILO = 1_000.0
private val CURVE_HEIGHT = 110.dp

/** Most presets stay within ±6 dB; drawing ±9 dB keeps their shape visible without clipping custom curves often. */
private const val CURVE_RANGE_DB = 9f

/** 1 dB steps from -12 to +12 dB. */
private const val BAND_STEPS = 23

private val PRESET_NAMES: Map<EqPreset, StringResource> =
    mapOf(
        EqPreset.FLAT to Res.string.preset_flat,
        EqPreset.BASS to Res.string.preset_bass,
        EqPreset.TREBLE to Res.string.preset_treble,
        EqPreset.VOCAL to Res.string.preset_vocal,
        EqPreset.ROCK to Res.string.preset_rock,
        EqPreset.POP to Res.string.preset_pop,
        EqPreset.ELECTRONIC to Res.string.preset_electronic,
        EqPreset.HIP_HOP to Res.string.preset_hip_hop,
        EqPreset.JAZZ to Res.string.preset_jazz,
        EqPreset.CLASSICAL to Res.string.preset_classical,
        EqPreset.ACOUSTIC to Res.string.preset_acoustic,
        EqPreset.CUSTOM to Res.string.preset_custom,
    )
