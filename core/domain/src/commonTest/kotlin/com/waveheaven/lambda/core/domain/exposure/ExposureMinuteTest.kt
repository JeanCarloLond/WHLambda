package com.waveheaven.lambda.core.domain.exposure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ExposureMinuteTest {
    private val start = Instant.parse("2026-10-26T08:15:00Z")

    @Test
    fun `instants truncate to the start of their minute`() {
        assertEquals(start, Instant.parse("2026-10-26T08:15:59.999Z").truncatedToMinute())
        assertEquals(Instant.parse("1969-12-31T23:59:00Z"), Instant.parse("1969-12-31T23:59:30Z").truncatedToMinute())
    }

    @Test
    fun `a minute is a dose segment at its output level`() {
        val minute = ExposureMinute(start, 30.seconds, 85.dBA, 6.0, 70.dBA)

        assertEquals(ExposureSegment(85.dBA, 30.seconds), minute.toSegment())
    }

    @Test
    fun `merging adds listening time and energy-averages the levels`() {
        val a = ExposureMinute(start, 30.seconds, 80.dBA, 6.0, 60.dBA)
        val b = ExposureMinute(start, 30.seconds, 90.dBA, 8.0, null)

        val merged = a.mergedWith(b)

        assertEquals(1.minutes, merged.listening)
        assertEquals(87.40, merged.output.decibels, 0.01)
        assertEquals(8.0, merged.outputUncertaintyDb)
        assertEquals(60.0, merged.ambient!!.decibels, 1e-9)
    }

    @Test
    fun `merged listening never exceeds a minute`() {
        val a = ExposureMinute(start, 50.seconds, 80.dBA, 6.0, null)

        val merged = a.mergedWith(a)

        assertEquals(1.minutes, merged.listening)
        assertNull(merged.ambient)
    }

    @Test
    fun `rejects invalid minutes`() {
        assertFailsWith<IllegalArgumentException> { ExposureMinute(start + 1.seconds, 30.seconds, 80.dBA, 6.0, null) }
        assertFailsWith<IllegalArgumentException> { ExposureMinute(start, Duration.ZERO, 80.dBA, 6.0, null) }
        assertFailsWith<IllegalArgumentException> { ExposureMinute(start, 61.seconds, 80.dBA, 6.0, null) }
        assertFailsWith<IllegalArgumentException> { ExposureMinute(start, 30.seconds, 80.dBA, -1.0, null) }
        val later = ExposureMinute(start + 1.minutes, 30.seconds, 80.dBA, 6.0, null)
        assertFailsWith<IllegalArgumentException> { ExposureMinute(start, 30.seconds, 80.dBA, 6.0, null).mergedWith(later) }
    }
}
