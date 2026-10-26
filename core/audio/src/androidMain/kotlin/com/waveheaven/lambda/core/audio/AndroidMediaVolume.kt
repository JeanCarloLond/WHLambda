package com.waveheaven.lambda.core.audio

import android.content.Context
import android.media.AudioManager
import com.waveheaven.lambda.core.domain.volume.MediaVolume
import com.waveheaven.lambda.core.domain.volume.OutputRoute

/** [MediaVolume] backed by `AudioManager.STREAM_MUSIC`, which controls the volume of every media app. */
class AndroidMediaVolume(
    context: Context,
) : MediaVolume {
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    override val indices: IntRange =
        audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)..audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    override fun currentRoute(): OutputRoute = AndroidMediaOutputs.routeOf(AndroidMediaOutputs.currentDevice(audio))

    override fun currentIndex(): Int = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun setIndex(index: Int) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, index.coerceIn(indices), 0)
    }

    override fun isMusicActive(): Boolean = audio.isMusicActive
}
