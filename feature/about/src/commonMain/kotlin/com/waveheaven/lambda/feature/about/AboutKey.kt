package com.waveheaven.lambda.feature.about

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Navigation destination for the about screen. */
@Serializable
data object AboutKey : NavKey

/** Public repository, opened from the about screen. */
const val SOURCE_CODE_URL: String = "https://github.com/JeanCarloLond/WHLambda"
