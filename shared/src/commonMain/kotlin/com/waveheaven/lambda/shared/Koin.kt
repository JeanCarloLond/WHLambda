package com.waveheaven.lambda.shared

import com.waveheaven.lambda.core.audio.AdaptiveVolumeEngine
import com.waveheaven.lambda.core.audio.AdaptiveVolumeSession
import com.waveheaven.lambda.core.audio.DoseAlertMonitor
import com.waveheaven.lambda.core.audio.LoopInputs
import com.waveheaven.lambda.core.data.SqlDoseAlertStore
import com.waveheaven.lambda.core.data.SqlExposureLog
import com.waveheaven.lambda.core.data.SqlHearingCheckStore
import com.waveheaven.lambda.core.data.SqlListeningProfileStore
import com.waveheaven.lambda.core.data.SqlUserPreferences
import com.waveheaven.lambda.core.data.SystemDayBoundaries
import com.waveheaven.lambda.core.data.createDatabase
import com.waveheaven.lambda.core.domain.alert.DoseAlertStore
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.exposure.DayBoundaries
import com.waveheaven.lambda.core.domain.exposure.DoseTracker
import com.waveheaven.lambda.core.domain.exposure.ExposureLog
import com.waveheaven.lambda.core.domain.hearing.HearingCheckStore
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import com.waveheaven.lambda.feature.dashboard.dashboardModule
import com.waveheaven.lambda.feature.exposure.exposureModule
import com.waveheaven.lambda.feature.hearingcheck.hearingCheckModule
import com.waveheaven.lambda.feature.onboarding.onboardingModule
import com.waveheaven.lambda.feature.profiles.profilesModule
import com.waveheaven.lambda.feature.settings.settingsModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Local storage and dose tracking. Needs a platform `SqlDriver` binding (`createAndroidDriver`,
 * `createDesktopDriver`), which each app provides through [initKoin]'s config.
 */
val dataModule =
    module {
        single { createDatabase(get()) }
        single<ExposureLog> { SqlExposureLog(get()) }
        single<DayBoundaries> { SystemDayBoundaries() }
        single { DoseTracker(get(), get()) }
        single<ListeningProfileStore> { SqlListeningProfileStore(get()) }
        single<UserPreferences> { SqlUserPreferences(get()) }
        single<DoseAlertStore> { SqlDoseAlertStore(get()) }
        single<HearingCheckStore> { SqlHearingCheckStore(get()) }
    }

/**
 * The adaptive-volume loop, shared by every platform. Needs platform bindings for `AmbientMeter`, `MediaVolume`,
 * `OutputLevelModels` and `DoseAlertNotifier`; it is only created on platforms whose `AdaptiveVolume` binding uses it.
 */
val adaptiveVolumeModule =
    module {
        single { LoopInputs(get<UserPreferences>(), get<ListeningProfileStore>()) }
        single { AdaptiveVolumeEngine(meter = get(), volume = get(), models = get(), tracker = get(), inputs = get()) }
        // Lives as long as the process, so the loop outlives screens and is shared with the Android service.
        single { DoseAlertMonitor(store = get(), notifier = get(), weeklyStandard = get<UserPreferences>().weeklyStandard) }
        single { AdaptiveVolumeSession(get(), CoroutineScope(SupervisorJob() + Dispatchers.Default), alerts = get()) }
    }

/** Bindings of every feature. Platform-specific bindings are added by each app through [initKoin]'s config. */
val appModules =
    listOf(
        dataModule,
        adaptiveVolumeModule,
        dashboardModule,
        exposureModule,
        hearingCheckModule,
        onboardingModule,
        profilesModule,
        settingsModule,
    )

/**
 * Starts dependency injection once per process: from `Application.onCreate` on Android and from `main` on
 * desktop, so that non-UI components such as the Android foreground service can use it too.
 */
fun initKoin(config: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        config()
        modules(appModules)
    }
