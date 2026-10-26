package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.volume.AdaptiveVolume
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Start, pause, resume and stop around [AdaptiveVolumeEngine] runs, in [scope]. Pausing cancels the run, which
 * releases the microphone; resuming starts a new run with the same settings (WH-023).
 *
 * Methods may be called from any thread. Status updates from a run that has been paused or stopped are ignored.
 */
class AdaptiveVolumeSession(
    private val engine: AdaptiveVolumeEngine,
    private val scope: CoroutineScope,
) : AdaptiveVolume {
    private val mutableStatus = MutableStateFlow(AdaptiveVolumeStatus(AdaptiveVolumeState.STOPPED))
    private var job: Job? = null

    /** Identifies the current run; updates from older runs are dropped. */
    @Volatile
    private var generation = 0

    override val status: StateFlow<AdaptiveVolumeStatus> = mutableStatus.asStateFlow()

    override fun start() {
        val state = mutableStatus.value.state
        if (state == AdaptiveVolumeState.RUNNING || state == AdaptiveVolumeState.WAITING_FOR_MUSIC) return
        launchRun()
    }

    override fun pause() {
        val state = mutableStatus.value.state
        if (state != AdaptiveVolumeState.RUNNING && state != AdaptiveVolumeState.WAITING_FOR_MUSIC) return
        cancelRun()
        mutableStatus.value = AdaptiveVolumeStatus(AdaptiveVolumeState.PAUSED)
    }

    override fun resume() {
        if (mutableStatus.value.state == AdaptiveVolumeState.PAUSED) launchRun()
    }

    override fun stop() {
        cancelRun()
        mutableStatus.value = AdaptiveVolumeStatus(AdaptiveVolumeState.STOPPED)
    }

    private fun launchRun() {
        cancelRun()
        val run = generation
        mutableStatus.value = AdaptiveVolumeStatus(AdaptiveVolumeState.WAITING_FOR_MUSIC)
        job =
            scope.launch {
                val problem = engine.run { status -> if (run == generation) mutableStatus.value = status }
                if (run == generation) mutableStatus.value = AdaptiveVolumeStatus(AdaptiveVolumeState.STOPPED, problem = problem)
            }
    }

    private fun cancelRun() {
        generation++
        job?.cancel()
        job = null
    }
}
