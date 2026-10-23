package com.waveheaven.lambda.core.domain.exposure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SoundLevelTest {
    @Test
    fun `94 dB SPL is one pascal RMS`() {
        assertEquals(1.0, 94.dBA.meanSquarePressure, 0.01)
    }

    @Test
    fun `mean-square pressure round-trips back to the level`() {
        val level = 87.3.dBA

        assertEquals(level.decibels, SoundLevel.fromMeanSquarePressure(level.meanSquarePressure).decibels, 1e-9)
    }

    @Test
    fun `levels compare by decibels`() {
        assertTrue(80.dBA < 85.dBA)
    }

    @Test
    fun `non-finite levels are rejected`() {
        assertFailsWith<IllegalArgumentException> { SoundLevel(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { SoundLevel(Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `Leq of a steady level is that level`() {
        val segments = listOf(ExposureSegment(80.dBA, 1.hours), ExposureSegment(80.dBA, 3.hours))

        assertEquals(80.0, equivalentLevel(segments).decibels, 1e-9)
    }

    @Test
    fun `Leq is energy-weighted, so loud moments dominate`() {
        val segments = listOf(ExposureSegment(80.dBA, 1.hours), ExposureSegment(90.dBA, 1.hours))

        // 10·log10((10^8 + 10^9) / 2) ≈ 87.40 dB, far above the arithmetic mean of 85 dB.
        assertEquals(87.40, equivalentLevel(segments).decibels, 0.01)
    }

    @Test
    fun `Leq weights segments by duration`() {
        val segments = listOf(ExposureSegment(70.dBA, 50.minutes), ExposureSegment(100.dBA, 10.minutes))

        assertEquals(92.24, equivalentLevel(segments).decibels, 0.01)
    }

    @Test
    fun `Leq needs a non-zero duration`() {
        assertFailsWith<IllegalArgumentException> { equivalentLevel(emptyList()) }
        assertFailsWith<IllegalArgumentException> { equivalentLevel(listOf(ExposureSegment(80.dBA, Duration.ZERO))) }
    }

    @Test
    fun `negative durations are rejected`() {
        assertFailsWith<IllegalArgumentException> { ExposureSegment(80.dBA, (-1).hours) }
    }
}
