package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.audio.AmbientMeter
import com.waveheaven.lambda.core.domain.audio.MicrophoneUnavailableException
import com.waveheaven.lambda.core.domain.consent.Consent
import com.waveheaven.lambda.core.domain.exposure.DayBoundaries
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.DoseTracker
import com.waveheaven.lambda.core.domain.exposure.InMemoryExposureLog
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import com.waveheaven.lambda.core.domain.exposure.dBA
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeProblem
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeStatus
import com.waveheaven.lambda.core.domain.volume.LinearOutputLevelModel
import com.waveheaven.lambda.core.domain.volume.MediaVolume
import com.waveheaven.lambda.core.domain.volume.OutputLevelModels
import com.waveheaven.lambda.core.domain.volume.OutputRoute
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/*
 * The engine runs on virtual time: the fake meter emits one level per virtual second, as AndroidAmbientMeter does in
 * real time. Output model: 16 steps, 2 dB per step, index 15 = 100 dB(A) ± 6 dB.
 */

// advanceTimeBy and runCurrent are still experimental, but they are how virtual time is driven.
@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveVolumeEngineTest {
    private val phone = FakeMediaVolume(level = 5)
    private val meter = FakeMeter()
    private val log = InMemoryExposureLog()
    private val consent = MutableStateFlow<Consent?>(Consent(Consent.CURRENT_VERSION, Instant.parse("2026-10-26T00:00:00Z")))
    private val profile = MutableStateFlow(ListeningProfile.COMMUTE)
    private val statuses = mutableListOf<AdaptiveVolumeStatus>()

    @Test
    fun `raises the volume in rising noise and reports what it does`() =
        runTest {
            val run = start()

            advanceTimeBy(20.seconds)

            assertTrue(phone.level > 5, "index ${phone.level}")
            val last = statuses.last()
            assertEquals(AdaptiveVolumeState.RUNNING, last.state)
            assertEquals(85.0, last.ambient!!.decibels, 1e-9)
            assertEquals(6.0, last.outputUncertaintyDb)
            assertTrue(statuses.any { it.decision?.reason == VolumeDecisionReason.RAISE_FOR_NOISE })
            run.cancel()
        }

    @Test
    fun `the microphone is open only while music plays`() =
        runTest {
            phone.playing = false
            val run = start()
            advanceTimeBy(5.seconds)
            assertEquals(0, meter.opened)
            assertEquals(AdaptiveVolumeState.WAITING_FOR_MUSIC, statuses.last().state)

            phone.playing = true
            advanceTimeBy(5.seconds)
            assertEquals(1, meter.opened)
            assertTrue(meter.isOpen)

            phone.playing = false
            advanceTimeBy(3.seconds)
            assertTrue(!meter.isOpen, "the microphone must be released when music stops")
            run.cancel()
        }

    @Test
    fun `listening is recorded per minute, and the minute in progress when it stops`() =
        runTest {
            val run = start()
            advanceTimeBy(90.seconds)
            run.cancel()
            runCurrent()

            val minutes = log.minutesSince(Instant.DISTANT_PAST)
            assertEquals(2, minutes.size)
            assertEquals(
                60,
                minutes
                    .first()
                    .listening.inWholeSeconds
                    .toInt(),
                "a full minute of 1 s ticks",
            )
            assertEquals(85.0, minutes.first().ambient!!.decibels, 1e-9)
            assertEquals(6.0, minutes.first().outputUncertaintyDb)
        }

    @Test
    fun `withdrawing consent ends the run and releases the microphone`() =
        runTest {
            var problem: AdaptiveVolumeProblem? = null
            launch { problem = engine().run { statuses += it } }
            advanceTimeBy(5.seconds)

            consent.value = null
            advanceTimeBy(2.seconds)

            assertEquals(AdaptiveVolumeProblem.NO_CONSENT, problem)
            assertTrue(!meter.isOpen)
        }

    @Test
    fun `readings below what a microphone can measure count as very quiet`() =
        runTest {
            meter.level = SoundLevel(-8.0) // digital silence through the nominal calibration
            val run = start()

            advanceTimeBy(5.seconds)

            assertEquals(30.0, statuses.last().ambient!!.decibels, 1e-9)
            run.cancel()
        }

    @Test
    fun `without consent it does not even start`() =
        runTest {
            consent.value = null

            val problem = engine().run { statuses += it }

            assertEquals(AdaptiveVolumeProblem.NO_CONSENT, problem)
            assertEquals(0, meter.opened)
        }

    @Test
    fun `an unavailable microphone ends the run`() =
        runTest {
            meter.fails = true

            val problem = engine().run { statuses += it }

            assertEquals(AdaptiveVolumeProblem.MICROPHONE_UNAVAILABLE, problem)
        }

    @Test
    fun `a new profile applies from the next tick`() =
        runTest {
            phone.level = 12
            val run = start()
            advanceTimeBy(5.seconds)

            profile.value = ListeningProfile.OFFICE // maximum 60 % of 15 steps = index 9
            advanceTimeBy(2.seconds)

            assertEquals(9, phone.level)
            assertTrue(statuses.any { it.decision?.reason == VolumeDecisionReason.LOWER_TO_PROFILE_LIMIT })
            run.cancel()
        }

    @Test
    fun `reports today's and this week's dose including the minute in progress`() =
        runTest {
            phone.level = 13 // 96 dB(A): about 1.6 % of NIOSH's daily budget per minute
            val run = start()
            advanceTimeBy(30.seconds)

            val last = statuses.last()
            assertTrue(last.dailyDose!!.fraction > 0.0)
            assertTrue(last.weeklyDose!!.fraction > 0.0)
            run.cancel()
        }

    private fun TestScope.start() = launch { engine().run { statuses += it } }

    private fun TestScope.engine(): AdaptiveVolumeEngine {
        val scheduler = testScheduler
        val clock =
            object : Clock {
                override fun now() = Instant.parse("2026-10-26T15:00:00Z") + scheduler.currentTime.milliseconds
            }
        return AdaptiveVolumeEngine(
            meter = meter,
            volume = phone,
            models = OutputLevelModels { MODEL },
            tracker = DoseTracker(log, DayBoundaries { Instant.parse("2026-10-26T05:00:00Z") }),
            inputs = LoopInputs(consent, MutableStateFlow(DoseStandard.WHO_ADULT), profile),
            clock = clock,
        )
    }

    private class FakeMediaVolume(
        var level: Int,
    ) : MediaVolume {
        var playing = true
        override val indices = 0..15

        override fun currentRoute() = OutputRoute("speaker")

        override fun currentIndex() = level

        override fun setIndex(index: Int) {
            level = index
        }

        override fun isMusicActive() = playing
    }

    private class FakeMeter : AmbientMeter {
        var opened = 0
        var isOpen = false
        var fails = false
        var level = 85.dBA

        override fun levels(): Flow<SoundLevel> =
            flow {
                if (fails) throw MicrophoneUnavailableException("denied")
                opened++
                isOpen = true
                try {
                    while (true) {
                        delay(1.seconds)
                        emit(level)
                    }
                } finally {
                    isOpen = false
                }
            }
    }

    private companion object {
        val MODEL = LinearOutputLevelModel(maxIndex = 15, levelAtMax = 100.dBA, decibelsPerStep = 2.0, uncertaintyDb = 6.0)
    }
}
