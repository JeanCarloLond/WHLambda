package com.waveheaven.lambda

import android.app.Application
import com.waveheaven.lambda.shared.initKoin
import org.koin.android.ext.koin.androidContext

class WaveHeavenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@WaveHeavenApplication)
        }
    }
}
