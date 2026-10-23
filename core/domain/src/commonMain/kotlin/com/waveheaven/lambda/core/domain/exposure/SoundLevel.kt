package com.waveheaven.lambda.core.domain.exposure

import kotlin.jvm.JvmInline
import kotlin.math.log10
import kotlin.math.pow

/**
 * An A-weighted sound pressure level, in dB(A) SPL (re 20 µPa).
 *
 * This is a physical level at the ear, not a raw microphone reading in dBFS:
 * converting one into the other requires a per-device calibration offset.
 */
@JvmInline
public value class SoundLevel(
    public val decibels: Double,
) : Comparable<SoundLevel> {
    init {
        require(decibels.isFinite()) { "Sound level must be finite, was $decibels" }
    }

    /** Mean-square sound pressure p² in Pa², the quantity that adds up into a dose. */
    public val meanSquarePressure: Double
        get() = REFERENCE_PRESSURE_PA.pow(2) * 10.0.pow(decibels / DECIBELS_PER_DECADE)

    override fun compareTo(other: SoundLevel): Int = decibels.compareTo(other.decibels)

    override fun toString(): String = "$decibels dB(A)"

    public companion object {
        /** Reference sound pressure for 0 dB SPL, 20 µPa. */
        public const val REFERENCE_PRESSURE_PA: Double = 20e-6

        private const val DECIBELS_PER_DECADE = 10.0

        /** Inverse of [meanSquarePressure]. */
        public fun fromMeanSquarePressure(pascalsSquared: Double): SoundLevel {
            require(pascalsSquared > 0.0) { "Mean-square pressure must be positive, was $pascalsSquared" }
            return SoundLevel(DECIBELS_PER_DECADE * log10(pascalsSquared / REFERENCE_PRESSURE_PA.pow(2)))
        }
    }
}

/** Convenience constructor: `85.dBA`. */
public val Number.dBA: SoundLevel
    get() = SoundLevel(toDouble())
