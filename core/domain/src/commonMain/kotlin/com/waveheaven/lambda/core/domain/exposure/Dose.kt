package com.waveheaven.lambda.core.domain.exposure

import kotlin.jvm.JvmInline

/** Share of a [DoseStandard] budget that has been consumed; 1.0 means 100 %. */
@JvmInline
public value class Dose(
    public val fraction: Double,
) : Comparable<Dose> {
    init {
        require(fraction.isFinite() && fraction >= 0.0) { "Dose must be finite and non-negative, was $fraction" }
    }

    public val percent: Double
        get() = fraction * PERCENT

    public val isExceeded: Boolean
        get() = fraction > 1.0

    public operator fun plus(other: Dose): Dose = Dose(fraction + other.fraction)

    override fun compareTo(other: Dose): Int = fraction.compareTo(other.fraction)

    public companion object {
        public val NONE: Dose = Dose(0.0)
        private const val PERCENT = 100.0
    }
}
