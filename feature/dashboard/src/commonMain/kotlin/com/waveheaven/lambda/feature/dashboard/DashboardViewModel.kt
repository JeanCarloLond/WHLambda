package com.waveheaven.lambda.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waveheaven.lambda.core.domain.consent.ConsentStatus
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.consent.status
import com.waveheaven.lambda.core.domain.exposure.Dose
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.DoseTracker
import com.waveheaven.lambda.core.domain.exposure.ExposureLog
import com.waveheaven.lambda.core.domain.exposure.WindowDose
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The dashboard: dose used today (NIOSH) and over the last 7 days (WHO), time left at the latest level, and a
 * 7-day history. Reloads whenever the exposure log changes and once a minute, so day boundaries roll over.
 */
class DashboardViewModel(
    private val tracker: DoseTracker,
    log: ExposureLog,
    preferences: UserPreferences,
    activeProfile: Flow<ListeningProfile> = flowOf(ListeningProfile.DEFAULT),
    private val time: DashboardClock = DashboardClock(),
) : ViewModel() {
    val state: StateFlow<DashboardUiState> =
        combine(
            log.changes(),
            time.ticks,
            preferences.weeklyStandard,
            activeProfile,
            preferences.consent,
        ) { _, _, standard, profile, consent ->
            Inputs(standard, profile, consent.status())
        }.map { inputs ->
            load(inputs.weeklyStandard).copy(
                profile = ActiveProfileUi(inputs.profile.id, inputs.profile.name, inputs.profile.isBuiltIn),
                consentStatus = inputs.consentStatus,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), DashboardUiState())

    private suspend fun load(weeklyStandard: DoseStandard): DashboardUiState {
        val now = time.clock.now()
        val report = tracker.report(now, weeklyStandard)
        val latest = report.latest
        val history = tracker.dailyHistory(now, HISTORY_DAYS, DoseStandard.NIOSH)
        val zone = time.timeZone()
        return DashboardUiState(
            isLoading = false,
            daily = report.daily.toBudget(latest?.output?.let { report.daily.timeLeftAt(it) }),
            weekly = report.weekly.toBudget(latest?.output?.let { report.weekly.timeLeftAt(it) }),
            latestLevel = latest?.let { LevelUi(it.output.decibels.roundToInt(), it.outputUncertaintyDb.roundToInt()) },
            history =
                history.mapIndexed { i, day ->
                    HistoryDayUi(
                        isoDayOfWeek =
                            day.start
                                .toLocalDateTime(zone)
                                .dayOfWeek.isoDayNumber,
                        percent = day.dose.roundedPercent(),
                        isToday = i == history.lastIndex,
                    )
                },
        )
    }

    private fun WindowDose.toBudget(timeLeft: kotlin.time.Duration?): BudgetUi {
        val percent = dose.roundedPercent()
        val status =
            when {
                dose.fraction >= 1.0 -> BudgetStatus.LIMIT_REACHED
                dose.fraction >= CLOSE_TO_LIMIT -> BudgetStatus.CLOSE_TO_LIMIT
                else -> BudgetStatus.ON_TRACK
            }
        return BudgetUi(standard, percent, status, timeLeft)
    }

    private fun Dose.roundedPercent(): Int = percent.roundToInt()

    private data class Inputs(
        val weeklyStandard: DoseStandard,
        val profile: ListeningProfile,
        val consentStatus: ConsentStatus,
    )

    private companion object {
        const val HISTORY_DAYS = 7
        const val CLOSE_TO_LIMIT = 0.8
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Where the dashboard gets the time: the current instant, the user's time zone (read on every reload, so travelling
 * users get local days) and a tick to reload on, once a minute by default so day boundaries roll over.
 */
class DashboardClock(
    val clock: Clock = Clock.System,
    val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    val ticks: Flow<Unit> = everyMinute(),
) {
    private companion object {
        fun everyMinute(): Flow<Unit> =
            flow {
                while (true) {
                    emit(Unit)
                    delay(1.minutes)
                }
            }
    }
}
