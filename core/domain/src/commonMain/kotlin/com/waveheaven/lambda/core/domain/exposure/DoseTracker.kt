package com.waveheaven.lambda.core.domain.exposure

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** The dose of one [standard] over the window that started at [start]. */
public data class WindowDose(
    val standard: DoseStandard,
    val start: Instant,
    val dose: Dose,
) {
    /** How long [level] can still be held before this window's budget is spent. */
    public fun timeLeftAt(level: SoundLevel): Duration = standard.remainingDuration(dose, level)
}

/**
 * What the user has consumed so far: today against NIOSH (since local midnight), and the last 7 days against the
 * weekly standard (WHO adult or sensitive, rolling window as in ITU-T H.870).
 *
 * @property latest the most recent stored minute, whose level is "the current level" when nothing is playing.
 */
public data class DoseReport(
    val daily: WindowDose,
    val weekly: WindowDose,
    val latest: ExposureMinute?,
)

/** Computes dose reports from the stored aggregates and applies the retention policy when recording. */
public class DoseTracker(
    private val log: ExposureLog,
    private val calendar: DayBoundaries,
) {
    /** Stores [minute] and deletes aggregates older than [ExposureMinute.RETENTION]. */
    public suspend fun record(minute: ExposureMinute) {
        log.record(minute)
        log.deleteBefore(minute.start - ExposureMinute.RETENTION)
    }

    public suspend fun report(
        now: Instant,
        weeklyStandard: DoseStandard,
    ): DoseReport {
        require(weeklyStandard.window == DoseWindow.WEEKLY) { "$weeklyStandard is not a weekly standard" }
        val weekStart = now - WEEK
        val dayStart = calendar.startOfDay(now)
        val week = log.minutesSince(minOf(weekStart, dayStart)).filter { it.start < now }
        return DoseReport(
            daily = window(DoseStandard.NIOSH, dayStart, week),
            weekly = window(weeklyStandard, weekStart, week),
            latest = week.lastOrNull(),
        )
    }

    /** Dose of each of the last [days] local days (today last), against [standard]. */
    public suspend fun dailyHistory(
        now: Instant,
        days: Int,
        standard: DoseStandard,
    ): List<WindowDose> {
        require(days >= 1) { "days must be at least 1" }
        val starts = generateSequence(calendar.startOfDay(now)) { calendar.startOfDay(it - 1.days / 2) }.take(days).toList().reversed()
        val minutes = log.minutesSince(starts.first())
        return starts.mapIndexed { i, start ->
            val end = starts.getOrNull(i + 1) ?: now
            WindowDose(standard, start, standard.doseOf(minutes.filter { it.start >= start && it.start < end }.map { it.toSegment() }))
        }
    }

    private fun window(
        standard: DoseStandard,
        start: Instant,
        minutes: List<ExposureMinute>,
    ) = WindowDose(standard, start, standard.doseOf(minutes.filter { it.start >= start }.map { it.toSegment() }))

    private companion object {
        val WEEK: Duration = 7.days
    }
}
