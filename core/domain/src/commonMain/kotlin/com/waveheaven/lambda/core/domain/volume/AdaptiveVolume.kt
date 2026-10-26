package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.Dose
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Port: the adaptive-volume loop (meter → controller → system volume) as the UI sees it. On Android it runs in a
 * `microphone` foreground service; on desktop inside the app.
 */
public interface AdaptiveVolume {
    public val status: StateFlow<AdaptiveVolumeStatus>

    /** Starts the loop. Must be called from a visible screen (Android 14 rule for microphone services, WH-020). */
    public fun start()

    /** Stops metering and volume changes but stays ready to resume (WH-023). */
    public fun pause()

    public fun resume()

    /** Stops everything and releases the microphone (WH-022). */
    public fun stop()

    /** For platforms or builds where the loop cannot run. */
    public object Unavailable : AdaptiveVolume {
        private val fixed = MutableStateFlow(AdaptiveVolumeStatus(AdaptiveVolumeState.UNAVAILABLE))

        override val status: StateFlow<AdaptiveVolumeStatus> = fixed.asStateFlow()

        override fun start(): Unit = Unit

        override fun pause(): Unit = Unit

        override fun resume(): Unit = Unit

        override fun stop(): Unit = Unit
    }
}

public enum class AdaptiveVolumeState {
    /** This platform or build cannot adapt the volume. */
    UNAVAILABLE,

    STOPPED,

    /** Running, but nothing is playing: the microphone is released until music starts (spike #2, D5). */
    WAITING_FOR_MUSIC,

    /** Metering and adapting. */
    RUNNING,

    PAUSED,
}

/** Why the loop stopped on its own. */
public enum class AdaptiveVolumeProblem {
    /** Consent missing, withdrawn or outdated (WH-071 – WH-073). */
    NO_CONSENT,

    /** The microphone permission is missing, or the microphone could not be opened. */
    MICROPHONE_UNAVAILABLE,
}

/**
 * What the loop is doing, for the dashboard and the notification.
 *
 * @property output estimated level at the ear right now, with [outputUncertaintyDb] (WH-032).
 * @property decision the controller's last decision, so the UI can explain it (e.g. the dose ceiling, WH-014).
 * @property dailyDose today's NIOSH dose, including the minute in progress.
 * @property problem why the loop stopped on its own, if it did.
 */
public data class AdaptiveVolumeStatus(
    val state: AdaptiveVolumeState,
    val ambient: SoundLevel? = null,
    val output: SoundLevel? = null,
    val outputUncertaintyDb: Double? = null,
    val decision: VolumeDecision? = null,
    val dailyDose: Dose? = null,
    val weeklyDose: Dose? = null,
    val problem: AdaptiveVolumeProblem? = null,
)
