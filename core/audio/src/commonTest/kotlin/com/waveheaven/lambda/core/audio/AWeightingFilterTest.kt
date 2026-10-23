package com.waveheaven.lambda.core.audio

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AWeightingFilterTest {
    /** Nominal A-weighting from IEC 61672-1 Table 3, with Class 1 acceptance limits (dB). */
    private val iecTable =
        listOf(
            Triple(31.5, -39.4, 1.5),
            Triple(63.0, -26.2, 1.0),
            Triple(125.0, -16.1, 1.0),
            Triple(250.0, -8.6, 1.0),
            Triple(500.0, -3.2, 1.0),
            Triple(1000.0, 0.0, 0.7),
            Triple(2000.0, 1.2, 1.0),
            Triple(4000.0, 1.0, 1.0),
            Triple(8000.0, -1.1, 1.5),
            Triple(10000.0, -2.5, 2.0),
        )

    @Test
    fun `theoretical response matches IEC 61672 within Class 1 limits at 48 kHz`() {
        val filter = AWeightingFilter(48_000)

        iecTable.forEach { (frequency, expected, tolerance) ->
            assertEquals(expected, filter.responseDb(frequency), tolerance, "at $frequency Hz")
        }
    }

    @Test
    fun `filtering real tones attenuates them as the response predicts`() {
        listOf(63.0, 250.0, 1000.0, 4000.0, 10000.0).forEach { frequency ->
            val filter = AWeightingFilter(48_000)

            val measured = measuredGainDb(filter, frequency, 48_000)

            assertEquals(filter.responseDb(frequency), measured, 0.05, "at $frequency Hz")
        }
    }

    @Test
    fun `is normalised to 0 dB at 1 kHz for other sample rates`() {
        listOf(16_000, 44_100).forEach { rate ->
            assertEquals(0.0, AWeightingFilter(rate).responseDb(1000.0), 1e-9, "at $rate Hz")
        }
    }

    @Test
    fun `strongly rejects low-frequency rumble`() {
        assertEquals(-50.5, AWeightingFilter(48_000).responseDb(20.0), 1.5)
    }

    @Test
    fun `rejects sample rates too low for audio`() {
        assertFailsWith<IllegalArgumentException> { AWeightingFilter(4_000) }
    }

    private fun measuredGainDb(
        filter: AWeightingFilter,
        frequency: Double,
        sampleRate: Int,
    ): Double {
        val total = sampleRate * 2
        val settle = sampleRate / 2
        var inSquares = 0.0
        var outSquares = 0.0
        for (n in 0 until total) {
            val x = 0.5 * sin(2 * PI * frequency * n / sampleRate)
            val y = filter.process(x)
            if (n >= settle) {
                inSquares += x * x
                outSquares += y * y
            }
        }
        return 20 * log10(sqrt(outSquares / inSquares))
    }
}
