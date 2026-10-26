package com.waveheaven.lambda.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.waveheaven.lambda.core.data.db.Listening_profile
import com.waveheaven.lambda.core.data.db.WhDatabase
import com.waveheaven.lambda.core.domain.profile.BuiltInProfileException
import com.waveheaven.lambda.core.domain.profile.EqCurve
import com.waveheaven.lambda.core.domain.profile.EqPoint
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/** [ListeningProfileStore] on the app database: user profiles in a table, the selection in settings. */
class SqlListeningProfileStore(
    private val database: WhDatabase,
    private val ioContext: CoroutineContext = Dispatchers.IO,
    private val clock: Clock = Clock.System,
) : ListeningProfileStore {
    private val queries = database.listeningProfileQueries
    private val settings = SqlSettings(database, ioContext)

    override fun profiles(): Flow<List<ListeningProfile>> =
        queries
            .selectAll()
            .asFlow()
            .mapToList(ioContext)
            .map { rows -> ListeningProfile.BUILT_IN + rows.map { it.toDomain() } }

    override fun activeProfile(): Flow<ListeningProfile> =
        combine(profiles(), settings.observe(ACTIVE_PROFILE_KEY)) { all, activeId ->
            all.firstOrNull { it.id == activeId } ?: ListeningProfile.DEFAULT
        }.distinctUntilChanged()

    override suspend fun save(profile: ListeningProfile) {
        if (profile.isBuiltIn || ListeningProfile.BUILT_IN.any { it.id == profile.id }) throw BuiltInProfileException(profile.id)
        withContext(ioContext) {
            queries.upsert(
                id = profile.id,
                name = profile.name,
                target_snr_db = profile.targetSnrDb,
                min_volume = profile.minVolume,
                max_volume = profile.maxVolume,
                eq_points = profile.eq?.encode(),
                now_ms = clock.now().toEpochMilliseconds(),
            )
        }
    }

    override suspend fun delete(id: String) {
        if (ListeningProfile.BUILT_IN.any { it.id == id }) throw BuiltInProfileException(id)
        withContext(ioContext) {
            database.transaction {
                queries.deleteById(id)
                if (database.settingQueries.selectValue(ACTIVE_PROFILE_KEY).executeAsOneOrNull() == id) {
                    database.settingQueries.remove(ACTIVE_PROFILE_KEY)
                }
            }
        }
    }

    override suspend fun setActive(id: String) {
        val exists =
            ListeningProfile.BUILT_IN.any { it.id == id } ||
                withContext(ioContext) { queries.selectById(id).executeAsOneOrNull() != null }
        require(exists) { "No profile '$id'" }
        settings.put(ACTIVE_PROFILE_KEY, id)
    }

    private fun Listening_profile.toDomain() =
        ListeningProfile(
            id = id,
            name = name,
            isBuiltIn = false,
            targetSnrDb = target_snr_db,
            minVolume = min_volume,
            maxVolume = max_volume,
            eq = eq_points?.let(::decodeEq),
        )

    private companion object {
        const val ACTIVE_PROFILE_KEY = "active_profile"

        fun EqCurve.encode(): String = points.joinToString(";") { "${it.frequencyHz}:${it.gainDb}" }

        fun decodeEq(encoded: String): EqCurve =
            EqCurve(
                encoded.split(';').map { pair ->
                    val (frequency, gain) = pair.split(':')
                    EqPoint(frequency.toDouble(), gain.toDouble())
                },
            )
    }
}
