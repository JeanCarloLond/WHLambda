package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LevelMeterTest {
    private val rate = 48_000

    @Test
    fun `a 1 kHz tone reads its RMS level plus the calibration offset`() {
        val meter = LevelMeter(rate, MicrophoneCalibration(offsetDb = 100.0))
        val levels = mutableListOf<SoundLevel>()

        meter.process(tone(1000.0, amplitude = 0.1, seconds = 3)) { levels += it }

        // RMS of a 0.1 sine = 0.0707 → −23.0 dBFS → 77.0 dB with a +100 dB offset.
        assertEquals(3, levels.size)
        assertEquals(100.0 + 20 * log10(0.1 / kotlin.math.sqrt(2.0)), levels.last().decibels, 0.1)
    }

    @Test
    fun `low-frequency hum counts far less than a 1 kHz tone of equal energy`() {
        val hum = mutableListOf<SoundLevel>()
        val voice = mutableListOf<SoundLevel>()

        LevelMeter(rate, MicrophoneCalibration(100.0)).process(tone(50.0, 0.1, 2)) { hum += it }
        LevelMeter(rate, MicrophoneCalibration(100.0)).process(tone(1000.0, 0.1, 2)) { voice += it }

        assertTrue(voice.last().decibels - hum.last().decibels > 25.0)
    }

    @Test
    fun `windows span several buffers`() {
        val meter = LevelMeter(rate, MicrophoneCalibration(100.0))
        val levels = mutableListOf<SoundLevel>()
        val buffer = tone(1000.0, 0.1, 1)
        val chunk = 4_800

        for (start in buffer.indices step chunk) {
            meter.process(buffer.copyOfRange(start, start + chunk)) { levels += it }
        }

        assertEquals(1, levels.size)
    }

    @Test
    fun `only the given number of samples is consumed`() {
        val meter = LevelMeter(rate, MicrophoneCalibration(100.0), windowSamples = 10)
        val levels = mutableListOf<SoundLevel>()

        meter.process(FloatArray(25), size = 20) { levels += it }

        assertEquals(2, levels.size)
    }

    @Test
    fun `digital silence floors at minus 120 dBFS`() {
        val levels = mutableListOf<SoundLevel>()

        LevelMeter(rate, MicrophoneCalibration(100.0)).process(FloatArray(rate)) { levels += it }

        assertEquals(-20.0, levels.single().decibels, 1e-9)
    }

    @Test
    fun `nominal Android calibration follows the CDD voice-recognition sensitivity`() {
        assertEquals(112.35, MicrophoneCalibration.ANDROID_VOICE_RECOGNITION_NOMINAL.offsetDb, 0.01)
    }

    @Test
    fun `rejects invalid arguments`() {
        assertFailsWith<IllegalArgumentException> { LevelMeter(rate, MicrophoneCalibration(0.0), windowSamples = 0) }
        assertFailsWith<IllegalArgumentException> { LevelMeter(rate, MicrophoneCalibration(0.0)).process(FloatArray(1), size = 2) {} }
        assertFailsWith<IllegalArgumentException> { MicrophoneCalibration(Double.NaN) }
    }

    private fun tone(
        frequency: Double,
        amplitude: Double,
        seconds: Int,
    ): FloatArray = FloatArray(rate * seconds) { n -> (amplitude * sin(2 * PI * frequency * n / rate)).toFloat() }
}
