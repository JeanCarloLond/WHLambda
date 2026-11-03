package com.waveheaven.lambda.core.domain.effects

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Port: the platform side of the equaliser and effects. On Android it runs the effects on other apps' playback
 * (ADR-0010); on platforms without it, [isSupported] is false and the app hides the feature.
 */
public interface SoundEffects {
    /** Whether this platform can apply effects at all. Fixed for the life of the process. */
    public val isSupported: Boolean

    /**
     * The command the user runs once, from a computer, to let the app detect every player
     * ([SoundEffectsStatus.fullDetection]); `null` where it does not apply.
     */
    public val detectionCommand: String?

    /** Live state, for the screen. */
    public val status: StateFlow<SoundEffectsStatus>

    /** Starts applying the stored settings (from a visible screen, as Android requires). */
    public fun start()

    /** Stops and removes every effect. */
    public fun stop()

    /** Checks again what the platform allows, after the user granted a permission outside the app. */
    public fun refresh()
}

/**
 * What the effects are doing now.
 *
 * @property running whether effects are being applied.
 * @property fullDetection whether every media player is detected (the diagnostic permission is granted), or only
 *   the players that announce their session.
 * @property surroundSupported whether the device has a virtualiser for headphones.
 * @property apps the apps that have effects now, by display name; [OUTPUT_MIX] in global mode.
 * @property lostControl apps whose effects another equaliser app took over (WH-095); may contain [OUTPUT_MIX].
 */
public data class SoundEffectsStatus(
    val running: Boolean = false,
    val fullDetection: Boolean = false,
    val surroundSupported: Boolean = false,
    val apps: List<String> = emptyList(),
    val lostControl: List<String> = emptyList(),
) {
    public companion object {
        /** Stands for the whole output mix (global mode) in [apps] and [lostControl]; never an app name. */
        public const val OUTPUT_MIX: String = "*"
    }
}

/** [SoundEffects] for platforms that cannot apply effects (desktop for now) and for tests. */
public class UnsupportedSoundEffects : SoundEffects {
    override val isSupported: Boolean = false
    override val detectionCommand: String? = null
    override val status: StateFlow<SoundEffectsStatus> = MutableStateFlow(SoundEffectsStatus())

    override fun start(): Unit = Unit

    override fun stop(): Unit = Unit

    override fun refresh(): Unit = Unit
}

/** [SoundEffects] that records calls, for tests. */
public class FakeSoundEffects(
    status: SoundEffectsStatus = SoundEffectsStatus(),
) : SoundEffects {
    private val state = MutableStateFlow(status)

    /** Calls to [start], [stop] and [refresh], in order. */
    public val calls: MutableList<String> = mutableListOf()

    override val isSupported: Boolean = true
    override val detectionCommand: String = "adb shell pm grant com.example android.permission.DUMP"
    override val status: StateFlow<SoundEffectsStatus> = state

    /** Lets a test change what the platform reports. */
    public fun emit(status: SoundEffectsStatus) {
        state.value = status
    }

    override fun start() {
        calls += "start"
        state.value = state.value.copy(running = true)
    }

    override fun stop() {
        calls += "stop"
        state.value = state.value.copy(running = false, apps = emptyList(), lostControl = emptyList())
    }

    override fun refresh() {
        calls += "refresh"
    }
}
