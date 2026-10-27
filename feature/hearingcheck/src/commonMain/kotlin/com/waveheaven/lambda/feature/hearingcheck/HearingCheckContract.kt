package com.waveheaven.lambda.feature.hearingcheck

import androidx.navigation3.runtime.NavKey
import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.core.domain.hearing.HearingResult.Assessment
import kotlinx.serialization.Serializable

/** Navigation destination for the hearing check. */
@Serializable
data object HearingCheckKey : NavKey

enum class HearingStep {
    /** What the check is and is not (WH-220), and the last result. */
    INTRO,

    /** The user sets a comfortable volume with the reference tone. */
    VOLUME,

    /** Tones play; the screen never shows when, so it cannot give the answer away. */
    TESTING,

    RESULT,
}

/** Everything the hearing check renders. Immutable; produced only by [HearingCheckViewModel]. */
data class HearingCheckUiState(
    val step: HearingStep = HearingStep.INTRO,
    val ear: Ear? = null,
    val progress: Float = 0f,
    /** The result just measured, or the last stored one on the intro. */
    val result: ResultUi? = null,
    val savedAsProfile: Boolean = false,
)

data class ResultUi(
    /** Local date of the check, `yyyy-mm-dd`. */
    val date: String,
    val rows: List<ThresholdRow>,
    val assessment: Assessment,
)

/** Thresholds in whole dB below the reference; `null` when the tone was not heard at all. */
data class ThresholdRow(
    val frequencyHz: Int,
    val right: Int?,
    val left: Int?,
)

/** User intents the hearing check understands. */
sealed interface HearingCheckAction {
    data object Begin : HearingCheckAction

    data object PlayReference : HearingCheckAction

    data object StartTest : HearingCheckAction

    data object Heard : HearingCheckAction

    data object Cancel : HearingCheckAction

    /** Saves the result as an EQ curve in a new listening profile named [name]. */
    data class SaveAsProfile(
        val name: String,
    ) : HearingCheckAction

    data object DeleteResults : HearingCheckAction
}
