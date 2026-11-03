package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.effects.EffectsMode
import com.waveheaven.lambda.core.domain.effects.EqPreset
import com.waveheaven.lambda.core.domain.effects.ResolvedEffects
import com.waveheaven.lambda.core.domain.effects.SoundEffectsSettings
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SoundEffectsEngineTest {
    private class FakeChain(
        val session: Int,
        val onControlChanged: () -> Unit,
    ) : EffectChain {
        override var hasControl = true
        val applied = mutableListOf<ResolvedEffects>()
        var released = false

        override fun apply(effects: ResolvedEffects) {
            applied += effects
        }

        override fun release() {
            released = true
        }

        fun setControl(control: Boolean) {
            hasControl = control
            onControlChanged()
        }
    }

    private val created = mutableListOf<FakeChain>()
    private val refused = mutableSetOf<Int>()
    private val factory =
        EffectChainFactory { session, onControlChanged ->
            if (session in refused) null else FakeChain(session, onControlChanged).also { created += it }
        }
    private val engine = SoundEffectsEngine(factory)

    private val flat = SoundEffectsSettings().resolve(null)
    private val rock = SoundEffectsSettings(preset = EqPreset.ROCK).resolve(null)
    private val spotify = PlaybackSession(97, "Spotify")
    private val vlc = PlaybackSession(145, "VLC")

    private fun chain(session: Int) = created.last { it.session == session }

    @Test
    fun `attaches a chain per session and applies the effects once`() =
        runEngine(EffectsInputs(rock, EffectsMode.PER_APP, listOf(spotify, vlc))) { inputs ->
            assertEquals(listOf(97, 145), created.map { it.session })
            assertEquals(listOf(rock), chain(97).applied)
            assertEquals(SoundEffectsStatus(running = true, apps = listOf("Spotify", "VLC")), engine.status.value)

            inputs.value = inputs.value.copy(sessions = listOf(spotify, vlc)) // same inputs, nothing to do
            runCurrent()
            assertEquals(listOf(rock), chain(97).applied)
        }

    @Test
    fun `new effects reach every chain`() =
        runEngine(EffectsInputs(flat, EffectsMode.PER_APP, listOf(spotify, vlc))) { inputs ->
            inputs.value = inputs.value.copy(effects = rock)
            runCurrent()

            assertEquals(listOf(flat, rock), chain(97).applied)
            assertEquals(listOf(flat, rock), chain(145).applied)
        }

    @Test
    fun `releases the chain of a session that ended and attaches new ones`() =
        runEngine(EffectsInputs(rock, EffectsMode.PER_APP, listOf(spotify))) { inputs ->
            inputs.value = inputs.value.copy(sessions = listOf(vlc))
            runCurrent()

            assertTrue(chain(97).released)
            assertEquals(listOf(rock), chain(145).applied)
            assertEquals(listOf("VLC"), engine.status.value.apps)
        }

    @Test
    fun `global mode uses one chain on the output mix and ignores sessions`() =
        runEngine(EffectsInputs(rock, EffectsMode.PER_APP, listOf(spotify))) { inputs ->
            inputs.value = inputs.value.copy(mode = EffectsMode.GLOBAL)
            runCurrent()

            assertTrue(chain(97).released)
            assertEquals(listOf(rock), chain(EffectChainFactory.OUTPUT_MIX).applied)
            assertEquals(listOf(SoundEffectsStatus.OUTPUT_MIX), engine.status.value.apps)
        }

    @Test
    fun `an output mix session reported by a player is never attached per app`() =
        runEngine(EffectsInputs(rock, EffectsMode.PER_APP, listOf(PlaybackSession(0, "System"), spotify))) {
            assertEquals(listOf(97), created.map { it.session })
        }

    @Test
    fun `reports lost control and re-applies the effects when control comes back`() =
        runEngine(EffectsInputs(flat, EffectsMode.PER_APP, listOf(spotify, vlc))) { inputs ->
            chain(97).setControl(false)
            runCurrent()
            assertEquals(listOf("VLC"), engine.status.value.apps)
            assertEquals(listOf("Spotify"), engine.status.value.lostControl)

            inputs.value = inputs.value.copy(effects = rock) // set while another app holds the effect
            runCurrent()
            assertEquals(listOf(flat), chain(97).applied)

            chain(97).setControl(true)
            runCurrent()
            assertEquals(listOf(flat, rock), chain(97).applied)
            assertEquals(emptyList(), engine.status.value.lostControl)
        }

    @Test
    fun `a refused session is retried on the next change`() {
        refused += 97
        runEngine(EffectsInputs(rock, EffectsMode.PER_APP, listOf(spotify))) { inputs ->
            assertEquals(emptyList(), created)
            assertEquals(emptyList(), engine.status.value.apps)

            refused.clear()
            inputs.value = inputs.value.copy(sessions = listOf(spotify, vlc))
            runCurrent()
            assertEquals(listOf(97, 145), created.map { it.session })
        }
    }

    @Test
    fun `stopping releases every chain and clears the status`() =
        runTest {
            val inputs = MutableStateFlow(EffectsInputs(rock, EffectsMode.PER_APP, listOf(spotify, vlc)))
            val job = launch { engine.run(inputs) }
            runCurrent()
            assertTrue(engine.status.value.running)

            job.cancel()
            runCurrent()

            assertTrue(created.all { it.released })
            assertFalse(engine.status.value.running)
            assertEquals(emptyList(), engine.status.value.apps)
        }

    private fun runEngine(
        initial: EffectsInputs,
        block: suspend TestScope.(MutableStateFlow<EffectsInputs>) -> Unit,
    ) = runTest {
        val inputs = MutableStateFlow(initial)
        val job = launch { engine.run(inputs) }
        runCurrent()
        block(inputs)
        job.cancel()
    }
}
