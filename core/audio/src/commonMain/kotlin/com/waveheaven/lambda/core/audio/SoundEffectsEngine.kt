package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.effects.EffectsMode
import com.waveheaven.lambda.core.domain.effects.ResolvedEffects
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStatus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** An app's audio playback session, which effects attach to. */
data class PlaybackSession(
    val id: Int,
    /** Display name of the app that owns it. */
    val app: String,
)

/** Everything the engine needs at one moment. */
data class EffectsInputs(
    val effects: ResolvedEffects,
    val mode: EffectsMode,
    /** Known sessions of other apps; ignored in [EffectsMode.GLOBAL]. */
    val sessions: List<PlaybackSession>,
)

/** The effects attached to one session: equaliser, gain and limiter, reverb, surround. */
interface EffectChain {
    /** Whether this app controls the effects; another equaliser app may take them over. */
    val hasControl: Boolean

    fun apply(effects: ResolvedEffects)

    fun release()
}

/** Creates [EffectChain]s on the platform. */
fun interface EffectChainFactory {
    /**
     * A chain on [session] ([OUTPUT_MIX] for the whole output), or `null` if the platform refuses it. The chain calls
     * [onControlChanged] whenever it loses or regains control.
     */
    fun create(
        session: Int,
        onControlChanged: () -> Unit,
    ): EffectChain?

    companion object {
        /** Android's session of the output mix, deprecated for effects but still honoured by many phones. */
        const val OUTPUT_MIX: Int = 0
    }
}

/**
 * Keeps one [EffectChain] per playback session in line with the settings (WH-093, ADR-0010): attaches chains to new
 * sessions, releases chains of sessions that ended, re-applies effects when they change or when control comes back,
 * and reports which apps have effects and which lost them to another app (WH-095).
 */
class SoundEffectsEngine(
    private val factory: EffectChainFactory,
) {
    private class Attached(
        val app: String,
        val chain: EffectChain,
        var applied: ResolvedEffects? = null,
    )

    private sealed interface Event {
        data class Inputs(
            val inputs: EffectsInputs,
        ) : Event

        data object ControlChanged : Event
    }

    private val chains = mutableMapOf<Int, Attached>()
    private val controlEvents = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val mutableStatus = MutableStateFlow(SoundEffectsStatus())
    private var latest: EffectsInputs? = null

    /** [SoundEffectsStatus.running] is true while [run] is collecting; device capabilities are filled in by the caller. */
    val status: StateFlow<SoundEffectsStatus> = mutableStatus.asStateFlow()

    /** Applies [inputs] until cancelled, then releases every chain. */
    suspend fun run(inputs: Flow<EffectsInputs>) {
        try {
            mutableStatus.value = mutableStatus.value.copy(running = true)
            merge(inputs.map { Event.Inputs(it) }, controlEvents).collect { event ->
                if (event is Event.Inputs) latest = event.inputs
                latest?.let(::sync)
            }
        } finally {
            chains.values.forEach { it.chain.release() }
            chains.clear()
            latest = null
            mutableStatus.value = SoundEffectsStatus()
        }
    }

    private fun sync(inputs: EffectsInputs) {
        val targets =
            when (inputs.mode) {
                EffectsMode.GLOBAL -> mapOf(EffectChainFactory.OUTPUT_MIX to SoundEffectsStatus.OUTPUT_MIX)
                EffectsMode.PER_APP -> inputs.sessions.filter { it.id != EffectChainFactory.OUTPUT_MIX }.associate { it.id to it.app }
            }
        (chains.keys - targets.keys).forEach { session -> chains.remove(session)?.chain?.release() }
        targets.forEach { (session, app) ->
            if (session !in chains) {
                factory.create(session) { controlEvents.tryEmit(Event.ControlChanged) }?.let { chains[session] = Attached(app, it) }
            }
        }
        chains.values.forEach { attached ->
            // Parameters set without control are lost, so a chain that regains control gets them again.
            if (attached.chain.hasControl && attached.applied != inputs.effects) {
                attached.chain.apply(inputs.effects)
                attached.applied = inputs.effects
            } else if (!attached.chain.hasControl) {
                attached.applied = null
            }
        }
        val (controlled, lost) = chains.values.partition { it.chain.hasControl }
        mutableStatus.value =
            mutableStatus.value.copy(
                running = true,
                apps = controlled.map { it.app }.distinct().sorted(),
                lostControl = lost.map { it.app }.distinct().sorted(),
            )
    }
}
