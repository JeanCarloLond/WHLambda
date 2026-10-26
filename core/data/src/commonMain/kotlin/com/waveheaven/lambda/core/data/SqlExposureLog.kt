package com.waveheaven.lambda.core.data

import app.cash.sqldelight.coroutines.asFlow
import com.waveheaven.lambda.core.data.db.Exposure_minute
import com.waveheaven.lambda.core.data.db.WhDatabase
import com.waveheaven.lambda.core.domain.exposure.ExposureLog
import com.waveheaven.lambda.core.domain.exposure.ExposureMinute
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * [ExposureLog] stored in the app's SQLite database. The database lives in app-private storage, which the Android
 * backup rules exclude from cloud backup and device transfer (ADR-0007).
 */
class SqlExposureLog(
    private val database: WhDatabase,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : ExposureLog {
    private val queries = database.exposureMinuteQueries

    override suspend fun record(minute: ExposureMinute): Unit =
        withContext(ioContext) {
            database.transaction {
                val key = minute.start.epochMinute
                val merged =
                    queries
                        .selectAt(key)
                        .executeAsOneOrNull()
                        ?.toDomain()
                        ?.mergedWith(minute) ?: minute
                queries.upsert(
                    start_epoch_minute = key,
                    listening_ms = merged.listening.inWholeMilliseconds,
                    output_leq_dba = merged.output.decibels,
                    output_uncertainty_db = merged.outputUncertaintyDb,
                    ambient_leq_dba = merged.ambient?.decibels,
                )
            }
        }

    override suspend fun minutesSince(start: Instant): List<ExposureMinute> =
        withContext(ioContext) {
            queries.selectSince(start.epochMinuteCeiling).executeAsList().map { it.toDomain() }
        }

    override fun changes(): Flow<Unit> = queries.countAll().asFlow().map { }

    override suspend fun deleteBefore(cutoff: Instant) {
        withContext(ioContext) { queries.deleteBefore(cutoff.epochMinuteCeiling) }
    }

    override suspend fun deleteAll() {
        withContext(ioContext) { queries.deleteAll() }
    }

    private fun Exposure_minute.toDomain() =
        ExposureMinute(
            start = Instant.fromEpochSeconds(start_epoch_minute * SECONDS_PER_MINUTE),
            listening = listening_ms.milliseconds,
            output = SoundLevel(output_leq_dba),
            outputUncertaintyDb = output_uncertainty_db,
            ambient = ambient_leq_dba?.let(::SoundLevel),
        )

    private companion object {
        const val SECONDS_PER_MINUTE = 60L

        val Instant.epochMinute: Long
            get() = epochSeconds.floorDiv(SECONDS_PER_MINUTE)

        /** First whole minute at or after this instant, so "since" and "before" bounds match the domain's `>=`. */
        val Instant.epochMinuteCeiling: Long
            get() {
                val seconds = epochSeconds + if (nanosecondsOfSecond > 0) 1 else 0
                return -(-seconds).floorDiv(SECONDS_PER_MINUTE)
            }
    }
}
