package com.waveheaven.lambda.feature.settings

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependency-injection bindings of the settings feature. */
val settingsModule =
    module {
        viewModel { SettingsViewModel(exposureLog = get(), preferences = get()) }
    }
