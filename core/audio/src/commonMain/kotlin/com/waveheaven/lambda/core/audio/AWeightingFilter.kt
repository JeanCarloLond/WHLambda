package com.waveheaven.lambda.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Digital A-weighting filter (IEC 61672-1), applied sample by sample.
 *
 * Built from the standard's analog transfer function
 * `H(s) = K·s⁴ / ((s+ω₁)²·(s+ω₂)·(s+ω₃)·(s+ω₄)²)` with the bilinear transform, pre-warping each pole so its
 * frequency is preserved, and normalised to 0 dB at 1 kHz. Implemented as three cascaded biquads in double
 * precision. Not thread-safe: one instance per audio stream.
 */
class AWeightingFilter(
    private val sampleRate: Int,
) {
    private val sections: List<Biquad>
    private val gain: Double

    init {
        require(sampleRate >= MIN_SAMPLE_RATE) { "Sample rate must be at least $MIN_SAMPLE_RATE Hz, was $sampleRate" }
        val fs2 = 2.0 * sampleRate
        val p1 = prewarp(F1_HZ, sampleRate)
        val p2 = prewarp(F2_HZ, sampleRate)
        val p3 = prewarp(F3_HZ, sampleRate)
        val p4 = prewarp(F4_HZ, sampleRate)
        sections =
            listOf(
                Biquad.product(highPass(p1, fs2), highPass(p1, fs2)),
                Biquad.product(highPass(p2, fs2), highPass(p3, fs2)),
                Biquad.product(lowPass(p4, fs2), lowPass(p4, fs2)),
            )
        gain = 1.0 / sections.fold(1.0) { acc, section -> acc * section.magnitudeAt(NORMALISATION_HZ, sampleRate) }
    }

    /** Filters one sample. */
    fun process(sample: Double): Double = gain * sections.fold(sample) { x, section -> section.process(x) }

    /** Theoretical response of this digital filter at [frequencyHz], in dB (0 dB at 1 kHz). */
    fun responseDb(frequencyHz: Double): Double =
        DECIBELS_PER_DECADE_AMPLITUDE *
            log10(gain * sections.fold(1.0) { acc, section -> acc * section.magnitudeAt(frequencyHz, sampleRate) })

    private companion object {
        // IEC 61672-1 A-weighting pole frequencies f₁…f₄.
        const val F1_HZ = 20.598997
        const val F2_HZ = 107.65265
        const val F3_HZ = 737.86223
        const val F4_HZ = 12194.217
        const val NORMALISATION_HZ = 1000.0
        const val MIN_SAMPLE_RATE = 8000
        const val DECIBELS_PER_DECADE_AMPLITUDE = 20.0

        /** Analog pole at [frequencyHz], pre-warped so the bilinear transform keeps its frequency. */
        fun prewarp(
            frequencyHz: Double,
            sampleRate: Int,
        ): Double = 2.0 * sampleRate * tan(PI * frequencyHz / sampleRate)

        /** Bilinear transform of `s / (s + p)`: zero at z = 1. */
        fun highPass(
            p: Double,
            fs2: Double,
        ) = FirstOrder(n0 = fs2, n1 = -fs2, d0 = fs2 + p, d1 = p - fs2)

        /** Bilinear transform of `1 / (s + p)`: zero at z = −1. */
        fun lowPass(
            p: Double,
            fs2: Double,
        ) = FirstOrder(n0 = 1.0, n1 = 1.0, d0 = fs2 + p, d1 = p - fs2)
    }
}

/** First-order section `(n0 + n1·z⁻¹) / (d0 + d1·z⁻¹)`. */
internal data class FirstOrder(
    val n0: Double,
    val n1: Double,
    val d0: Double,
    val d1: Double,
)

/** Second-order IIR section in transposed direct form II, coefficients normalised so a0 = 1. */
internal class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    /** |H(e^{jω})| at [frequencyHz]. */
    fun magnitudeAt(
        frequencyHz: Double,
        sampleRate: Int,
    ): Double {
        val w = 2 * PI * frequencyHz / sampleRate
        val numRe = b0 + b1 * cos(w) + b2 * cos(2 * w)
        val numIm = -(b1 * sin(w) + b2 * sin(2 * w))
        val denRe = 1.0 + a1 * cos(w) + a2 * cos(2 * w)
        val denIm = -(a1 * sin(w) + a2 * sin(2 * w))
        return sqrt((numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm))
    }

    companion object {
        /** Biquad equal to the product of two first-order sections. */
        fun product(
            a: FirstOrder,
            b: FirstOrder,
        ): Biquad {
            val a0 = a.d0 * b.d0
            return Biquad(
                b0 = a.n0 * b.n0 / a0,
                b1 = (a.n0 * b.n1 + a.n1 * b.n0) / a0,
                b2 = a.n1 * b.n1 / a0,
                a1 = (a.d0 * b.d1 + a.d1 * b.d0) / a0,
                a2 = a.d1 * b.d1 / a0,
            )
        }
    }
}
