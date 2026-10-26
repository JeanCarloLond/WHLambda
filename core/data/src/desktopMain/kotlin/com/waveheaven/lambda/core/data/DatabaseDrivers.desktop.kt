package com.waveheaven.lambda.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.waveheaven.lambda.core.data.db.WhDatabase
import java.io.File
import java.util.Properties

/** The app database in [directory], created or migrated to the current schema. */
fun createDesktopDriver(directory: File = desktopDataDirectory()): SqlDriver {
    directory.mkdirs()
    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${File(directory, DATABASE_FILE).absolutePath}",
        properties = Properties(),
        schema = WhDatabase.Schema,
    )
}

/** A throw-away database in memory, for tests and previews. */
fun createInMemoryDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), WhDatabase.Schema)

/**
 * Per-user data directory: `%LOCALAPPDATA%\WaveHeaven Lambda` on Windows, `$XDG_DATA_HOME/waveheaven-lambda`
 * (default `~/.local/share/waveheaven-lambda`) elsewhere. Local only: nothing here roams or syncs.
 */
fun desktopDataDirectory(): File {
    val home = System.getProperty("user.home")
    return if (System.getProperty("os.name").lowercase().startsWith("win")) {
        File(System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local", "WaveHeaven Lambda")
    } else {
        File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "waveheaven-lambda")
    }
}
