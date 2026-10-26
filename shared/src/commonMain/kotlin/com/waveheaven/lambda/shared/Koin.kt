package com.waveheaven.lambda.shared

import com.waveheaven.lambda.core.data.SqlExposureLog
import com.waveheaven.lambda.core.data.SystemDayBoundaries
import com.waveheaven.lambda.core.data.createDatabase
import com.waveheaven.lambda.core.domain.exposure.DayBoundaries
import com.waveheaven.lambda.core.domain.exposure.DoseTracker
import com.waveheaven.lambda.core.domain.exposure.ExposureLog
import com.waveheaven.lambda.feature.dashboard.dashboardModule
import com.waveheaven.lambda.feature.exposure.exposureModule
import com.waveheaven.lambda.feature.settings.settingsModule
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
    }

/** Bindings of every feature. Platform-specific bindings are added by each app through [initKoin]'s config. */
val appModules = listOf(dataModule, dashboardModule, exposureModule, settingsModule)

/**
 * Starts dependency injection once per process: from `Application.onCreate` on Android and from `main` on
 * desktop, so that non-UI components such as the Android foreground service can use it too.
 */
fun initKoin(config: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        config()
        modules(appModules)
    }
