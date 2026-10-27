package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tuning of [VolumeController]. Defaults follow ADR-0006 and the measurements of spike #2;
 * listening profiles (`ListeningProfile.applyTo`) override the comfort parameters, never the safety ones.
 */
public data class VolumeControllerConfig(
    /** Exposure standard whose remaining budget caps the volume. */
    val standard: DoseStandard = DoseStandard.WHO_ADULT,
    /** Desired margin of the music over the ambient noise. */
    val targetSnrDb: Double = 10.0,
    /** Comfort changes happen only when the target is at least this far from the current level. */
    val hysteresisDb: Double = 3.0,
    /** Number of 1 s ambient readings averaged (energy mean) before deciding. */
    val ambientWindowTicks: Int = 3,
    /** After we set the volume, readings during this time are adopted as the result (Bluetooth may round it). */
    val settleTime: Duration = 600.milliseconds,
    /** Minimum time between two comfort raises. Raising is deliberately slower than lowering. */
    val raiseInterval: Duration = 3.seconds,
    /** Minimum time between two comfort reductions. */
    val lowerInterval: Duration = 1.seconds,
    /** How long automatic adjustments pause after someone else changes the volume. */
    val manualOverrideDuration: Duration = 10.minutes,
    /** The ceiling assumes the user keeps listening for this long at the chosen level. */
    val planningHorizon: Duration = 1.hours,
    /**
     * The ceiling never drops below this level, so the music never becomes inaudible because the budget
     * ran out. At 70 dB(A) NIOSH allows 256 h a day and WHO 400 h a week, so the extra dose is negligible.
     */
    val minimumCeiling: SoundLevel = SoundLevel(DEFAULT_MINIMUM_CEILING_DBA),
    /** Lowest index the controller will choose; it never mutes. */
    val minIndex: Int = 1,
    /**
     * Highest index the controller will choose, from the listening profile (#14); `null` for the top of the range.
     * It can only lower the dose ceiling, never raise it.
     */
    val maxIndex: Int? = null,
) {
    init {
        require(targetSnrDb.isFinite()) { "targetSnrDb must be finite" }
        require(hysteresisDb >= 0.0 && hysteresisDb.isFinite()) { "hysteresisDb must be non-negative" }
        require(ambientWindowTicks >= 1) { "ambientWindowTicks must be at least 1" }
        require(!settleTime.isNegative()) { "settleTime must not be negative" }
        require(!raiseInterval.isNegative() && !lowerInterval.isNegative()) { "Intervals must not be negative" }
        require(!manualOverrideDuration.isNegative()) { "manualOverrideDuration must not be negative" }
        require(planningHorizon.isPositive() && planningHorizon.isFinite()) { "planningHorizon must be positive" }
        require(minIndex >= 0) { "minIndex must not be negative" }
        require(maxIndex == null || maxIndex >= minIndex) { "maxIndex must not be below minIndex" }
    }

    private companion object {
        const val DEFAULT_MINIMUM_CEILING_DBA = 70.0
    }
}
