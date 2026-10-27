package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.hearing.Ear
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ToneSynthTest {
    private val rate = 48_000

    @Test
    fun `plays in the chosen ear only, at the requested RMS level`() {
        val tone = ToneSynth.stereo(Ear.LEFT, 1000, rmsDbfs = -30.0, durationMillis = 1000, sampleRate = rate)

        val left = tone.filterIndexed { i, _ -> i % 2 == 0 }
        val right = tone.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(rate, left.size)
        assertTrue(right.all { it == 0f })
        // The fades lower the RMS of a 1 s tone by a few hundredths of a dB.
        assertEquals(-30.0, rmsDb(left.subList(rate / 10, rate * 9 / 10)), 0.05)
    }

    @Test
    fun `both ears for the reference tone`() {
        val tone = ToneSynth.stereo(null, 1000, -20.0, 200, rate)

        assertTrue((0 until tone.size / 2).all { tone[2 * it] == tone[2 * it + 1] })
    }

    @Test
    fun `fades in and out without a click`() {
        val tone = ToneSynth.stereo(Ear.RIGHT, 1000, -20.0, 500, rate).filterIndexed { i, _ -> i % 2 == 1 }

        assertTrue(abs(tone.first()) < 1e-6)
        assertTrue(abs(tone.last()) < 1e-3)
        val ramp = rate * ToneSynth.RAMP_MILLIS / 1000
        assertTrue(tone.take(ramp / 4).maxOf { abs(it) } < tone.subList(ramp, 2 * ramp).maxOf { abs(it) } / 2)
    }

    @Test
    fun `never above full scale`() {
        assertFailsWith<IllegalArgumentException> { ToneSynth.stereo(Ear.LEFT, 1000, 3.0, 100, rate) }
    }

    private fun rmsDb(samples: List<Float>) = 20 * log10(sqrt(samples.sumOf { it.toDouble() * it } / samples.size))
}
