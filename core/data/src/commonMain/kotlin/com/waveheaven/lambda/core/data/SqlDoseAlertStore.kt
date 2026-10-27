package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.data.db.WhDatabase
import com.waveheaven.lambda.core.domain.alert.DoseAlertSettings
import com.waveheaven.lambda.core.domain.alert.DoseAlertStore
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/** [DoseAlertStore] in the app database's key-value settings. All thresholds are on until the user changes them. */
class SqlDoseAlertStore(
    database: WhDatabase,
    ioContext: CoroutineContext = Dispatchers.IO,
) : DoseAlertStore {
    private val settingsTable = SqlSettings(database, ioContext)

    override val settings: Flow<DoseAlertSettings> =
        settingsTable
            .observe(ENABLED)
            .map { stored -> if (stored == null) DoseAlertSettings() else DoseAlertSettings(decode(stored)) }
            .distinctUntilChanged()

    override suspend fun setSettings(settings: DoseAlertSettings) = settingsTable.put(ENABLED, encode(settings.enabled))

    override suspend fun passedThresholds(): Set<DoseThreshold> = settingsTable.get(PASSED)?.let(::decode) ?: emptySet()

    override suspend fun setPassedThresholds(passed: Set<DoseThreshold>) = settingsTable.put(PASSED, encode(passed))

    private companion object {
        const val ENABLED = "dose_alerts_enabled"
        const val PASSED = "dose_alerts_passed"

        fun encode(thresholds: Set<DoseThreshold>): String = thresholds.joinToString(",") { it.name }

        fun decode(stored: String): Set<DoseThreshold> =
            stored.split(',').mapNotNull { name -> DoseThreshold.entries.firstOrNull { it.name == name } }.toSet()
    }
}
