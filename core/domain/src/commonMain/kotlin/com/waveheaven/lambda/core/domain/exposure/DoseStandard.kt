package com.waveheaven.lambda.core.domain.exposure

import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** The period over which a [DoseStandard] budget resets. */
public enum class DoseWindow { DAILY, WEEKLY }

/**
 * A noise-exposure limit expressed as a criterion level that may be sustained for a reference
 * period, plus an exchange rate: every [exchangeRateDb] above the criterion halves the allowed time.
 *
 * See `docs/domain/hearing-safety.md` for sources and rationale.
 */
public enum class DoseStandard(
    public val criterionLevel: SoundLevel,
    public val referencePeriod: Duration,
    public val exchangeRateDb: Double,
    public val window: DoseWindow,
) {
    /** NIOSH REL (1998): 85 dB(A) for 8 h per day, 3 dB exchange rate. */
    NIOSH(SoundLevel(85.0), 8.hours, exchangeRateDb = 3.0, DoseWindow.DAILY),

    /** WHO/ITU-T H.870 mode 1 (adults): 80 dB(A) for 40 h per week, equal-energy (≈1.6 Pa²·h). */
    WHO_ADULT(SoundLevel(80.0), 40.hours, EQUAL_ENERGY_EXCHANGE_RATE_DB, DoseWindow.WEEKLY),

    /** WHO/ITU-T H.870 mode 2 (sensitive users, children): 75 dB(A) for 40 h per week (≈0.51 Pa²·h). */
    WHO_SENSITIVE(SoundLevel(75.0), 40.hours, EQUAL_ENERGY_EXCHANGE_RATE_DB, DoseWindow.WEEKLY),
    ;

    /** How long [level] may be sustained before reaching 100 % of the budget. */
    public fun allowedDuration(level: SoundLevel): Duration =
        referencePeriod / 2.0.pow((level.decibels - criterionLevel.decibels) / exchangeRateDb)

    /** Fraction of the budget consumed by [segments]. */
    public fun doseOf(segments: Iterable<ExposureSegment>): Dose = Dose(segments.sumOf { it.duration / allowedDuration(it.level) })

    /** Time left at [level] before [alreadyConsumed] reaches 100 %; zero once the budget is spent. */
    public fun remainingDuration(
        alreadyConsumed: Dose,
        level: SoundLevel,
    ): Duration = allowedDuration(level) * (1.0 - alreadyConsumed.fraction).coerceAtLeast(0.0)
}

/** Exchange rate that keeps acoustic energy constant: doubling the energy adds 10·log10(2) ≈ 3.01 dB. */
private val EQUAL_ENERGY_EXCHANGE_RATE_DB: Double = 10.0 * log10(2.0)
