package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.SoundLevel

/**
 * Maps a media-volume index (Android `STREAM_MUSIC`, a desktop app's volume…) to the estimated
 * A-weighted level at the ear for the current output route.
 *
 * Implementations must be strictly increasing over [indices].
 */
public interface OutputLevelModel {
    /** Valid volume indices, e.g. `0..15`. Index 0 is mute on Android. */
    public val indices: IntRange

    /** ± margin of every level this model returns, in dB. */
    public val uncertaintyDb: Double

    public fun levelAt(index: Int): SoundLevel
}

/**
 * Constant decibels per step, anchored at the level produced at maximum volume.
 *
 * A simple model for tests and for platforms with evenly spaced steps (e.g. the desktop, ADR-0009). Spike #2
 * measured ≈1.9 dB/step near the top of the range on a Redmi Note 14; on Android prefer [CurveOutputLevelModel].
 */
public class LinearOutputLevelModel(
    public val maxIndex: Int,
    public val levelAtMax: SoundLevel,
    public val decibelsPerStep: Double,
    override val uncertaintyDb: Double = 0.0,
) : OutputLevelModel {
    init {
        require(maxIndex > 0) { "maxIndex must be positive, was $maxIndex" }
        require(decibelsPerStep > 0.0 && decibelsPerStep.isFinite()) { "decibelsPerStep must be positive, was $decibelsPerStep" }
        require(uncertaintyDb >= 0.0 && uncertaintyDb.isFinite()) { "uncertaintyDb must be non-negative" }
    }

    override val indices: IntRange = 0..maxIndex

    override fun levelAt(index: Int): SoundLevel {
        require(index in indices) { "Index $index outside $indices" }
        return SoundLevel(levelAtMax.decibels - (maxIndex - index) * decibelsPerStep)
    }
}

/** Port: the [OutputLevelModel] of each output route, since every output has its own curve and loudness. */
public fun interface OutputLevelModels {
    public fun modelFor(route: OutputRoute): OutputLevelModel
}
