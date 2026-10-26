package com.waveheaven.lambda.feature.onboarding

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Dependency-injection bindings of the onboarding feature. */
val onboardingModule =
    module {
        viewModel { (reviewConsentOnly: Boolean) -> OnboardingViewModel(reviewConsentOnly, preferences = get()) }
    }
