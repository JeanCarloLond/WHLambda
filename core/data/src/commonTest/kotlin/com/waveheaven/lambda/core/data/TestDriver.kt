package com.waveheaven.lambda.core.data

import app.cash.sqldelight.db.SqlDriver

/** A fresh in-memory database with the current schema. */
expect fun testDriver(): SqlDriver
