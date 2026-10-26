package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.SoundLevel

/**
 * Level at the ear = the profile's maximum output + the attenuation the platform applies at each volume index.
 *
 * On Android the attenuations come from `AudioManager.getStreamVolumeDb` for the current output, which follows the
 * device's own volume curve. When that curve is not usable (e.g. Bluetooth absolute volume, where the headphones
 * attenuate instead of the phone), pass [VolumeCurves.androidDefaultMedia] and [VolumeCurves.DEFAULT_CURVE_UNCERTAINTY_DB].
 *
 * @param attenuationDb attenuation per index, in dB relative to the maximum; index 0 is usually mute. Values are
 *   normalised so the last one is 0 dB and floored at [MUTE_FLOOR_DB].
 * @param curveUncertaintyDb extra ± margin from the curve itself, added to the profile's.
 */
public class CurveOutputLevelModel(
    public val profile: HeadphoneProfile,
    attenuationDb: List<Double>,
    curveUncertaintyDb: Double = 0.0,
) : OutputLevelModel {
    private val attenuation: List<Double>

    init {
        require(attenuationDb.size >= 2) { "A volume curve needs at least two indices" }
        require(attenuationDb.all { !it.isNaN() }) { "Attenuations must be numbers" }
        require(curveUncertaintyDb >= 0.0 && curveUncertaintyDb.isFinite()) { "curveUncertaintyDb must be non-negative" }
        val top = attenuationDb.last()
        require(top.isFinite()) { "The maximum index must have a finite attenuation" }
        attenuation = attenuationDb.map { (it - top).coerceAtLeast(MUTE_FLOOR_DB) }
        require(isStrictlyIncreasing(attenuation)) { "Attenuation must increase strictly with the index: $attenuationDb" }
    }

    override val indices: IntRange = attenuation.indices

    override val uncertaintyDb: Double = profile.uncertaintyDb + curveUncertaintyDb

    override fun levelAt(index: Int): SoundLevel {
        require(index in indices) { "Index $index outside $indices" }
        return SoundLevel(profile.maxOutput.decibels + attenuation[index])
    }

    public companion object {
        /** Attenuations below this are treated as silence; Android reports mute as a huge negative number. */
        public const val MUTE_FLOOR_DB: Double = -96.0

        /** Whether [attenuationDb] can serve as a volume curve (after the same normalisation as the constructor). */
        public fun isUsableCurve(attenuationDb: List<Double>): Boolean {
            if (attenuationDb.size < 2 || attenuationDb.any { it.isNaN() } || !attenuationDb.last().isFinite()) return false
            val top = attenuationDb.last()
            return isStrictlyIncreasing(attenuationDb.map { (it - top).coerceAtLeast(MUTE_FLOOR_DB) })
        }

        private fun isStrictlyIncreasing(values: List<Double>) = values.zipWithNext().all { (a, b) -> b > a }
    }
}

/** Reference volume curves for when the platform cannot tell us its own. */
public object VolumeCurves {
    /**
     * Extra ± margin when a reference curve stands in for the device's own one. Device curves we have seen differ
     * from the reference by a few decibels in the middle of the range.
     */
    public const val DEFAULT_CURVE_UNCERTAINTY_DB: Double = 3.0

    /**
     * Android's default media curve for headsets (AOSP `DEFAULT_MEDIA_VOLUME_CURVE`): −58 dB at 1 % of the range,
     * −40 dB at 20 %, −17 dB at 60 % and 0 dB at the top, linear in decibels in between. Index 0 is mute.
     */
    public fun androidDefaultMedia(maxIndex: Int): List<Double> {
        require(maxIndex >= 1) { "maxIndex must be at least 1" }
        return List(maxIndex + 1) { index ->
            if (index == 0) CurveOutputLevelModel.MUTE_FLOOR_DB else interpolate(ANDROID_MEDIA_POINTS, PERCENT * index / maxIndex)
        }
    }

    private fun interpolate(
        points: List<Pair<Double, Double>>,
        percent: Double,
    ): Double {
        if (percent <= points.first().first) return points.first().second
        val (upper, i) = points.withIndex().first { it.value.first >= percent }.let { it.value to it.index }
        val lower = points[i - 1]
        val t = (percent - lower.first) / (upper.first - lower.first)
        return lower.second + t * (upper.second - lower.second)
    }

    private const val PERCENT = 100.0

    /** (percent of the range, attenuation in dB). */
    private val ANDROID_MEDIA_POINTS = listOf(1.0 to -58.0, 20.0 to -40.0, 60.0 to -17.0, 100.0 to 0.0)
}
