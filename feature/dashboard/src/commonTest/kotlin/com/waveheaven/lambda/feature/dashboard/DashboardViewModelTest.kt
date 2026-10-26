package com.waveheaven.lambda.feature.dashboard

import com.waveheaven.lambda.core.domain.exposure.DayBoundaries
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.DoseTracker
import com.waveheaven.lambda.core.domain.exposure.ExposureMinute
import com.waveheaven.lambda.core.domain.exposure.InMemoryExposureLog
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import com.waveheaven.lambda.core.domain.exposure.dBA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// setMain is still experimental, but it is the supported way to give viewModelScope a test dispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val log = InMemoryExposureLog()
    private val bogota = TimeZone.of("America/Bogota")
    private val tracker = DoseTracker(log, DayBoundaries { Instant.fromEpochSeconds(((it.epochSeconds - OFFSET) / DAY) * DAY + OFFSET) })
    private val now = Instant.parse("2026-10-26T20:00:00Z") // Monday, 15:00 in Bogotá
    private val clock =
        object : Clock {
            override fun now() = now
        }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `nothing recorded shows empty budgets and no level`() =
        runTest {
            val state = observe(viewModel())

            assertFalse(state.value.isLoading)
            assertEquals(BudgetUi(DoseStandard.NIOSH, 0, BudgetStatus.ON_TRACK, timeLeft = null), state.value.daily)
            assertNull(state.value.latestLevel)
            assertEquals(7, state.value.history.size)
        }

    @Test
    fun `shows percentages, status and time left at the latest level`() =
        runTest {
            listen(from = now - 2.hours, duration = 30.minutes, level = 94.dBA) // half of NIOSH's hour at 94 dB(A)

            val state = observe(viewModel()).value

            val daily = state.daily!!
            assertEquals(50, daily.percent)
            assertEquals(BudgetStatus.ON_TRACK, daily.status)
            assertEquals(30.0, daily.timeLeft!!.inWholeSeconds / 60.0, 0.5)
            assertEquals(LevelUi(94, 6), state.latestLevel)
            assertEquals(DoseStandard.WHO_ADULT, state.weekly!!.standard)
        }

    @Test
    fun `flags the budget close to and over the limit`() =
        runTest {
            listen(from = now - 3.hours, duration = 50.minutes, level = 94.dBA)
            assertEquals(BudgetStatus.CLOSE_TO_LIMIT, observe(viewModel()).value.daily!!.status)

            listen(from = now - 2.hours, duration = 20.minutes, level = 94.dBA)
            assertEquals(BudgetStatus.LIMIT_REACHED, observe(viewModel()).value.daily!!.status)
        }

    @Test
    fun `history labels the last 7 local days, today last`() =
        runTest {
            listen(from = now - 1.days, duration = 30.minutes, level = 94.dBA)

            val history = observe(viewModel()).value.history

            assertEquals(listOf(2, 3, 4, 5, 6, 7, 1), history.map { it.isoDayOfWeek }) // Tuesday … Monday
            assertEquals(listOf(false, false, false, false, false, false, true), history.map { it.isToday })
            assertEquals(50, history[5].percent)
        }

    @Test
    fun `reloads when new listening is recorded and follows the chosen weekly standard`() =
        runTest {
            val standard = MutableStateFlow(DoseStandard.WHO_ADULT)
            val state = observe(viewModel(weeklyStandard = standard))
            assertEquals(0, state.value.daily!!.percent)

            listen(from = now - 2.hours, duration = 30.minutes, level = 94.dBA)
            assertEquals(50, state.value.daily!!.percent)

            standard.value = DoseStandard.WHO_SENSITIVE
            assertEquals(DoseStandard.WHO_SENSITIVE, state.value.weekly!!.standard)
        }

    @Test
    fun `percentages are written out, capped for display`() {
        assertEquals("42 %", formatPercent(42))
        assertEquals("> 999 %", formatPercent(1500))
    }

    private fun viewModel(weeklyStandard: MutableStateFlow<DoseStandard> = MutableStateFlow(DoseStandard.WHO_ADULT)) =
        DashboardViewModel(tracker, log, clock, { bogota }, weeklyStandard, ticks = flowOf(Unit))

    private fun TestScope.observe(viewModel: DashboardViewModel) =
        viewModel.state.also { state -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect {} } }

    private suspend fun listen(
        from: Instant,
        duration: Duration,
        level: SoundLevel,
    ) {
        var t = from
        while (t < from + duration) {
            tracker.record(ExposureMinute(t, ExposureMinute.MINUTE, level, 6.0, null))
            t += ExposureMinute.MINUTE
        }
    }

    private companion object {
        const val DAY = 86_400L
        const val OFFSET = 5 * 3_600L
    }
}
