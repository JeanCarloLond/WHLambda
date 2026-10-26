package com.waveheaven.lambda.feature.dashboard

import androidx.navigation3.runtime.NavKey
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
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
)

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
