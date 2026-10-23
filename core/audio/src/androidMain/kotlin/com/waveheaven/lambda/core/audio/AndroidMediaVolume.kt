package com.waveheaven.lambda.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.waveheaven.lambda.core.domain.volume.MediaVolume
import com.waveheaven.lambda.core.domain.volume.OutputRoute

/** [MediaVolume] backed by `AudioManager.STREAM_MUSIC`, which controls the volume of every media app. */
class AndroidMediaVolume(
    context: Context,
) : MediaVolume {
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    override val indices: IntRange =
        audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)..audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    override fun currentRoute(): OutputRoute {
        val device = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).maxByOrNull { routePriority(it.type) }
        return if (device == null || routePriority(device.type) == PRIORITY_SPEAKER) {
            SPEAKER
        } else {
            OutputRoute("${routeKind(device.type)}:${device.productName}")
        }
    }

    override fun currentIndex(): Int = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun setIndex(index: Int) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, index.coerceIn(indices), 0)
    }

    override fun isMusicActive(): Boolean = audio.isMusicActive

    private companion object {
        val SPEAKER = OutputRoute("speaker")

        const val PRIORITY_SPEAKER = 0
        const val PRIORITY_WIRED = 1
        const val PRIORITY_BLUETOOTH = 2

        val WIRED_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
            )

        /**
         * Media goes to the most "personal" connected output: Bluetooth or LE audio over wired/USB headsets over
         * the built-in speaker. A heuristic, but only used to keep separate controller state per output.
         */
        fun routePriority(type: Int): Int =
            when {
                type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> PRIORITY_BLUETOOTH
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET -> PRIORITY_BLUETOOTH
                type in WIRED_TYPES -> PRIORITY_WIRED
                else -> PRIORITY_SPEAKER
            }

        fun routeKind(type: Int): String = if (routePriority(type) == PRIORITY_BLUETOOTH) "bluetooth" else "wired"
    }
}
