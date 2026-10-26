package com.waveheaven.lambda.core.domain.exposure

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Port: durable storage of [ExposureMinute]s on the device. Implementations store aggregates only, never audio,
 * and must exclude them from cloud backups (ADR-0007).
 */
public interface ExposureLog {
    /** Stores [minute], merging it with any record of the same minute (see [ExposureMinute.mergedWith]). */
    public suspend fun record(minute: ExposureMinute)

    /** Every minute that starts at or after [start], oldest first. */
    public suspend fun minutesSince(start: Instant): List<ExposureMinute>

    /** Emits once on collection and again after every change, so readers know when to reload. */
    public fun changes(): Flow<Unit>

    /** Deletes every minute that starts before [cutoff]. */
    public suspend fun deleteBefore(cutoff: Instant)

    /** Deletes everything. */
    public suspend fun deleteAll()
}

/** Port: the user's local calendar, so the domain can find day boundaries without a time-zone library. */
public fun interface DayBoundaries {
    /** Start of the local day that contains [instant]. */
    public fun startOfDay(instant: Instant): Instant
}
