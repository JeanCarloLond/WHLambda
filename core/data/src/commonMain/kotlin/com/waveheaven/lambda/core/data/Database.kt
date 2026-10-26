package com.waveheaven.lambda.core.data

import app.cash.sqldelight.db.SqlDriver
import com.waveheaven.lambda.core.data.db.WhDatabase

/** File name of the app database on every platform. */
internal const val DATABASE_FILE = "waveheaven.db"

/** Opens the app database on a platform [driver] (`createAndroidDriver`, `createDesktopDriver`). */
fun createDatabase(driver: SqlDriver): WhDatabase = WhDatabase(driver)
