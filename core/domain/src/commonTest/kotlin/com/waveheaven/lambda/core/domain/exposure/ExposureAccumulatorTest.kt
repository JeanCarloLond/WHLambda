package com.waveheaven.lambda.core.domain.exposure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ExposureAccumulatorTest {
    private val start = Instant.parse("2026-10-26T08:15:00Z")

    @Test
    fun `emits the previous minute when a new one begins`() {
        val accumulator = ExposureAccumulator()

        val during = (1..59).map { accumulator.add(start + it.seconds, 85.dBA, 6.0, 70.dBA) }
        val completed = accumulator.add(start + 60.seconds, 85.dBA, 6.0, 70.dBA)

        assertEquals(List(59) { null }, during)
        assertEquals(start, completed!!.start)
        assertEquals(59.seconds, completed.listening)
        assertEquals(85.0, completed.output.decibels, 1e-9)
        assertEquals(70.0, completed.ambient!!.decibels, 1e-9)
    }

    @Test
    fun `levels are energy-averaged and the widest uncertainty is kept`() {
        val accumulator = ExposureAccumulator()

        repeat(30) { accumulator.add(start + it.seconds, 80.dBA, 4.0, null) }
        repeat(30) { accumulator.add(start + (30 + it).seconds, 90.dBA, 8.0, 60.dBA) }
        val minute = accumulator.flush()!!

        assertEquals(87.40, minute.output.decibels, 0.01)
        assertEquals(8.0, minute.outputUncertaintyDb)
        assertEquals(60.0, minute.ambient!!.decibels, 1e-9) // only the seconds that had an ambient reading
    }

    @Test
    fun `flush returns the minute in progress once`() {
        val accumulator = ExposureAccumulator()
        accumulator.add(start + 5.seconds, 85.dBA, 6.0, null)

        assertEquals(1.seconds, accumulator.flush()!!.listening)
        assertNull(accumulator.flush())
    }

    @Test
    fun `gaps between minutes do not leak listening time`() {
        val accumulator = ExposureAccumulator()
        accumulator.add(start, 85.dBA, 6.0, null)

        val first = accumulator.add(start + 600.seconds, 70.dBA, 6.0, null)
        val second = accumulator.flush()!!

        assertEquals(1.seconds, first!!.listening)
        assertEquals(start + 600.seconds, second.start)
        assertEquals(70.0, second.output.decibels, 1e-9)
    }

    @Test
    fun `rejects non-positive durations`() {
        assertFailsWith<IllegalArgumentException> { ExposureAccumulator().add(start, 85.dBA, 6.0, null, Duration.ZERO) }
    }
}
