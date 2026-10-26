package com.waveheaven.lambda.core.domain.exposure

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Listening aggregated over one minute: the only exposure data the app keeps (ADR-0007).
 *
 * @property start beginning of the minute, truncated to whole minutes since the epoch.
 * @property listening how long media played during that minute, at most one minute.
 * @property output energy-averaged level estimated at the ear while listening.
 * @property outputUncertaintyDb ± margin of [output], so the UI can state it (#11).
 * @property ambient energy-averaged ambient level, when it was measured.
 */
public data class ExposureMinute(
    val start: Instant,
    val listening: Duration,
    val output: SoundLevel,
    val outputUncertaintyDb: Double,
    val ambient: SoundLevel?,
) {
    init {
        require(start == start.truncatedToMinute()) { "start must be a whole minute, was $start" }
        require(listening.isPositive() && listening <= MINUTE) { "listening must be within (0, 1 min], was $listening" }
        require(outputUncertaintyDb >= 0.0 && outputUncertaintyDb.isFinite()) { "uncertainty must be non-negative" }
    }

    /** This minute as a dose segment: the output level held for the listening time. */
    public fun toSegment(): ExposureSegment = ExposureSegment(output, listening)

    /**
     * Combines two records of the same minute (e.g. the service restarted within it): times add up and levels are
     * energy-averaged, weighted by listening time. The total is capped at one minute.
     */
    public fun mergedWith(other: ExposureMinute): ExposureMinute {
        require(other.start == start) { "Only records of the same minute can be merged" }
        val segments = listOf(toSegment(), other.toSegment())
        val ambientSegments =
            listOfNotNull(
                ambient?.let { ExposureSegment(it, listening) },
                other.ambient?.let { ExposureSegment(it, other.listening) },
            )
        return ExposureMinute(
            start = start,
            listening = minOf(listening + other.listening, MINUTE),
            output = equivalentLevel(segments),
            outputUncertaintyDb = maxOf(outputUncertaintyDb, other.outputUncertaintyDb),
            ambient = if (ambientSegments.isEmpty()) null else equivalentLevel(ambientSegments),
        )
    }

    public companion object {
        /** Length of one aggregate. */
        public val MINUTE: Duration = 1.minutes

        /** Aggregates older than this are deleted (data minimisation, WH-044). */
        public val RETENTION: Duration = 30.days
    }
}

/** This instant rounded down to the start of its minute. */
public fun Instant.truncatedToMinute(): Instant = Instant.fromEpochSeconds(epochSeconds.floorDiv(SECONDS_PER_MINUTE) * SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60L
