package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.audio.AmbientMeter
import com.waveheaven.lambda.core.domain.consent.Consent
import com.waveheaven.lambda.core.domain.exposure.DayBoundaries
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.DoseTracker
import com.waveheaven.lambda.core.domain.exposure.InMemoryExposureLog
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import com.waveheaven.lambda.core.domain.exposure.dBA
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolume
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeProblem
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.core.domain.volume.LinearOutputLevelModel
import com.waveheaven.lambda.core.domain.volume.MediaVolume
import com.waveheaven.lambda.core.domain.volume.OutputLevelModels
import com.waveheaven.lambda.core.domain.volume.OutputRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// advanceTimeBy is still experimental, but it is how virtual time is driven.
@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveVolumeSessionTest {
    private var micOpen = false
    private val consent = MutableStateFlow<Consent?>(Consent(Consent.CURRENT_VERSION, Instant.parse("2026-10-26T00:00:00Z")))

    @Test
    fun `starts stopped, runs, pauses without the microphone and resumes`() =
        runTest {
            val session = session()
            assertEquals(AdaptiveVolumeState.STOPPED, session.status.value.state)

            session.start()
            advanceTimeBy(3.seconds)
            assertEquals(AdaptiveVolumeState.RUNNING, session.status.value.state)
            assertTrue(micOpen)

            session.pause()
            advanceTimeBy(3.seconds)
            assertEquals(AdaptiveVolumeState.PAUSED, session.status.value.state)
            assertFalse(micOpen, "pausing must release the microphone (WH-023)")

            session.resume()
            advanceTimeBy(3.seconds)
            assertEquals(AdaptiveVolumeState.RUNNING, session.status.value.state)

            session.stop()
            advanceTimeBy(1.seconds)
            assertEquals(AdaptiveVolumeState.STOPPED, session.status.value.state)
            assertFalse(micOpen)
        }

    @Test
    fun `stops by itself and says why when consent is withdrawn`() =
        runTest {
            val session = session()
            session.start()
            advanceTimeBy(3.seconds)

            consent.value = null
            advanceTimeBy(2.seconds)

            assertEquals(AdaptiveVolumeState.STOPPED, session.status.value.state)
            assertEquals(AdaptiveVolumeProblem.NO_CONSENT, session.status.value.problem)
        }

    @Test
    fun `pause and resume do nothing when they do not apply`() =
        runTest {
            val session = session()

            session.pause()
            session.resume()
            assertEquals(AdaptiveVolumeState.STOPPED, session.status.value.state)

            session.start()
            session.start()
            advanceTimeBy(2.seconds)
            assertEquals(AdaptiveVolumeState.RUNNING, session.status.value.state)
            session.stop()
        }

    @Test
    fun `the unavailable implementation never runs`() {
        AdaptiveVolume.Unavailable.start()
        AdaptiveVolume.Unavailable.resume()
        AdaptiveVolume.Unavailable.pause()
        AdaptiveVolume.Unavailable.stop()

        assertEquals(AdaptiveVolumeState.UNAVAILABLE, AdaptiveVolume.Unavailable.status.value.state)
    }

    private fun TestScope.session(): AdaptiveVolumeSession {
        val meter =
            object : AmbientMeter {
                override fun levels(): Flow<SoundLevel> =
                    flow {
                        micOpen = true
                        try {
                            while (true) {
                                delay(1.seconds)
                                emit(70.dBA)
                            }
                        } finally {
                            micOpen = false
                        }
                    }
            }
        val volume =
            object : MediaVolume {
                private var index = 5
                override val indices = 0..15

                override fun currentRoute() = OutputRoute("speaker")

                override fun currentIndex() = index

                override fun setIndex(index: Int) {
                    this.index = index
                }

                override fun isMusicActive() = true
            }
        val engine =
            AdaptiveVolumeEngine(
                meter = meter,
                volume = volume,
                models = OutputLevelModels { LinearOutputLevelModel(15, 100.dBA, 2.0) },
                tracker = DoseTracker(InMemoryExposureLog(), DayBoundaries { Instant.parse("2026-10-26T05:00:00Z") }),
                inputs = LoopInputs(consent, MutableStateFlow(DoseStandard.WHO_ADULT), MutableStateFlow(ListeningProfile.COMMUTE)),
            )
        return AdaptiveVolumeSession(engine, backgroundScope)
    }
}
