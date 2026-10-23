package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlin.math.log10

/**
 * Turns a stream of raw microphone samples into one calibrated A-weighted Leq per window (1 s by default).
 *
 * Samples are only accumulated as a running sum of squares and discarded right away (ADR-0007).
 * Not thread-safe: one instance per audio stream.
 */
class LevelMeter(
    sampleRate: Int,
    private val calibration: MicrophoneCalibration,
    private val windowSamples: Int = sampleRate,
) {
    private val filter = AWeightingFilter(sampleRate)
    private var sumOfSquares = 0.0
    private var accumulated = 0

    init {
        require(windowSamples > 0) { "windowSamples must be positive" }
    }

    /** Feeds the first [size] samples (−1…1); calls [onLevel] each time a window completes. */
    fun process(
        samples: FloatArray,
        size: Int = samples.size,
        onLevel: (SoundLevel) -> Unit,
    ) {
        require(size in 0..samples.size) { "size $size outside 0..${samples.size}" }
        for (i in 0 until size) {
            val weighted = filter.process(samples[i].toDouble())
            sumOfSquares += weighted * weighted
            accumulated++
            if (accumulated == windowSamples) {
                onLevel(calibration.toSoundLevel(toDbfs(sumOfSquares / accumulated)))
                sumOfSquares = 0.0
                accumulated = 0
            }
        }
    }

    private fun toDbfs(meanSquare: Double): Double = DECIBELS_PER_DECADE_POWER * log10(meanSquare.coerceAtLeast(SILENCE_MEAN_SQUARE))

    private companion object {
        const val DECIBELS_PER_DECADE_POWER = 10.0

        /** Floor for digital silence: −120 dBFS, far below any microphone's self-noise. */
        const val SILENCE_MEAN_SQUARE = 1e-12
    }
}
