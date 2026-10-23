package com.waveheaven.lambda.core.domain.exposure

import kotlin.time.Duration
import kotlin.time.DurationUnit

/** A stretch of time during which the ear was exposed to a steady [level]. */
public data class ExposureSegment(
    val level: SoundLevel,
    val duration: Duration,
) {
    init {
        require(!duration.isNegative() && duration.isFinite()) { "Duration must be finite and non-negative, was $duration" }
    }

    /** Sound exposure E = p² · t, in Pa²·h (the unit used by WHO/ITU-T H.870). */
    val soundExposure: Double
        get() = level.meanSquarePressure * duration.toDouble(DurationUnit.HOURS)
}

/** Total sound exposure of [segments], in Pa²·h. */
public fun soundExposure(segments: Iterable<ExposureSegment>): Double = segments.sumOf { it.soundExposure }

/**
 * Equivalent continuous level (Leq): the steady level that would deliver the same acoustic
 * energy as [segments] over their total duration.
 */
public fun equivalentLevel(segments: Iterable<ExposureSegment>): SoundLevel {
    val totalHours = segments.sumOf { it.duration.toDouble(DurationUnit.HOURS) }
    require(totalHours > 0.0) { "Leq needs a non-zero total duration" }
    return SoundLevel.fromMeanSquarePressure(soundExposure(segments) / totalHours)
}
