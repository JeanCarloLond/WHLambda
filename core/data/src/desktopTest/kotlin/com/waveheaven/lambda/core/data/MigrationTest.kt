package com.waveheaven.lambda.core.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.waveheaven.lambda.core.data.db.WhDatabase
import com.waveheaven.lambda.core.domain.exposure.ExposureMinute
import com.waveheaven.lambda.core.domain.exposure.dBA
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Every released schema must upgrade to the current one without losing data. */
class MigrationTest {
    private val migrations = File("src/commonMain/sqldelight/com/waveheaven/lambda/core/data/db")

    @Test
    fun `a version 2 database (exposure only) upgrades and keeps its data`() =
        runTest {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties())
            File(migrations, "1.sqm")
                .readText()
                .split(';')
                .filter { it.isNotBlank() }
                .forEach { driver.execute(null, it, 0) }
            val minute = ExposureMinute(Instant.parse("2026-10-26T08:15:00Z"), 1.minutes, 85.dBA, 6.0, null)
            SqlExposureLog(createDatabase(driver), coroutineContext).record(minute)

            WhDatabase.Schema.migrate(driver, oldVersion = 2, newVersion = WhDatabase.Schema.version)

            val database = createDatabase(driver)
            assertEquals(listOf(minute), SqlExposureLog(database, coroutineContext).minutesSince(minute.start))
            val store = SqlListeningProfileStore(database, coroutineContext)
            store.save(ListeningProfile("study", "Study", false, 4.0, 0.05, 0.4))
            store.setActive("study")
        }

    @Test
    fun `the schema version counts every migration`() {
        val count = migrations.listFiles { file -> file.extension == "sqm" }!!.size
        assertEquals(count + 1L, WhDatabase.Schema.version)
    }
}
