package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.data.db.WhDatabase
import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.core.domain.hearing.HearingCheckStore
import com.waveheaven.lambda.core.domain.hearing.HearingResult
import com.waveheaven.lambda.core.domain.hearing.StoredHearingCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/** [HearingCheckStore] in the app database: one row per ear and frequency of each check. */
class SqlHearingCheckStore(
    private val database: WhDatabase,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : HearingCheckStore {
    private val queries = database.hearingThresholdQueries

    override suspend fun save(check: StoredHearingCheck) =
        withContext(ioContext) {
            database.transaction {
                check.result.thresholds.forEach { (ear, byFrequency) ->
                    byFrequency.forEach { (frequency, threshold) ->
                        queries.insert(check.takenAt.toEpochMilliseconds(), ear.name, frequency.toLong(), threshold)
                    }
                }
            }
        }

    override suspend fun latest(): StoredHearingCheck? =
        withContext(ioContext) {
            val takenAt = queries.latestTakenAt().executeAsOne().taken_at_ms ?: return@withContext null
            val rows = queries.selectTakenAt(takenAt).executeAsList()
            StoredHearingCheck(
                takenAt = Instant.fromEpochMilliseconds(takenAt),
                result =
                    HearingResult(
                        rows.groupBy { Ear.valueOf(it.ear) }.mapValues { (_, byEar) ->
                            byEar.associate { it.frequency_hz.toInt() to it.threshold_db }
                        },
                    ),
            )
        }

    override suspend fun deleteAll() {
        withContext(ioContext) { queries.deleteAll() }
    }
}
