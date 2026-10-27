package com.waveheaven.lambda.core.domain.profile

import com.waveheaven.lambda.core.domain.volume.VolumeControllerConfig
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/**
 * How the app behaves in one kind of place (WH-060): how far above the noise the music should sit and within which
 * part of the volume range the app may move it. Profiles tune comfort only; the dose ceiling applies on top of any
 * profile and a profile can only lower it (WH-063).
 *
 * @property targetSnrDb desired margin of the music over the ambient noise, in [SNR_RANGE_DB].
 * @property minVolume lowest share of the volume range the app may choose, 0.0 to 1.0.
 * @property maxVolume highest share of the volume range the app may choose, above [minVolume].
 * @property eq optional equaliser curve stored with the profile (WH-064).
 */
public data class ListeningProfile(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean,
    val targetSnrDb: Double,
    val minVolume: Double,
    val maxVolume: Double,
    val eq: EqCurve? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(targetSnrDb in SNR_RANGE_DB) { "targetSnrDb must be in $SNR_RANGE_DB, was $targetSnrDb" }
        require(minVolume in 0.0..1.0 && maxVolume in 0.0..1.0) { "Volumes are shares of the range, 0.0 to 1.0" }
        require(maxVolume > minVolume) { "maxVolume must be above minVolume" }
    }

    /**
     * [base] with this profile's comfort settings for a volume range of [indices]. The minimum never goes below
     * index 1 (the controller never mutes) and the maximum never below the minimum.
     */
    public fun applyTo(
        base: VolumeControllerConfig,
        indices: IntRange,
    ): VolumeControllerConfig {
        val steps = indices.last - indices.first
        val minIndex = maxOf(indices.first + ceil(minVolume * steps).toInt(), indices.first + 1).coerceAtMost(indices.last)
        val maxIndex = maxOf(indices.first + floor(maxVolume * steps).toInt(), minIndex)
        return base.copy(targetSnrDb = targetSnrDb, minIndex = minIndex, maxIndex = maxIndex)
    }

    public companion object {
        /** Margins we let users choose: from "just audible" to "well above the noise". */
        public val SNR_RANGE_DB: ClosedFloatingPointRange<Double> = 0.0..20.0

        public val COMMUTE: ListeningProfile = builtIn("commute", "Commute", targetSnrDb = 10.0, minVolume = 0.1, maxVolume = 1.0)
        public val GYM: ListeningProfile = builtIn("gym", "Gym", targetSnrDb = 12.0, minVolume = 0.1, maxVolume = 1.0)
        public val OFFICE: ListeningProfile = builtIn("office", "Office", targetSnrDb = 6.0, minVolume = 0.05, maxVolume = 0.6)
        public val HOME: ListeningProfile = builtIn("home", "Home", targetSnrDb = 8.0, minVolume = 0.05, maxVolume = 0.7)

        /** The four built-in profiles, in display order. They cannot be edited or deleted. */
        public val BUILT_IN: List<ListeningProfile> = listOf(COMMUTE, GYM, OFFICE, HOME)

        /** Active until the user picks another one. */
        public val DEFAULT: ListeningProfile = COMMUTE

        private fun builtIn(
            id: String,
            name: String,
            targetSnrDb: Double,
            minVolume: Double,
            maxVolume: Double,
        ) = ListeningProfile(id, name, isBuiltIn = true, targetSnrDb, minVolume, maxVolume)
    }
}

/** One point of an [EqCurve]: a gain at a frequency. */
public data class EqPoint(
    val frequencyHz: Double,
    val gainDb: Double,
)

/**
 * The single equaliser model used everywhere (profiles, hearing check): gains at frequencies, interpolated linearly
 * on a logarithmic frequency axis and held flat beyond the first and last points.
 */
public data class EqCurve(
    val points: List<EqPoint>,
) {
    init {
        require(points.isNotEmpty()) { "An EQ curve needs at least one point" }
        require(points.all { it.frequencyHz in AUDIBLE_HZ }) { "Frequencies must be within $AUDIBLE_HZ Hz" }
        require(points.all { it.gainDb in GAIN_RANGE_DB }) { "Gains must be within $GAIN_RANGE_DB dB" }
        require(points.zipWithNext().all { (a, b) -> b.frequencyHz > a.frequencyHz }) { "Frequencies must increase strictly" }
    }

    /** Gain at [frequencyHz]. */
    public fun gainAt(frequencyHz: Double): Double {
        require(frequencyHz > 0.0) { "frequency must be positive" }
        val upper = points.indexOfFirst { it.frequencyHz >= frequencyHz }
        return when (upper) {
            -1 -> {
                points.last().gainDb
            }

            0 -> {
                points.first().gainDb
            }

            else -> {
                val a = points[upper - 1]
                val b = points[upper]
                a.gainDb + (b.gainDb - a.gainDb) * ln(frequencyHz / a.frequencyHz) / ln(b.frequencyHz / a.frequencyHz)
            }
        }
    }

    public companion object {
        public val AUDIBLE_HZ: ClosedFloatingPointRange<Double> = 20.0..20_000.0
        public val GAIN_RANGE_DB: ClosedFloatingPointRange<Double> = -24.0..24.0
    }
}
