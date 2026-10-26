package com.waveheaven.lambda.core.audio

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.waveheaven.lambda.core.domain.volume.OutputRoute

/**
 * Which output media plays on. Media goes to the most "personal" connected output: Bluetooth or LE audio over
 * wired/USB headsets over the built-in speaker. A heuristic, used to keep separate state and models per output.
 */
internal object AndroidMediaOutputs {
    val SPEAKER = OutputRoute("speaker")

    /** The current media output, or `null` for the built-in speaker. */
    fun currentDevice(audio: AudioManager): AudioDeviceInfo? =
        audio
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .maxByOrNull { priority(it.type) }
            ?.takeIf { priority(it.type) > PRIORITY_SPEAKER }

    fun routeOf(device: AudioDeviceInfo?): OutputRoute =
        if (device == null) SPEAKER else OutputRoute("${kind(device.type)}:${device.productName}")

    private const val PRIORITY_SPEAKER = 0
    private const val PRIORITY_WIRED = 1
    private const val PRIORITY_BLUETOOTH = 2

    private val WIRED_TYPES =
        setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )

    private fun priority(type: Int): Int =
        when {
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> PRIORITY_BLUETOOTH
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET -> PRIORITY_BLUETOOTH
            type in WIRED_TYPES -> PRIORITY_WIRED
            else -> PRIORITY_SPEAKER
        }

    private fun kind(type: Int): String = if (priority(type) == PRIORITY_BLUETOOTH) "bluetooth" else "wired"
}
