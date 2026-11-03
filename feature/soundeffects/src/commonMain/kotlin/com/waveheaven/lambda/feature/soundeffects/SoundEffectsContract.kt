package com.waveheaven.lambda.feature.soundeffects

import androidx.navigation3.runtime.NavKey
import com.waveheaven.lambda.core.domain.effects.EffectsMode
import com.waveheaven.lambda.core.domain.effects.EqBands
import com.waveheaven.lambda.core.domain.effects.EqPreset
import com.waveheaven.lambda.core.domain.effects.ReverbPreset
import kotlinx.serialization.Serializable

/** Navigation destination for the equaliser and effects screen. */
@Serializable
data object SoundEffectsKey : NavKey

/** Everything the equaliser and effects screen renders. Immutable; produced only by [SoundEffectsViewModel]. */
data class SoundEffectsUiState(
    val isLoading: Boolean = true,
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    /** The global curve shown on the band sliders, one gain per [EqBands] band. */
    val bandGainsDb: List<Int> = List(EqBands.COUNT) { 0 },
    /** Name of the active profile when it has its own EQ curve, which then replaces the global one (WH-092). */
    val profileWithEq: String? = null,
    val bassBoostDb: Int = 0,
    val extraVolumeDb: Int = 0,
    val reverb: ReverbPreset = ReverbPreset.NONE,
    /** 0 to 100. */
    val surroundPercent: Int = 0,
    val surroundSupported: Boolean = false,
    val mode: EffectsMode = EffectsMode.PER_APP,
    /** The effects raise what reaches the ear, which the dose does not include (WH-094). */
    val raisesLevel: Boolean = false,
    val running: Boolean = false,
    /** Apps that have effects now. */
    val apps: List<String> = emptyList(),
    /** Global mode is applied to the whole output. */
    val wholeOutput: Boolean = false,
    /** Apps whose effects another equaliser app took over (WH-095). */
    val lostControl: List<String> = emptyList(),
    /** Another equaliser app took over the whole output in global mode. */
    val lostWholeOutput: Boolean = false,
    val fullDetection: Boolean = false,
    /** What to run once from a computer for [fullDetection]. */
    val detectionCommand: String? = null,
)

/** User intents the equaliser and effects screen understands. */
sealed interface SoundEffectsAction {
    data class EnabledChanged(
        val enabled: Boolean,
    ) : SoundEffectsAction

    data class PresetSelected(
        val preset: EqPreset,
    ) : SoundEffectsAction

    /** Moving a band turns the curve into the user's own ([EqPreset.CUSTOM]). */
    data class BandChanged(
        val band: Int,
        val gainDb: Float,
    ) : SoundEffectsAction

    data class BassBoostChanged(
        val gainDb: Float,
    ) : SoundEffectsAction

    data class ExtraVolumeChanged(
        val gainDb: Float,
    ) : SoundEffectsAction

    data class ReverbSelected(
        val reverb: ReverbPreset,
    ) : SoundEffectsAction

    data class SurroundChanged(
        val percent: Float,
    ) : SoundEffectsAction

    data class ModeSelected(
        val mode: EffectsMode,
    ) : SoundEffectsAction

    /** The user says they granted the permission; check again. */
    data object CheckDetection : SoundEffectsAction
}
