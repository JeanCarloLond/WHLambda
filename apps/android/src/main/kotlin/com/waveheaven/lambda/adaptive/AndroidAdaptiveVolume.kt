package com.waveheaven.lambda.adaptive

import android.content.Context
import com.waveheaven.lambda.core.audio.AdaptiveVolumeSession
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolume
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * [AdaptiveVolume] on Android: starting goes through [AdaptiveVolumeService], so the loop runs in a `microphone`
 * foreground service and survives the activity; the other commands go straight to the shared [session], which the
 * service watches.
 */
class AndroidAdaptiveVolume(
    private val context: Context,
    private val session: AdaptiveVolumeSession,
) : AdaptiveVolume {
    override val status: StateFlow<AdaptiveVolumeStatus> = session.status

    override fun start() {
        context.startForegroundService(AdaptiveVolumeService.intent(context, AdaptiveVolumeService.ACTION_START))
    }

    override fun pause() = session.pause()

    override fun resume() = session.resume()

    override fun stop() = session.stop()
}
