package com.waveheaven.lambda.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.waveheaven.lambda.core.data.db.WhDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Small key-value settings stored in the app database, observed as flows. */
internal class SqlSettings(
    database: WhDatabase,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {
    private val queries = database.settingQueries

    fun observe(key: String): Flow<String?> = queries.selectValue(key).asFlow().mapToOneOrNull(ioContext)

    suspend fun get(key: String): String? = withContext(ioContext) { queries.selectValue(key).executeAsOneOrNull() }

    suspend fun put(
        key: String,
        value: String,
    ) {
        withContext(ioContext) { queries.put(key, value) }
    }

    suspend fun remove(key: String) {
        withContext(ioContext) { queries.remove(key) }
    }
}
