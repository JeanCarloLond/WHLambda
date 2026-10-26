package com.waveheaven.lambda

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import com.waveheaven.lambda.core.data.createAndroidDriver
import com.waveheaven.lambda.shared.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class WaveHeavenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@WaveHeavenApplication)
            modules(module { single<SqlDriver> { createAndroidDriver(androidContext()) } })
        }
    }
}
