package com.waveheaven.lambda.feature.dashboard

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependency-injection bindings of the dashboard feature. */
val dashboardModule =
    module {
        viewModel { DashboardViewModel(tracker = get(), log = get()) }
    }
