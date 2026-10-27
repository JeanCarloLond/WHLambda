package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.data.db.WhDatabase
import com.waveheaven.lambda.core.domain.consent.Consent
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.DoseWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Instant

/** [UserPreferences] in the app database's key-value settings. */
class SqlUserPreferences(
    private val database: WhDatabase,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : UserPreferences {
    private val settings = SqlSettings(database, ioContext)

    override val onboardingCompleted: Flow<Boolean> =
        settings.observe(ONBOARDING_COMPLETED).map { it == TRUE }.distinctUntilChanged()

    override val weeklyStandard: Flow<DoseStandard> =
        settings
            .observe(WEEKLY_STANDARD)
            .map { stored -> WEEKLY_STANDARDS.firstOrNull { it.name == stored } ?: DoseStandard.WHO_ADULT }
            .distinctUntilChanged()

    override val consent: Flow<Consent?> =
        combine(settings.observe(CONSENT_VERSION), settings.observe(CONSENT_AT_MS)) { version, at ->
            if (version == null || at == null) null else Consent(version.toInt(), Instant.fromEpochMilliseconds(at.toLong()))
        }.distinctUntilChanged()

    override val microphoneAdjustmentDb: Flow<Double> =
        settings.observe(MICROPHONE_ADJUSTMENT).map { it?.toDoubleOrNull() ?: 0.0 }.distinctUntilChanged()

    override val mediaApp: Flow<String?> = settings.observe(MEDIA_APP).distinctUntilChanged()

    override suspend fun setMicrophoneAdjustmentDb(adjustmentDb: Double) {
        require(adjustmentDb in UserPreferences.MICROPHONE_ADJUSTMENT_RANGE_DB) { "Adjustment out of range: $adjustmentDb" }
        settings.put(MICROPHONE_ADJUSTMENT, adjustmentDb.toString())
    }

    override suspend fun setMediaApp(app: String?) {
        if (app == null) settings.remove(MEDIA_APP) else settings.put(MEDIA_APP, app)
    }

    override suspend fun completeOnboarding() = settings.put(ONBOARDING_COMPLETED, TRUE)

    override suspend fun setWeeklyStandard(standard: DoseStandard) {
        require(standard.window == DoseWindow.WEEKLY) { "$standard is not a weekly standard" }
        settings.put(WEEKLY_STANDARD, standard.name)
    }

    override suspend fun giveConsent(
        version: Int,
        at: Instant,
    ) {
        val consent = Consent(version, at)
        withContext(ioContext) {
            database.transaction {
                database.settingQueries.put(CONSENT_VERSION, consent.version.toString())
                database.settingQueries.put(CONSENT_AT_MS, consent.acceptedAt.toEpochMilliseconds().toString())
            }
        }
    }

    override suspend fun withdrawConsent() {
        withContext(ioContext) {
            database.transaction {
                database.settingQueries.remove(CONSENT_VERSION)
                database.settingQueries.remove(CONSENT_AT_MS)
            }
        }
    }

    private companion object {
        const val ONBOARDING_COMPLETED = "onboarding_completed"
        const val WEEKLY_STANDARD = "weekly_standard"
        const val CONSENT_VERSION = "consent_version"
        const val CONSENT_AT_MS = "consent_at_ms"
        const val MICROPHONE_ADJUSTMENT = "microphone_adjustment_db"
        const val MEDIA_APP = "media_app"
        const val TRUE = "true"

        val WEEKLY_STANDARDS = DoseStandard.entries.filter { it.window == DoseWindow.WEEKLY }
    }
}
