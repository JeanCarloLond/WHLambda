package com.waveheaven.lambda.core.data

import app.cash.sqldelight.db.QueryResult
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.DoseTracker
import com.waveheaven.lambda.core.domain.exposure.ExposureMinute
import com.waveheaven.lambda.core.domain.exposure.dBA
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// advanceUntilIdle is still experimental in kotlinx-coroutines-test, but it is the way to drain the dispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class SqlExposureLogTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = testDriver()
    private val log = SqlExposureLog(createDatabase(driver), dispatcher)
    private val start = Instant.parse("2026-10-26T08:15:00Z")

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun `stores and reads back every field`() =
        test {
            val minute = ExposureMinute(start, 45.seconds, 82.5.dBA, 6.0, 71.25.dBA)

            log.record(minute)

            assertEquals(listOf(minute), log.minutesSince(start))
        }

    @Test
    fun `a missing ambient level stays missing`() =
        test {
            log.record(ExposureMinute(start, 45.seconds, 82.5.dBA, 6.0, null))

            assertNull(log.minutesSince(start).single().ambient)
        }

    @Test
    fun `recording the same minute twice merges instead of overwriting`() =
        test {
            log.record(ExposureMinute(start, 30.seconds, 80.dBA, 4.0, null))
            log.record(ExposureMinute(start, 30.seconds, 90.dBA, 8.0, null))

            val merged = log.minutesSince(start).single()
            assertEquals(1.minutes, merged.listening)
            assertEquals(87.40, merged.output.decibels, 0.01)
            assertEquals(8.0, merged.outputUncertaintyDb)
        }

    @Test
    fun `reads minutes since a bound, oldest first`() =
        test {
            listOf(2, 0, 1).forEach { log.record(ExposureMinute(start + it.minutes, 1.minutes, 80.dBA, 6.0, null)) }

            assertEquals(listOf(start + 1.minutes, start + 2.minutes), log.minutesSince(start + 30.seconds).map { it.start })
        }

    @Test
    fun `deletes before a cutoff, and everything`() =
        test {
            (0..4).forEach { log.record(ExposureMinute(start + it.minutes, 1.minutes, 80.dBA, 6.0, null)) }

            log.deleteBefore(start + 3.minutes)
            assertEquals(2, log.minutesSince(Instant.DISTANT_PAST).size)

            log.deleteAll()
            assertTrue(log.minutesSince(Instant.DISTANT_PAST).isEmpty())
        }

    @Test
    fun `changes emit on collection and after every write`() =
        test {
            var emissions = 0
            val collector = launch { log.changes().collect { emissions++ } }
            advanceUntilIdle()
            val initial = emissions

            log.record(ExposureMinute(start, 1.minutes, 80.dBA, 6.0, null))
            advanceUntilIdle()
            log.deleteAll()
            advanceUntilIdle()

            collector.cancel()
            assertEquals(1, initial)
            assertEquals(3, emissions)
        }

    @Test
    fun `the dose tracker computes doses from the database across restarts`() =
        test {
            val now = Instant.parse("2026-10-26T20:00:00Z")
            val bogota = SystemDayBoundaries { TimeZone.of("America/Bogota") }
            val firstRun = DoseTracker(log, bogota)
            repeat(60) { firstRun.record(ExposureMinute(now - 2.hours + it.minutes, 1.minutes, 94.dBA, 6.0, null)) }

            // A new tracker over a new log on the same database, as after the app restarts.
            val restarted = DoseTracker(SqlExposureLog(createDatabase(driver), dispatcher), bogota)
            val report = restarted.report(now, DoseStandard.WHO_ADULT)

            assertEquals(1.0, report.daily.dose.fraction, 0.01)
            assertEquals(Instant.parse("2026-10-26T05:00:00Z"), report.daily.start)
            assertEquals(60, log.minutesSince(now - 1.days).size)
        }

    @Test
    fun `thirty days of continuous listening fits in well under 5 MB`() =
        test {
            // WH-107: worst case, one row per minute for 30 days.
            val rows = 30 * 24 * 60
            repeat(rows) { log.record(ExposureMinute(start + it.minutes, 1.minutes, 85.dBA, 6.0, 70.dBA)) }

            val bytes = pragma("page_count") * pragma("page_size")
            assertTrue(bytes < 5_000_000, "database is $bytes bytes")
        }

    private fun pragma(name: String): Long =
        driver
            .executeQuery(
                identifier = null,
                sql = "PRAGMA $name",
                mapper = { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0)!!)
                },
                parameters = 0,
            ).value

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }
}
