package com.waveheaven.lambda.feature.exposure

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Dependency-injection bindings of the exposure feature. */
val exposureModule =
    module {
        viewModelOf(::DoseExplorerViewModel)
    }
