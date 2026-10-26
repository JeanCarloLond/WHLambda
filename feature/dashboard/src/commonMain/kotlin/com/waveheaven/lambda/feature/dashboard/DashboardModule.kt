package com.waveheaven.lambda.feature.dashboard

import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependency-injection bindings of the dashboard feature. */
val dashboardModule =
    module {
        viewModel {
            DashboardViewModel(tracker = get(), log = get(), activeProfile = get<ListeningProfileStore>().activeProfile())
        }
    }
