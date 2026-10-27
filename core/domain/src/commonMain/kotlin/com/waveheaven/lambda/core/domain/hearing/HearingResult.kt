package com.waveheaven.lambda.core.domain.hearing

import com.waveheaven.lambda.core.domain.profile.EqCurve
import com.waveheaven.lambda.core.domain.profile.EqPoint
import kotlin.math.abs
import kotlin.time.Instant

/**
 * Relative hearing thresholds per ear and frequency, in dB below the user's comfortable reference (more negative is
 * better hearing). [NOT_HEARD] marks a frequency not heard even at the reference level. Health data: kept on the
 * device only (WH-222, ADR-0007).
 */
public data class HearingResult(
    val thresholds: Map<Ear, Map<Int, Double?>>,
) {
    public fun threshold(
        ear: Ear,
        frequencyHz: Int,
    ): Double? = thresholds[ear]?.get(frequencyHz)

    /**
     * Whether to advise seeing an audiologist (WH-221). Thresholds are relative, so the check looks for patterns, not
     * absolute loss:
     * - [Assessment.ASYMMETRIC]: the ears differ by [ASYMMETRY_DB] or more at two or more frequencies, a common
     *   referral criterion;
     * - [Assessment.UNEVEN]: some frequencies (at least two, or any not heard at all) are [DIP_DB] or more worse than
     *   the user's own best, as a noise notch around 4 kHz would be;
     * - otherwise [Assessment.EVEN].
     */
    public fun assessment(): Assessment {
        val asymmetric =
            HearingCheck.FREQUENCIES_HZ.count { f ->
                val right = threshold(Ear.RIGHT, f)
                val left = threshold(Ear.LEFT, f)
                (right == null) != (left == null) || (right != null && left != null && abs(right - left) >= ASYMMETRY_DB)
            } >= MIN_FREQUENCIES
        val all = thresholds.values.flatMap { it.values }
        val heard = all.filterNotNull()
        val best = heard.minOrNull() ?: 0.0
        val dips = heard.count { it - best >= DIP_DB }
        return when {
            asymmetric -> Assessment.ASYMMETRIC
            all.any { it == null } || dips >= MIN_FREQUENCIES -> Assessment.UNEVEN
            else -> Assessment.EVEN
        }
    }

    /**
     * An EQ curve that gently lifts the frequencies heard less well (WH-222): half of the gap to the user's best
     * threshold (the "half-gain" rule of thumb), averaged over both ears, never more than [MAX_BOOST_DB].
     */
    public fun compensationCurve(): EqCurve {
        val known = thresholds.values.flatMap { it.values }.filterNotNull()
        val best = known.minOrNull() ?: 0.0
        return EqCurve(
            HearingCheck.FREQUENCIES_HZ.map { f ->
                val values = Ear.entries.map { threshold(it, f) ?: HearingCheck.CEILING_DB }
                val gap = values.average() - best
                EqPoint(f.toDouble(), (gap * HALF_GAIN).coerceIn(0.0, MAX_BOOST_DB))
            },
        )
    }

    public enum class Assessment {
        /** No pattern worth acting on. */
        EVEN,

        /** One ear hears clearly worse: see an audiologist. */
        ASYMMETRIC,

        /** Some frequencies are much harder to hear: see an audiologist. */
        UNEVEN,
    }

    public companion object {
        public val NOT_HEARD: Double? = null
        public const val ASYMMETRY_DB: Double = 15.0
        public const val DIP_DB: Double = 25.0
        public const val MAX_BOOST_DB: Double = 12.0
        private const val HALF_GAIN = 0.5
        private const val MIN_FREQUENCIES = 2
    }
}

/** A finished check, as stored. */
public data class StoredHearingCheck(
    val takenAt: Instant,
    val result: HearingResult,
)

/** Port: hearing-check results on the device only (WH-222). */
public interface HearingCheckStore {
    public suspend fun save(check: StoredHearingCheck)

    public suspend fun latest(): StoredHearingCheck?

    public suspend fun deleteAll()
}

/** Port: plays one pure tone in one ear. */
public interface TonePlayer {
    /**
     * Plays [frequencyHz] in [ear] for [durationMillis], [levelDb] relative to full scale of the reference
     * (`referenceDbfs + levelDb`), with short fades so it does not click. Returns when the tone has ended.
     */
    public suspend fun play(
        ear: Ear?,
        frequencyHz: Int,
        levelDb: Double,
        durationMillis: Long,
    )

    public companion object {
        /** The comfortable reference tone sits this far below digital full scale, so levels stay headroom-safe. */
        public const val REFERENCE_DBFS: Double = -20.0
    }
}
