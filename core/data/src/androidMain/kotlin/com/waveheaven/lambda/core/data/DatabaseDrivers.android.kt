package com.waveheaven.lambda.core.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.waveheaven.lambda.core.data.db.WhDatabase

/**
 * The app database in Android's private `databases/` directory, which `data_extraction_rules.xml` and
 * `backup_rules.xml` exclude from backups.
 */
fun createAndroidDriver(context: Context): SqlDriver = AndroidSqliteDriver(WhDatabase.Schema, context, DATABASE_FILE)
