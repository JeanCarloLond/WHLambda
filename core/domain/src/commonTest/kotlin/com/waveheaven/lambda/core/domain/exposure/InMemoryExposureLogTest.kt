package com.waveheaven.lambda.core.domain.exposure

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class InMemoryExposureLogTest {
    private val log = InMemoryExposureLog()
    private val start = Instant.parse("2026-10-26T08:15:00Z")

    @Test
    fun `merges records of the same minute and sorts by time`() =
        runTest {
            log.record(ExposureMinute(start + 1.minutes, 30.seconds, 80.dBA, 6.0, null))
            log.record(ExposureMinute(start, 30.seconds, 80.dBA, 6.0, null))
            log.record(ExposureMinute(start, 30.seconds, 80.dBA, 6.0, null))

            val minutes = log.minutesSince(start)
            assertEquals(listOf(start, start + 1.minutes), minutes.map { it.start })
            assertEquals(1.minutes, minutes.first().listening)
        }

    @Test
    fun `deletes before a cutoff and everything`() =
        runTest {
            log.record(ExposureMinute(start, 30.seconds, 80.dBA, 6.0, null))
            log.record(ExposureMinute(start + 1.minutes, 30.seconds, 80.dBA, 6.0, null))

            log.deleteBefore(start + 1.minutes)
            assertEquals(1, log.minutesSince(Instant.DISTANT_PAST).size)

            log.deleteAll()
            assertTrue(log.minutesSince(Instant.DISTANT_PAST).isEmpty())
            assertEquals(Unit, log.changes().first())
        }
}
