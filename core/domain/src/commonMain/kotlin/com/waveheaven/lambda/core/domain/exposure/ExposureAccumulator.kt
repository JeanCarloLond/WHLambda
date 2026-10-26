package com.waveheaven.lambda.core.domain.exposure

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Folds one-second listening observations into [ExposureMinute]s. Only aggregates leave this class: levels are
 * accumulated as energy and nothing else is kept (ADR-0007). Not thread-safe.
 */
public class ExposureAccumulator {
    private var minute: Instant? = null
    private var listening: Duration = Duration.ZERO
    private var outputEnergy = 0.0
    private var ambientEnergy = 0.0
    private var ambientTime: Duration = Duration.ZERO
    private var uncertaintyDb = 0.0

    /**
     * Records [duration] of listening at [output] ending at [now]. Returns the previous minute once [now] falls in a
     * new one, so the caller can store it; otherwise `null`.
     */
    public fun add(
        now: Instant,
        output: SoundLevel,
        outputUncertaintyDb: Double,
        ambient: SoundLevel?,
        duration: Duration = 1.seconds,
    ): ExposureMinute? {
        require(duration.isPositive()) { "duration must be positive" }
        val current = now.truncatedToMinute()
        val completed = if (minute != null && minute != current) flush() else null
        minute = current
        listening += duration
        outputEnergy += output.meanSquarePressure * duration.inWholeMilliseconds
        ambient?.let {
            ambientEnergy += it.meanSquarePressure * duration.inWholeMilliseconds
            ambientTime += duration
        }
        uncertaintyDb = maxOf(uncertaintyDb, outputUncertaintyDb)
        return completed
    }

    /** Returns the minute in progress, if any, and starts afresh. Call it when listening stops. */
    public fun flush(): ExposureMinute? {
        val start = minute ?: return null
        val result =
            ExposureMinute(
                start = start,
                listening = minOf(listening, ExposureMinute.MINUTE),
                output = SoundLevel.fromMeanSquarePressure(outputEnergy / listening.inWholeMilliseconds),
                outputUncertaintyDb = uncertaintyDb,
                ambient =
                    if (ambientTime.isPositive()) {
                        SoundLevel.fromMeanSquarePressure(ambientEnergy / ambientTime.inWholeMilliseconds)
                    } else {
                        null
                    },
            )
        minute = null
        listening = Duration.ZERO
        outputEnergy = 0.0
        ambientEnergy = 0.0
        ambientTime = Duration.ZERO
        uncertaintyDb = 0.0
        return result
    }
}
