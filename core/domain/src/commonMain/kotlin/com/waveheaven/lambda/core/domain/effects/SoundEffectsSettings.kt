package com.waveheaven.lambda.core.domain.effects

import com.waveheaven.lambda.core.domain.profile.EqCurve
import kotlin.math.max
import kotlin.math.pow

/**
 * The ten bands of the equaliser (WH-090), one per octave from 31 Hz to 16 kHz, as on most graphic equalisers.
 * Any [EqCurve] (such as the hearing-check compensation) is sampled at these centres.
 */
public object EqBands {
    public val CENTERS_HZ: List<Double> = listOf(31.0, 62.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0)

    /** What the user can set per band. */
    public val GAIN_RANGE_DB: ClosedFloatingPointRange<Double> = -12.0..12.0

    public val COUNT: Int = CENTERS_HZ.size

    /** [curve] at every band centre. */
    public fun sample(curve: EqCurve): List<Double> = CENTERS_HZ.map(curve::gainAt)

    /** Ten zeros. */
    public val FLAT: List<Double> = List(COUNT) { 0.0 }
}

/** Ready-made equaliser curves (WH-090), in display order. [CUSTOM] uses the user's own gains. */
public enum class EqPreset(
    gains: List<Double>?,
) {
    FLAT(EqBands.FLAT),
    BASS(listOf(6.0, 5.0, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
    TREBLE(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 4.0, 5.0, 6.0)),
    VOCAL(listOf(-2.0, -2.0, -1.0, 0.0, 2.0, 4.0, 4.0, 2.0, 0.0, -1.0)),
    ROCK(listOf(5.0, 4.0, 3.0, 1.0, -1.0, -1.0, 1.0, 3.0, 4.0, 5.0)),
    POP(listOf(-1.0, 0.0, 2.0, 3.0, 4.0, 3.0, 1.0, 0.0, -1.0, -1.0)),
    ELECTRONIC(listOf(5.0, 4.0, 1.0, 0.0, -2.0, 1.0, 0.0, 1.0, 4.0, 5.0)),
    HIP_HOP(listOf(6.0, 5.0, 2.0, 3.0, -1.0, -1.0, 1.0, 0.0, 2.0, 3.0)),
    JAZZ(listOf(3.0, 2.0, 1.0, 2.0, -2.0, -2.0, 0.0, 1.0, 2.0, 3.0)),
    CLASSICAL(listOf(4.0, 3.0, 2.0, 1.0, -1.0, -1.0, 0.0, 2.0, 3.0, 4.0)),
    ACOUSTIC(listOf(4.0, 3.0, 2.0, 1.0, 1.0, 1.0, 2.0, 2.0, 3.0, 2.0)),
    CUSTOM(null),
    ;

    /** The preset's gain per band, or `null` for [CUSTOM]. */
    public val gainsDb: List<Double>? = gains
}

/** Reverb rooms (WH-091), the standard I3DL2 presets every Android device implements. */
public enum class ReverbPreset {
    NONE,
    SMALL_ROOM,
    MEDIUM_ROOM,
    LARGE_ROOM,
    MEDIUM_HALL,
    LARGE_HALL,
    PLATE,
}

/** Where effects are attached (WH-093). */
public enum class EffectsMode {
    /** One effect chain per playback session of each music app. The reliable default. */
    PER_APP,

    /** One chain on the whole output mix, for phones where apps cannot be detected; not every phone applies it. */
    GLOBAL,
}

/**
 * The user's equaliser and effects (WH-090, WH-091). Settings are global; a listening profile with its own EQ curve
 * replaces [preset] and [customGainsDb] while it is active (WH-092), see [resolve].
 *
 * @property customGainsDb the user's own curve, one gain per [EqBands] band, used when [preset] is [EqPreset.CUSTOM].
 * @property bassBoostDb extra gain of a low shelf around [BASS_SHELF_HZ], on top of the curve.
 * @property extraVolumeDb gain before a limiter that keeps peaks below full scale.
 * @property surround 0.0 (off) to 1.0; only headphones are virtualised.
 */
public data class SoundEffectsSettings(
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    val customGainsDb: List<Double> = EqBands.FLAT,
    val bassBoostDb: Double = 0.0,
    val extraVolumeDb: Double = 0.0,
    val reverb: ReverbPreset = ReverbPreset.NONE,
    val surround: Double = 0.0,
    val mode: EffectsMode = EffectsMode.PER_APP,
) {
    init {
        require(customGainsDb.size == EqBands.COUNT) { "customGainsDb needs ${EqBands.COUNT} bands, had ${customGainsDb.size}" }
        require(customGainsDb.all { it in EqBands.GAIN_RANGE_DB }) { "Band gains must be within ${EqBands.GAIN_RANGE_DB} dB" }
        require(bassBoostDb in BASS_BOOST_RANGE_DB) { "bassBoostDb must be in $BASS_BOOST_RANGE_DB, was $bassBoostDb" }
        require(extraVolumeDb in EXTRA_VOLUME_RANGE_DB) { "extraVolumeDb must be in $EXTRA_VOLUME_RANGE_DB, was $extraVolumeDb" }
        require(surround in 0.0..1.0) { "surround must be in 0.0..1.0, was $surround" }
    }

    /** The equaliser curve chosen in these settings, before any profile curve or bass boost. */
    public val curveDb: List<Double>
        get() = preset.gainsDb ?: customGainsDb

    /**
     * What to apply: [profileEq] (the active profile's own curve) when present, else [curveDb], plus the bass shelf.
     * Each band is clamped to [MAX_BAND_GAIN_DB] either way.
     */
    public fun resolve(profileEq: EqCurve?): ResolvedEffects {
        val base = profileEq?.let(EqBands::sample) ?: curveDb
        val bands =
            base.mapIndexed { i, gain ->
                (gain + bassBoostDb * bassShelfWeight(EqBands.CENTERS_HZ[i])).coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
            }
        return ResolvedEffects(
            bandGainsDb = bands,
            inputGainDb = extraVolumeDb,
            reverb = reverb,
            surround = surround,
            usesProfileEq = profileEq != null,
        )
    }

    public companion object {
        public val BASS_BOOST_RANGE_DB: ClosedFloatingPointRange<Double> = 0.0..12.0
        public val EXTRA_VOLUME_RANGE_DB: ClosedFloatingPointRange<Double> = 0.0..10.0

        /** Corner of the bass shelf: full boost well below it, half at it, almost none an octave and a half above. */
        public const val BASS_SHELF_HZ: Double = 120.0

        /** Curve plus bass boost never go beyond this, so stacking both cannot reach absurd gains. */
        public const val MAX_BAND_GAIN_DB: Double = 18.0

        /** Share of the bass boost at [frequencyHz]: a first-order shelf, 1 at low frequencies, 0.5 at [BASS_SHELF_HZ]. */
        public fun bassShelfWeight(frequencyHz: Double): Double = 1.0 / (1.0 + (frequencyHz / BASS_SHELF_HZ).pow(2))
    }
}

/**
 * The effects as the platform applies them: final gain per [EqBands] band, the gain before the limiter, reverb and
 * surround.
 */
public data class ResolvedEffects(
    val bandGainsDb: List<Double>,
    val inputGainDb: Double,
    val reverb: ReverbPreset,
    val surround: Double,
    val usesProfileEq: Boolean,
) {
    /** The largest boost anywhere in the spectrum, in dB (0 when nothing is raised). */
    public val maxBoostDb: Double
        get() = max(0.0, bandGainsDb.max()) + inputGainDb

    /**
     * Whether these effects make what reaches the ear louder, so the app warns that the dose does not include it
     * (WH-094). Boosts under [BOOST_WARNING_DB] are within the accuracy of the output-level estimate.
     */
    public val raisesLevel: Boolean
        get() = maxBoostDb >= BOOST_WARNING_DB

    public companion object {
        public const val BOOST_WARNING_DB: Double = 3.0
    }
}
