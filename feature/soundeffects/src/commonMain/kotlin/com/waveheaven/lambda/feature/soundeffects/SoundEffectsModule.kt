package com.waveheaven.lambda.feature.soundeffects

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependency-injection bindings of the equaliser and effects feature. Needs a platform `SoundEffects` binding. */
val soundEffectsModule =
    module {
        viewModel { SoundEffectsViewModel(store = get(), profiles = get(), effects = get()) }
    }
