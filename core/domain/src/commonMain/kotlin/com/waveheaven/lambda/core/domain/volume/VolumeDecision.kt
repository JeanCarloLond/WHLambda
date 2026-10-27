package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.SoundLevel

/** What [VolumeController] decided for one [VolumeTick], plus the context the UI needs to explain it. */
public data class VolumeDecision(
    /** Index to set, or `null` to leave the volume as it is. */
    val newIndex: Int?,
    val reason: VolumeDecisionReason,
    /** Energy-averaged ambient level, once enough readings exist. */
    val ambientLeq: SoundLevel?,
    /** Level the music should reach for the configured margin over the noise, when computed. */
    val targetLevel: SoundLevel?,
    /** Highest level allowed by the remaining dose. */
    val ceilingLevel: SoundLevel,
    /** The dose ceiling prevents reaching [targetLevel]; the UI should suggest noise-cancelling or a break. */
    val isTargetCappedByDose: Boolean = false,
    /** The current volume is above the dose ceiling (e.g. set manually); the UI should warn. */
    val isAboveDoseCeiling: Boolean = false,
)

public enum class VolumeDecisionReason {
    /** Nothing is playing: no adjustment (and the meter may pause). */
    NO_MUSIC,

    /** The volume is below the controller's range (muted by the user): never unmute automatically. */
    MUTED,

    /** Waiting for enough ambient readings. */
    WARMING_UP,

    /** Our last change may still be rounded by the device; adopting the observed index. */
    SETTLING,

    /** Someone else changed the volume recently; automatic adjustments are paused. */
    MANUAL_OVERRIDE,

    /** Safety: the volume exceeded what the remaining dose allows and was lowered at once. */
    LOWER_TO_DOSE_CEILING,

    /** The volume exceeded the listening profile's maximum and was lowered to it. */
    LOWER_TO_PROFILE_LIMIT,

    /** Comfort: noise rose, so the volume goes up one step. */
    RAISE_FOR_NOISE,

    /** Comfort: noise fell, so the volume goes down one step. */
    LOWER_FOR_QUIET,

    /** The target is above what the remaining dose allows; holding at the ceiling. */
    AT_DOSE_CEILING,

    /** The volume is close enough to the target. */
    WITHIN_DEAD_BAND,

    /** A comfort change is due but the previous one was too recent. */
    RATE_LIMITED,
}
