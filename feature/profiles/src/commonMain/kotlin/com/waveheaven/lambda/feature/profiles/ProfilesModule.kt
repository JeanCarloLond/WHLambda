package com.waveheaven.lambda.feature.profiles

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependency-injection bindings of the profiles feature. */
val profilesModule =
    module {
        viewModel { ProfilesViewModel(store = get()) }
    }
