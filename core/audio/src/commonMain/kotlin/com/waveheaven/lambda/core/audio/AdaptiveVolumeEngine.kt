package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.audio.AmbientMeter
import com.waveheaven.lambda.core.domain.audio.MicrophoneUnavailableException
import com.waveheaven.lambda.core.domain.consent.Consent
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.consent.allowsMetering
import com.waveheaven.lambda.core.domain.exposure.DoseReport
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.DoseTracker
import com.waveheaven.lambda.core.domain.exposure.ExposureAccumulator
import com.waveheaven.lambda.core.domain.exposure.ExposureMinute
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import com.waveheaven.lambda.core.domain.profile.ListeningProfile
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeProblem
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeStatus
import com.waveheaven.lambda.core.domain.volume.MediaVolume
import com.waveheaven.lambda.core.domain.volume.OutputLevelModel
import com.waveheaven.lambda.core.domain.volume.OutputLevelModels
import com.waveheaven.lambda.core.domain.volume.OutputRoute
import com.waveheaven.lambda.core.domain.volume.VolumeController
import com.waveheaven.lambda.core.domain.volume.VolumeControllerConfig
import com.waveheaven.lambda.core.domain.volume.VolumeTick
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The user's choices the loop follows live: consent, the weekly standard and the listening profile. */
class LoopInputs(
    val consent: Flow<Consent?>,
    val weeklyStandard: Flow<DoseStandard>,
    val profile: Flow<ListeningProfile>,
) {
    constructor(preferences: UserPreferences, profiles: ListeningProfileStore) :
        this(preferences.consent, preferences.weeklyStandard, profiles.activeProfile())
}

/**
 * One run of the adaptive-volume loop (#10): every second, meter → [VolumeController] → system volume, and record the
 * estimated exposure per minute.
 *
 * - The microphone is open only while music plays (spike #2, D5): when playback stops the meter flow is cancelled,
 *   which releases it, and the loop polls the playback state once per second until music returns.
 * - Consent, weekly standard and profile are followed live; without consent for the current text the run ends.
 * - Samples never leave [AmbientMeter]; only levels and per-minute aggregates do (ADR-0007).
 */
class AdaptiveVolumeEngine(
    private val meter: AmbientMeter,
    private val volume: MediaVolume,
    models: OutputLevelModels,
    private val tracker: DoseTracker,
    private val inputs: LoopInputs,
    private val clock: Clock = Clock.System,
) {
    private val modelCache = mutableMapOf<OutputRoute, OutputLevelModel>()
    private val models = OutputLevelModels { route -> modelCache.getOrPut(route) { models.modelFor(route) } }

    /**
     * Runs until cancelled or until it cannot go on, reporting progress through [onStatus].
     *
     * @return why the run ended on its own.
     */
    suspend fun run(onStatus: suspend (AdaptiveVolumeStatus) -> Unit): AdaptiveVolumeProblem =
        coroutineScope {
            val settings = combine(inputs.consent, inputs.weeklyStandard, inputs.profile, ::Settings).stateIn(this)
            val loop = Loop(settings)
            try {
                loop.run(onStatus)
            } finally {
                withContext(NonCancellable) { loop.flush() }
                // The settings collector would otherwise keep this scope, and the run, alive.
                coroutineContext.cancelChildren()
            }
        }

    private data class Settings(
        val consent: Consent?,
        val weeklyStandard: DoseStandard,
        val profile: ListeningProfile,
    ) {
        fun configFor(indices: IntRange): VolumeControllerConfig =
            profile.applyTo(VolumeControllerConfig(standard = weeklyStandard), indices)
    }

    /** State of one run. */
    private inner class Loop(
        private val settings: StateFlow<Settings>,
    ) {
        private val startedAt = TimeSource.Monotonic.markNow()
        private val controller = VolumeController(models, settings.value.configFor(volume.indices))
        private val accumulator = ExposureAccumulator()
        private lateinit var doses: DoseReport

        suspend fun run(onStatus: suspend (AdaptiveVolumeStatus) -> Unit): AdaptiveVolumeProblem {
            doses = tracker.report(clock.now(), settings.value.weeklyStandard)
            while (true) {
                val problem =
                    when {
                        !settings.value.consent.allowsMetering() -> AdaptiveVolumeProblem.NO_CONSENT
                        volume.needsSetup() -> AdaptiveVolumeProblem.NO_MEDIA_APP
                        volume.isMusicActive() -> meterWhilePlaying(onStatus)
                        else -> waitForMusic(onStatus)
                    }
                if (problem != null) return problem
            }
        }

        private suspend fun waitForMusic(onStatus: suspend (AdaptiveVolumeStatus) -> Unit): AdaptiveVolumeProblem? {
            flush()
            onStatus(status(AdaptiveVolumeState.WAITING_FOR_MUSIC))
            delay(IDLE_POLL_INTERVAL)
            return null
        }

        /** Leaving this function cancels the meter flow, which releases the microphone. */
        private suspend fun meterWhilePlaying(onStatus: suspend (AdaptiveVolumeStatus) -> Unit): AdaptiveVolumeProblem? =
            // The failure is reported as a problem the UI explains; its message adds nothing for the user.
            @Suppress("SwallowedException")
            try {
                meter
                    .levels()
                    .takeWhile { volume.isMusicActive() && settings.value.consent.allowsMetering() }
                    .collect { ambient -> onStatus(tick(ambient.coerceAtLeast(QUIETEST_MEASURABLE))) }
                null
            } catch (e: MicrophoneUnavailableException) {
                AdaptiveVolumeProblem.MICROPHONE_UNAVAILABLE
            }

        private suspend fun tick(ambient: SoundLevel): AdaptiveVolumeStatus {
            val current = settings.value
            controller.reconfigure(current.configFor(volume.indices))
            val route = volume.currentRoute()
            val model = models.modelFor(route)
            val index = volume.currentIndex()
            val pending = accumulator.peek()
            val weeklyDose = doses.weekly.dose + current.weeklyStandard.doseOfMinute(pending)
            val decision = controller.update(VolumeTick(startedAt.elapsedNow(), route, ambient, index, isMusicActive = true, weeklyDose))
            decision.newIndex?.let(volume::setIndex)
            val output = model.levelAt(decision.newIndex ?: index)
            // A reading covers the second before it arrived, so it counts towards the minute that second belongs to.
            accumulator.add(clock.now() - READING_DURATION, output, model.uncertaintyDb, ambient)?.let { record(it) }
            return status(AdaptiveVolumeState.RUNNING).copy(
                ambient = ambient,
                output = output,
                outputUncertaintyDb = model.uncertaintyDb,
                decision = decision,
            )
        }

        suspend fun flush() {
            accumulator.flush()?.let { record(it) }
        }

        private suspend fun record(minute: ExposureMinute) {
            tracker.record(minute)
            doses = tracker.report(clock.now(), settings.value.weeklyStandard)
        }

        private fun status(state: AdaptiveVolumeState): AdaptiveVolumeStatus {
            val pending = accumulator.peek()
            return AdaptiveVolumeStatus(
                state = state,
                dailyDose = doses.daily.dose + DoseStandard.NIOSH.doseOfMinute(pending),
                weeklyDose = doses.weekly.dose + doses.weekly.standard.doseOfMinute(pending),
            )
        }

        private fun DoseStandard.doseOfMinute(minute: ExposureMinute?) = doseOf(listOfNotNull(minute?.toSegment()))
    }

    private companion object {
        /** How often playback is checked while waiting for music; the microphone is closed meanwhile. */
        val IDLE_POLL_INTERVAL = 1.seconds

        /**
         * No phone or laptop microphone measures below its own noise floor, around 30 dB(A); quieter readings (digital
         * silence, a muted input) mean "very quiet", not −10 dB(A).
         */
        val QUIETEST_MEASURABLE = SoundLevel(30.0)

        /** [AmbientMeter] delivers one level per second. */
        val READING_DURATION = 1.seconds
    }
}
