package com.waveheaven.lambda.feature.hearingcheck

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependency-injection bindings of the hearing-check feature. Needs a platform `TonePlayer`. */
val hearingCheckModule =
    module {
        viewModel { HearingCheckViewModel(store = get(), player = get(), profiles = get()) }
    }
