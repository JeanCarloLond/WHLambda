package com.waveheaven.lambda.core.domain.exposure

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DoseTrackerTest {
    private val log = FakeExposureLog()

    // Local days start at 05:00 UTC (UTC−5, Colombia).
    private val calendar = DayBoundaries { instant -> Instant.fromEpochSeconds(((instant.epochSeconds - OFFSET) / DAY) * DAY + OFFSET) }
    private val tracker = DoseTracker(log, calendar)
    private val now = Instant.parse("2026-10-26T20:00:00Z") // 15:00 local

    @Test
    fun `daily NIOSH dose counts only today, weekly WHO dose the last 7 days`() =
        runTest {
            listen(from = now - 2.hours, duration = 1.hours, level = 94.dBA) // today: 1 h of the 1 h allowed
            listen(from = now - 2.days, duration = 1.hours, level = 94.dBA) // two days ago
            listen(from = now - 8.days, duration = 1.hours, level = 94.dBA) // outside the week

            val report = tracker.report(now, DoseStandard.WHO_ADULT)

            assertEquals(1.0, report.daily.dose.fraction, 0.01)
            assertEquals(Instant.parse("2026-10-26T05:00:00Z"), report.daily.start)
            // WHO allows 40 h at 80 dB(A); 94 dB(A) is 14 dB louder → 40 h / 10^1.4 ≈ 1.59 h. Two hours → ≈ 125 %.
            assertEquals(2.0 / (40.0 / 25.12), report.weekly.dose.fraction, 0.01)
            assertEquals(now - 7.days, report.weekly.start)
        }

    @Test
    fun `the latest minute is the one closest to now`() =
        runTest {
            listen(from = now - 3.hours, duration = 10.minutes, level = 80.dBA)
            listen(from = now - 1.hours, duration = 10.minutes, level = 75.dBA)

            val latest = tracker.report(now, DoseStandard.WHO_ADULT).latest

            assertEquals(75.0, latest!!.output.decibels, 1e-9)
            assertEquals(now - 1.hours + 9.minutes, latest.start)
        }

    @Test
    fun `nothing stored means no dose and no latest level`() =
        runTest {
            val report = tracker.report(now, DoseStandard.WHO_SENSITIVE)

            assertEquals(Dose.NONE, report.daily.dose)
            assertEquals(Dose.NONE, report.weekly.dose)
            assertNull(report.latest)
        }

    @Test
    fun `time left accounts for the dose already used`() =
        runTest {
            listen(from = now - 2.hours, duration = 30.minutes, level = 94.dBA) // half of NIOSH's hour at 94 dB(A)

            val daily = tracker.report(now, DoseStandard.WHO_ADULT).daily

            assertEquals(30.0, daily.timeLeftAt(94.dBA).inWholeSeconds / 60.0, 0.5)
        }

    @Test
    fun `daily history splits listening by local day, oldest first`() =
        runTest {
            listen(from = now - 2.hours, duration = 1.hours, level = 94.dBA)
            listen(from = now - 1.days, duration = 30.minutes, level = 94.dBA)

            val history = tracker.dailyHistory(now, days = 7, standard = DoseStandard.NIOSH)

            assertEquals(7, history.size)
            assertEquals(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 1.0), history.map { (it.dose.fraction * 100).roundToInt() / 100.0 })
            assertEquals(Instant.parse("2026-10-20T05:00:00Z"), history.first().start)
        }

    @Test
    fun `recording prunes aggregates older than the retention period`() =
        runTest {
            listen(from = now - 40.days, duration = 1.minutes, level = 80.dBA)
            listen(from = now, duration = 1.minutes, level = 80.dBA)

            assertEquals(listOf(now), log.minutesSince(Instant.DISTANT_PAST).map { it.start })
        }

    @Test
    fun `only weekly standards can be the weekly standard`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { tracker.report(now, DoseStandard.NIOSH) }
            assertFailsWith<IllegalArgumentException> { tracker.dailyHistory(now, 0, DoseStandard.NIOSH) }
        }

    private suspend fun listen(
        from: Instant,
        duration: kotlin.time.Duration,
        level: SoundLevel,
    ) {
        var t = from
        while (t < from + duration) {
            tracker.record(ExposureMinute(t, ExposureMinute.MINUTE, level, 6.0, null))
            t += ExposureMinute.MINUTE
        }
    }

    private class FakeExposureLog : ExposureLog {
        private val minutes = MutableStateFlow(sortedMapOf<Instant, ExposureMinute>())

        override suspend fun record(minute: ExposureMinute) {
            val existing = minutes.value[minute.start]
            minutes.value =
                sortedMapOf<Instant, ExposureMinute>().apply {
                    putAll(minutes.value)
                    put(minute.start, existing?.mergedWith(minute) ?: minute)
                }
        }

        override suspend fun minutesSince(start: Instant) = minutes.value.values.filter { it.start >= start }

        override fun changes(): Flow<Unit> = minutes.map { }

        override suspend fun deleteBefore(cutoff: Instant) {
            minutes.value = minutes.value.filterKeys { it >= cutoff }.toSortedMap()
        }

        override suspend fun deleteAll() {
            minutes.value = sortedMapOf()
        }
    }

    private companion object {
        const val DAY = 86_400L
        const val OFFSET = 5 * 3_600L
    }
}
