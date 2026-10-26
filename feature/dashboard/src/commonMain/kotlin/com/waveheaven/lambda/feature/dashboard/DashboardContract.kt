package com.waveheaven.lambda.feature.dashboard

import androidx.navigation3.runtime.NavKey
import com.waveheaven.lambda.core.domain.consent.ConsentStatus
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeProblem
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/** Navigation destination for the dashboard, the app's home screen. */
@Serializable
data object DashboardKey : NavKey

/** Everything the dashboard renders. Immutable; produced only by [DashboardViewModel]. */
data class DashboardUiState(
    val isLoading: Boolean = true,
    /** Today against NIOSH. */
    val daily: BudgetUi? = null,
    /** The last 7 days against the chosen WHO standard. */
    val weekly: BudgetUi? = null,
    /** Most recent estimated output level, used for "time left". `null` until something was recorded. */
    val latestLevel: LevelUi? = null,
    /** Daily NIOSH dose of the last 7 local days, oldest first. */
    val history: List<HistoryDayUi> = emptyList(),
    /** The listening profile in use. */
    val profile: ActiveProfileUi? = null,
    /** Without consent for the current text the app does not measure; the dashboard explains why (WH-074). */
    val consentStatus: ConsentStatus = ConsentStatus.GIVEN,
    val adaptive: AdaptiveUi = AdaptiveUi(AdaptiveVolumeState.UNAVAILABLE),
)

/** The adaptive-volume loop as the dashboard shows it. */
data class AdaptiveUi(
    val state: AdaptiveVolumeState,
    val ambientDb: Int? = null,
    /** Estimated level at the ear right now, with its margin (WH-032). */
    val output: LevelUi? = null,
    val hint: AdaptiveHint? = null,
    val problem: AdaptiveVolumeProblem? = null,
    /** The user refused the microphone when starting. */
    val microphoneDenied: Boolean = false,
)

/** Why the volume is where it is, when the user should know (WH-014). */
enum class AdaptiveHint {
    /** The remaining dose keeps the volume below what the noise calls for. */
    LIMITED_BY_DOSE,

    /** The user changed the volume; the app leaves it alone for a while. */
    MANUAL_OVERRIDE,

    /** The user set the volume above the safe limit; the app does not fight it but says so. */
    ABOVE_LIMIT_BY_USER,
}

/** User intents (and permission answers) the dashboard understands. */
sealed interface DashboardAction {
    data object Start : DashboardAction

    data object Pause : DashboardAction

    data object Resume : DashboardAction

    data object Stop : DashboardAction

    /** The microphone permission was refused when starting. */
    data object MicrophoneDenied : DashboardAction
}

data class ActiveProfileUi(
    val id: String,
    /** The user's name for their own profiles; built-in ones are named by the screen in the user's language. */
    val name: String,
    val isBuiltIn: Boolean,
)

/** How much of one standard's budget is used. */
data class BudgetUi(
    val standard: DoseStandard,
    val percent: Int,
    val status: BudgetStatus,
    /** Listening time left at [DashboardUiState.latestLevel]; `null` when no level is known yet. */
    val timeLeft: Duration?,
)

enum class BudgetStatus {
    /** Below 80 %. */
    ON_TRACK,

    /** 80 % or more: the last alert threshold before the limit. */
    CLOSE_TO_LIMIT,

    /** 100 % or more. */
    LIMIT_REACHED,
}

/** An estimated level at the ear and its ± margin, both rounded to whole decibels. */
data class LevelUi(
    val decibels: Int,
    val marginDb: Int,
)

data class HistoryDayUi(
    /** ISO day of the week: 1 = Monday … 7 = Sunday. */
    val isoDayOfWeek: Int,
    val percent: Int,
    val isToday: Boolean,
)
