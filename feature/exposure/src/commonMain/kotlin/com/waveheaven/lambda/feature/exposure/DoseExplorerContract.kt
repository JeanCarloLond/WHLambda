package com.waveheaven.lambda.feature.exposure

import androidx.navigation3.runtime.NavKey
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import com.waveheaven.lambda.core.domain.exposure.dBA
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.time.Duration

/** Navigation destination for the dose explorer. */
@Serializable
data object DoseExplorerKey : NavKey

/** Everything the dose explorer screen renders. Immutable; produced only by [DoseExplorerViewModel]. */
data class DoseExplorerUiState(
    /** Raw slider position, kept so dragging stays smooth. */
    val sliderValue: Float,
    /** Level the safe times are computed for: the slider position rounded to whole decibels. */
    val level: SoundLevel,
    val nioshDailySafeTime: Duration,
    val whoWeeklySafeTime: Duration,
) {
    companion object {
        val LEVEL_RANGE: ClosedFloatingPointRange<Float> = 60f..110f
        const val DEFAULT_LEVEL_DBA: Float = 85f

        /** State for a slider position, clamped to [LEVEL_RANGE]. */
        fun at(sliderValue: Float): DoseExplorerUiState {
            val clamped = sliderValue.coerceIn(LEVEL_RANGE)
            val level = clamped.roundToInt().dBA
            return DoseExplorerUiState(
                sliderValue = clamped,
                level = level,
                nioshDailySafeTime = DoseStandard.NIOSH.allowedDuration(level),
                whoWeeklySafeTime = DoseStandard.WHO_ADULT.allowedDuration(level),
            )
        }
    }
}

/** User intents the dose explorer understands. */
sealed interface DoseExplorerAction {
    data class LevelChanged(
        val sliderValue: Float,
    ) : DoseExplorerAction
}
