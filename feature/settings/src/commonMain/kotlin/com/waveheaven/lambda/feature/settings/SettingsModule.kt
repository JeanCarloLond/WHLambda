package com.waveheaven.lambda.feature.settings

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dependency-injection bindings of the settings feature. */
val settingsModule =
    module {
        viewModelOf(::SettingsViewModel)
    }
