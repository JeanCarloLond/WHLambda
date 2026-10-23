package com.waveheaven.lambda.shared

import com.waveheaven.lambda.feature.exposure.exposureModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/** Bindings of every feature. Platform-specific bindings are added by each app through [initKoin]'s config. */
val appModules = listOf(exposureModule)

/**
 * Starts dependency injection once per process: from `Application.onCreate` on Android and from `main` on
 * desktop, so that non-UI components such as the Android foreground service can use it too.
 */
fun initKoin(config: KoinAppDeclaration = {}): KoinApplication =
    startKoin {
        config()
        modules(appModules)
    }
