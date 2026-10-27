package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.hearing.Ear
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Stereo pure tones for the hearing check: a sine in one ear (or both), at an RMS level in dBFS, with raised-cosine
 * fades of [RAMP_MILLIS] so the start and end do not click (a click would be heard before the tone itself).
 */
object ToneSynth {
    const val RAMP_MILLIS = 20

    /**
     * Interleaved stereo samples (left, right) for [durationMillis] of [frequencyHz] at [rmsDbfs] in [ear], or in both
     * ears when [ear] is `null`.
     */
    fun stereo(
        ear: Ear?,
        frequencyHz: Int,
        rmsDbfs: Double,
        durationMillis: Long,
        sampleRate: Int,
    ): FloatArray {
        require(rmsDbfs <= 0.0) { "A tone cannot exceed full scale, was $rmsDbfs dBFS" }
        val frames = (sampleRate * durationMillis / MILLIS_PER_SECOND).toInt()
        val ramp = sampleRate * RAMP_MILLIS / MILLIS_PER_SECOND.toInt()
        val amplitude = sqrt(2.0) * TEN.pow(rmsDbfs / DECIBELS_PER_DECADE)
        val left = ear == null || ear == Ear.LEFT
        val right = ear == null || ear == Ear.RIGHT
        return FloatArray(frames * 2).also { out ->
            for (n in 0 until frames) {
                val envelope =
                    when {
                        n < ramp -> fade(n, ramp)
                        n >= frames - ramp -> fade(frames - 1 - n, ramp)
                        else -> 1.0
                    }
                val sample = (amplitude * envelope * sin(2 * PI * frequencyHz * n / sampleRate)).toFloat()
                if (left) out[2 * n] = sample
                if (right) out[2 * n + 1] = sample
            }
        }
    }

    private fun fade(
        position: Int,
        length: Int,
    ): Double = HALF - HALF * cos(PI * position / length)

    private const val MILLIS_PER_SECOND = 1000L
    private const val HALF = 0.5
    private const val TEN = 10.0
    private const val DECIBELS_PER_DECADE = 20.0
}
