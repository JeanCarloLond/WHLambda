package com.waveheaven.lambda.feature.hearingcheck

import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.core.domain.hearing.HearingCheckStore
import com.waveheaven.lambda.core.domain.hearing.HearingResult.Assessment
import com.waveheaven.lambda.core.domain.hearing.StoredHearingCheck
import com.waveheaven.lambda.core.domain.hearing.TonePlayer
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// setMain and advanceUntilIdle are still experimental; they drive viewModelScope on virtual time.
@OptIn(ExperimentalCoroutinesApi::class)
class HearingCheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeStore()
    private val profiles = FakeProfiles()
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-10-27T03:00:00Z") // 26 October in Bogotá
        }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `runs the whole check, stores it and shows the result`() =
        test {
            // Hears everything down to −45 dB, except 6 kHz on the left, only down to −25 dB.
            val player = ListenerPlayer { ear, f -> if (ear == Ear.LEFT && f == 6000) -25.0 else -45.0 }
            val viewModel = viewModel(player)

            viewModel.onAction(HearingCheckAction.Begin)
            assertEquals(HearingStep.VOLUME, viewModel.state.value.step)
            viewModel.onAction(HearingCheckAction.StartTest)
            assertEquals(HearingStep.TESTING, viewModel.state.value.step)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(HearingStep.RESULT, state.step)
            val result = state.result!!
            assertEquals("2026-10-26", result.date)
            assertEquals(8, result.rows.size)
            assertEquals(
                -45.0,
                result.rows
                    .first { it.frequencyHz == 1000 }
                    .right!!
                    .toDouble(),
                5.0,
            )
            assertEquals(
                -25.0,
                result.rows
                    .first { it.frequencyHz == 6000 }
                    .left!!
                    .toDouble(),
                5.0,
            )
            assertEquals(Assessment.EVEN, result.assessment)
            assertNotNull(store.saved)
            assertTrue(player.tones > 0)
        }

    @Test
    fun `a press outside a tone does not count`() =
        test {
            val viewModel = viewModel(ListenerPlayer { _, _ -> 100.0 }) // hears nothing
            viewModel.onAction(HearingCheckAction.StartTest)
            viewModel.onAction(HearingCheckAction.Heard) // before any tone

            advanceUntilIdle()

            assertTrue(
                viewModel.state.value.result!!
                    .rows
                    .all { it.right == null && it.left == null },
            )
            assertEquals(
                Assessment.UNEVEN,
                viewModel.state.value.result!!
                    .assessment,
            )
        }

    @Test
    fun `cancelling stops the tones and returns to the intro`() =
        test {
            val player = ListenerPlayer { _, _ -> -45.0 }
            val viewModel = viewModel(player)
            viewModel.onAction(HearingCheckAction.StartTest)
            testScheduler.advanceTimeBy(10_000)

            viewModel.onAction(HearingCheckAction.Cancel)
            val tones = player.tones
            advanceUntilIdle()

            assertEquals(HearingStep.INTRO, viewModel.state.value.step)
            assertEquals(tones, player.tones)
            assertNull(store.saved)
        }

    @Test
    fun `the result becomes the EQ of a new, selected profile`() =
        test {
            val viewModel = viewModel(ListenerPlayer { _, f -> if (f >= 4000) -20.0 else -50.0 })
            viewModel.onAction(HearingCheckAction.StartTest)
            advanceUntilIdle()

            viewModel.onAction(HearingCheckAction.SaveAsProfile("My hearing"))
            advanceUntilIdle()

            val saved = profiles.saved.single()
            assertEquals("My hearing", saved.name)
            assertTrue(saved.eq!!.gainAt(6000.0) > saved.eq!!.gainAt(1000.0))
            assertEquals(saved.id, profiles.active.value)
            assertTrue(viewModel.state.value.savedAsProfile)
        }

    @Test
    fun `shows the last result on the intro and can delete it`() =
        test {
            val viewModel = viewModel(ListenerPlayer { _, _ -> -45.0 })
            viewModel.onAction(HearingCheckAction.StartTest)
            advanceUntilIdle()
            val reopened = viewModel(ListenerPlayer { _, _ -> -45.0 })
            advanceUntilIdle()
            assertEquals(HearingStep.INTRO, reopened.state.value.step)
            assertNotNull(reopened.state.value.result)

            reopened.onAction(HearingCheckAction.DeleteResults)
            advanceUntilIdle()

            assertNull(store.saved)
            assertNull(reopened.state.value.result)
        }

    @Test
    fun `the reference tone plays in both ears at the reference level`() =
        test {
            val player = ListenerPlayer { _, _ -> -45.0 }
            viewModel(player).onAction(HearingCheckAction.PlayReference)
            advanceUntilIdle()

            assertEquals(listOf(Triple(null, 1000, 0.0)), player.played.take(1))
        }

    private fun test(block: suspend TestScope.() -> Unit) =
        runTest(dispatcher) {
            Dispatchers.setMain(dispatcher)
            block()
        }

    /** A listener who presses "I hear it" during tones at or above their threshold. */
    private inner class ListenerPlayer(
        private val threshold: (Ear, Int) -> Double,
    ) : TonePlayer {
        var tones = 0
        val played = mutableListOf<Triple<Ear?, Int, Double>>()

        override suspend fun play(
            ear: Ear?,
            frequencyHz: Int,
            levelDb: Double,
            durationMillis: Long,
        ) {
            played += Triple(ear, frequencyHz, levelDb)
            if (ear != null) tones++
            delay(durationMillis / 2)
            if (ear != null && levelDb >= threshold(ear, frequencyHz)) listener?.onAction(HearingCheckAction.Heard)
            delay(durationMillis / 2)
        }
    }

    private var listener: HearingCheckViewModel? = null

    private fun viewModel(player: ListenerPlayer): HearingCheckViewModel =
        HearingCheckViewModel(store, player, profiles, clock, Random(3)) { TimeZone.of("America/Bogota") }.also { listener = it }

    private class FakeStore : HearingCheckStore {
        var saved: StoredHearingCheck? = null

        override suspend fun save(check: StoredHearingCheck) {
            saved = check
        }

        override suspend fun latest() = saved

        override suspend fun deleteAll() {
            saved = null
        }
    }

    private class FakeProfiles : ListeningProfileStore {
        private val custom = MutableStateFlow<List<ListeningProfile>>(emptyList())
        val active = MutableStateFlow(ListeningProfile.DEFAULT.id)
        val saved = mutableListOf<ListeningProfile>()

        override fun profiles(): Flow<List<ListeningProfile>> = custom.map { ListeningProfile.BUILT_IN + it }

        override fun activeProfile(): Flow<ListeningProfile> =
            combine(profiles(), active) { all, id -> all.firstOrNull { it.id == id } ?: ListeningProfile.DEFAULT }

        override suspend fun save(profile: ListeningProfile) {
            saved += profile
            custom.value += profile
        }

        override suspend fun delete(id: String) = Unit

        override suspend fun setActive(id: String) {
            active.value = id
        }
    }
}
