package com.waveheaven.lambda.feature.soundeffects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waveheaven.lambda.core.domain.effects.EqBands
import com.waveheaven.lambda.core.domain.effects.EqPreset
import com.waveheaven.lambda.core.domain.effects.SoundEffects
import com.waveheaven.lambda.core.domain.effects.SoundEffectsSettings
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStatus
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStore
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Edits the equaliser and effects (WH-090 – WH-095) and turns them on and off. Slider moves show at once and are
 * saved in the background, the latest one winning, so dragging does not queue a write per frame.
 */
class SoundEffectsViewModel(
    private val store: SoundEffectsStore,
    profiles: ListeningProfileStore,
    private val effects: SoundEffects,
) : ViewModel() {
    /** An edit not saved yet; `null` once the store has it, so changes made elsewhere (the notification) show. */
    private val edit = MutableStateFlow<SoundEffectsSettings?>(null)
    private var current = SoundEffectsSettings()

    /** On or off asked by the user, done once a saved edit has it: the service reads the stored setting at start. */
    private var switchTo: Boolean? = null

    val state: StateFlow<SoundEffectsUiState> =
        combine(store.settings, edit, profiles.activeProfile(), effects.status) { stored, editing, profile, status ->
            val settings = editing ?: stored
            current = settings
            toUi(settings, profile, status)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SoundEffectsUiState())

    init {
        viewModelScope.launch {
            edit.filterNotNull().conflate().collect { settings ->
                store.save(settings)
                if (switchTo == settings.enabled) {
                    if (settings.enabled) effects.start() else effects.stop()
                    switchTo = null
                }
                edit.compareAndSet(settings, null)
            }
        }
    }

    fun onAction(action: SoundEffectsAction) {
        when (action) {
            is SoundEffectsAction.EnabledChanged -> {
                switchTo = action.enabled
                update { it.copy(enabled = action.enabled) }
            }

            is SoundEffectsAction.PresetSelected -> {
                update { it.copy(preset = action.preset) }
            }

            is SoundEffectsAction.BandChanged -> {
                update { it.withBand(action.band, action.gainDb.whole()) }
            }

            is SoundEffectsAction.BassBoostChanged -> {
                update { it.copy(bassBoostDb = action.gainDb.whole().coerceIn(SoundEffectsSettings.BASS_BOOST_RANGE_DB)) }
            }

            is SoundEffectsAction.ExtraVolumeChanged -> {
                update { it.copy(extraVolumeDb = action.gainDb.whole().coerceIn(SoundEffectsSettings.EXTRA_VOLUME_RANGE_DB)) }
            }

            is SoundEffectsAction.ReverbSelected -> {
                update { it.copy(reverb = action.reverb) }
            }

            is SoundEffectsAction.SurroundChanged -> {
                update { it.copy(surround = action.percent.snapToTens() / PERCENT) }
            }

            is SoundEffectsAction.ModeSelected -> {
                update { it.copy(mode = action.mode) }
            }

            SoundEffectsAction.CheckDetection -> {
                effects.refresh()
            }
        }
    }

    private fun update(change: (SoundEffectsSettings) -> SoundEffectsSettings) {
        edit.value = change(current)
    }

    private fun SoundEffectsSettings.withBand(
        band: Int,
        gainDb: Double,
    ): SoundEffectsSettings {
        if (band !in 0 until EqBands.COUNT) return this
        val gains = curveDb.toMutableList().apply { this[band] = gainDb.coerceIn(EqBands.GAIN_RANGE_DB) }
        return copy(preset = EqPreset.CUSTOM, customGainsDb = gains)
    }

    private fun toUi(
        settings: SoundEffectsSettings,
        profile: ListeningProfile,
        status: SoundEffectsStatus,
    ) = SoundEffectsUiState(
        isLoading = false,
        enabled = settings.enabled,
        preset = settings.preset,
        bandGainsDb = settings.curveDb.map { it.roundToInt() },
        profileWithEq = profile.name.takeIf { profile.eq != null },
        bassBoostDb = settings.bassBoostDb.roundToInt(),
        extraVolumeDb = settings.extraVolumeDb.roundToInt(),
        reverb = settings.reverb,
        surroundPercent = (settings.surround * PERCENT).roundToInt(),
        surroundSupported = status.surroundSupported,
        mode = settings.mode,
        raisesLevel = settings.resolve(profile.eq).raisesLevel,
        running = status.running,
        apps = status.apps - SoundEffectsStatus.OUTPUT_MIX,
        wholeOutput = SoundEffectsStatus.OUTPUT_MIX in status.apps,
        lostControl = status.lostControl - SoundEffectsStatus.OUTPUT_MIX,
        lostWholeOutput = SoundEffectsStatus.OUTPUT_MIX in status.lostControl,
        fullDetection = status.fullDetection,
        detectionCommand = effects.detectionCommand,
    )

    private fun Float.whole(): Double = roundToInt().toDouble()

    private fun Float.snapToTens(): Double = ((this / TEN).roundToInt() * TEN).toDouble().coerceIn(0.0, PERCENT)

    private companion object {
        const val PERCENT = 100.0
        const val TEN = 10f
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
