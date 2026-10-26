package com.waveheaven.lambda.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.waveheaven.lambda.core.data.db.WhDatabase
import java.util.Properties

actual fun testDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), WhDatabase.Schema)
