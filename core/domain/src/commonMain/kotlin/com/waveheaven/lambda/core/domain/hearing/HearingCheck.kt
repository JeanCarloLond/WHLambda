package com.waveheaven.lambda.core.domain.hearing

/** Which ear a tone plays in. */
public enum class Ear { RIGHT, LEFT }

/** One tone to play: [levelDb] is relative to the comfortable reference level the user set (0 dB, never exceeded). */
public data class Presentation(
    val ear: Ear,
    val frequencyHz: Int,
    val levelDb: Double,
)

/**
 * A quick hearing check (WH-220), not a diagnosis: per ear, at [FREQUENCIES_HZ], an adaptive staircase in the style of
 * the modified Hughson-Westlake method finds the quietest tone the user still hears.
 *
 * - Start [START_DB] below the reference. Heard → [STEP_DOWN_DB] quieter; missed → [STEP_UP_DB] louder.
 * - The threshold is the lowest level heard on [ASCENDING_HITS_NEEDED] ascending presentations (ones that follow a
 *   miss), the classic "2 out of 3".
 * - Levels never go above the reference (0 dB), so a tone is never louder than what the user chose as comfortable.
 *   Missing it even there records no threshold ([HearingResult.NOT_HEARD]).
 * - A frequency gives up after [MAX_PRESENTATIONS] tones and keeps the lowest level heard, so the check stays short.
 *
 * Thresholds are relative: the device and headphones are not calibrated, so only differences between ears and
 * frequencies mean something. Not thread-safe.
 */
public class HearingCheck {
    private val order: List<Pair<Ear, Int>> = Ear.entries.flatMap { ear -> TEST_ORDER_HZ.map { ear to it } }
    private var position = 0
    private val thresholds = mutableMapOf<Ear, MutableMap<Int, Double?>>()
    private var staircase = Staircase()

    /** The tone to play now, or `null` once every ear and frequency is done. */
    public fun next(): Presentation? = order.getOrNull(position)?.let { (ear, frequency) -> Presentation(ear, frequency, staircase.level) }

    /** How far along the check is, 0.0 to 1.0. */
    public val progress: Double
        get() = position.toDouble() / order.size

    /** Records whether the user heard the tone from [next]. */
    public fun answer(heard: Boolean) {
        val (ear, frequency) = order.getOrNull(position) ?: error("The check is already finished")
        staircase.answer(heard)?.let { threshold ->
            thresholds.getOrPut(ear) { mutableMapOf() }[frequency] = threshold.takeUnless { it == NO_THRESHOLD }
            position++
            staircase = Staircase()
        }
    }

    /** The thresholds found so far; complete once [next] returns `null`. */
    public fun result(): HearingResult = HearingResult(thresholds.mapValues { (_, byFrequency) -> byFrequency.toMap() })

    /** One frequency in one ear. */
    private class Staircase {
        var level: Double = START_DB
            private set
        private var presentations = 0
        private var lastWasMiss = false
        private val ascendingHits = mutableMapOf<Double, Int>()
        private var lowestHeard: Double? = null

        /** Returns the threshold once found ([NO_THRESHOLD] if never heard), `null` while still searching. */
        fun answer(heard: Boolean): Double? {
            presentations++
            val found = if (heard) onHeard() else onMissed()
            return found ?: if (presentations >= MAX_PRESENTATIONS) lowestHeard ?: NO_THRESHOLD else null
        }

        private fun onHeard(): Double? {
            lowestHeard = minOf(lowestHeard ?: level, level)
            val threshold =
                if (lastWasMiss) {
                    val hits = (ascendingHits[level] ?: 0) + 1
                    ascendingHits[level] = hits
                    level.takeIf { hits >= ASCENDING_HITS_NEEDED }
                } else {
                    null
                }
            lastWasMiss = false
            level = (level - STEP_DOWN_DB).coerceAtLeast(FLOOR_DB)
            return threshold
        }

        private fun onMissed(): Double? {
            if (level >= CEILING_DB) return NO_THRESHOLD
            lastWasMiss = true
            level = (level + STEP_UP_DB).coerceAtMost(CEILING_DB)
            return null
        }
    }

    public companion object {
        /** Six to eight frequencies from 250 Hz to 8 kHz (WH-220). */
        public val FREQUENCIES_HZ: List<Int> = listOf(250, 500, 1000, 2000, 3000, 4000, 6000, 8000)

        /** Clinical habit: 1 kHz first, then up, then down. */
        private val TEST_ORDER_HZ = listOf(1000, 2000, 3000, 4000, 6000, 8000, 500, 250)

        public const val START_DB: Double = -30.0
        public const val STEP_DOWN_DB: Double = 10.0
        public const val STEP_UP_DB: Double = 5.0
        public const val CEILING_DB: Double = 0.0
        public const val FLOOR_DB: Double = -80.0
        public const val ASCENDING_HITS_NEEDED: Int = 2
        public const val MAX_PRESENTATIONS: Int = 14

        private const val NO_THRESHOLD = Double.NEGATIVE_INFINITY
    }
}
