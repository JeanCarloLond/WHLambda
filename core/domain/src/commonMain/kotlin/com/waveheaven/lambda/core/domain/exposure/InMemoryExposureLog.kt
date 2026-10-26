package com.waveheaven.lambda.core.domain.exposure

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Instant

/** [ExposureLog] kept in memory: for tests, previews and builds without storage. */
public class InMemoryExposureLog : ExposureLog {
    private val minutes = MutableStateFlow<Map<Instant, ExposureMinute>>(emptyMap())

    override suspend fun record(minute: ExposureMinute) {
        minutes.update { stored -> stored + (minute.start to (stored[minute.start]?.mergedWith(minute) ?: minute)) }
    }

    override suspend fun minutesSince(start: Instant): List<ExposureMinute> =
        minutes.value.values
            .filter { it.start >= start }
            .sortedBy { it.start }

    override fun changes(): Flow<Unit> = minutes.map { }

    override suspend fun deleteBefore(cutoff: Instant) {
        minutes.update { stored -> stored.filterKeys { it >= cutoff } }
    }

    override suspend fun deleteAll() {
        minutes.value = emptyMap()
    }
}
