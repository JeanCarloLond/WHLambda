package com.waveheaven.lambda

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import com.waveheaven.lambda.adaptive.AndroidAdaptiveVolume
import com.waveheaven.lambda.adaptive.AndroidDoseAlertNotifier
import com.waveheaven.lambda.core.audio.AndroidAmbientMeter
import com.waveheaven.lambda.core.audio.AndroidEffectChainFactory
import com.waveheaven.lambda.core.audio.AndroidEffectsSupport
import com.waveheaven.lambda.core.audio.AndroidMediaVolume
import com.waveheaven.lambda.core.audio.AndroidOutputLevelModels
import com.waveheaven.lambda.core.audio.AndroidPlaybackSessions
import com.waveheaven.lambda.core.audio.AndroidTonePlayer
import com.waveheaven.lambda.core.audio.SoundEffectsEngine
import com.waveheaven.lambda.core.data.createAndroidDriver
import com.waveheaven.lambda.core.domain.alert.DoseAlertNotifier
import com.waveheaven.lambda.core.domain.audio.AmbientMeter
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.effects.SoundEffects
import com.waveheaven.lambda.core.domain.hearing.TonePlayer
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolume
import com.waveheaven.lambda.core.domain.volume.MediaVolume
import com.waveheaven.lambda.core.domain.volume.OutputLevelModels
import com.waveheaven.lambda.effects.AndroidSoundEffects
import com.waveheaven.lambda.shared.initKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

class WaveHeavenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@WaveHeavenApplication)
            modules(
                module {
                    single<SqlDriver> { createAndroidDriver(androidContext()) }
                    single<AmbientMeter> {
                        val preferences = get<UserPreferences>()
                        AndroidAmbientMeter(androidContext()) { preferences.microphoneAdjustmentDb.first() }
                    }
                    single<MediaVolume> { AndroidMediaVolume(androidContext()) }
                    single<OutputLevelModels> { AndroidOutputLevelModels(androidContext()) }
                    single<AdaptiveVolume> { AndroidAdaptiveVolume(androidContext(), get()) }
                    single<DoseAlertNotifier> { AndroidDoseAlertNotifier(androidContext()) }
                    single<TonePlayer> { AndroidTonePlayer() }
                    single { AndroidPlaybackSessions(androidContext()) }
                    single { SoundEffectsEngine(AndroidEffectChainFactory(AndroidEffectsSupport.surroundSupported)) }
                    single<SoundEffects> {
                        AndroidSoundEffects(androidContext(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default))
                    }
                },
            )
        }
    }
}
