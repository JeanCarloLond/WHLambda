package com.waveheaven.lambda.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.waveheaven.lambda.core.domain.volume.CurveOutputLevelModel
import com.waveheaven.lambda.core.domain.volume.HeadphoneProfile
import com.waveheaven.lambda.core.domain.volume.OutputLevelModel
import com.waveheaven.lambda.core.domain.volume.OutputLevelModels
import com.waveheaven.lambda.core.domain.volume.OutputRoute
import com.waveheaven.lambda.core.domain.volume.VolumeCurves

/**
 * Estimates the level at the ear for the current media output (#11): the [HeadphoneProfile] of the route plus the
 * attenuation Android applies at each index for that output (`AudioManager.getStreamVolumeDb`).
 *
 * With Bluetooth absolute volume the phone does not attenuate (the headphones do), so the reported curve is flat; then
 * Android's reference media curve stands in, with a wider margin.
 *
 * @param headphones profile for a headphone route; unknown headphones by default (conservative, WH-031).
 */
class AndroidOutputLevelModels(
    context: Context,
    private val headphones: (OutputRoute) -> HeadphoneProfile = { HeadphoneProfile.UNKNOWN_HEADPHONES },
) : OutputLevelModels {
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    override fun modelFor(route: OutputRoute): OutputLevelModel {
        val device = AndroidMediaOutputs.currentDevice(audio)
        val profile = if (route == AndroidMediaOutputs.SPEAKER) HeadphoneProfile.PHONE_SPEAKER else headphones(route)
        val maxIndex = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curve = platformCurve(device?.type ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, maxIndex)
        return if (curve != null) {
            CurveOutputLevelModel(profile, curve)
        } else {
            CurveOutputLevelModel(profile, VolumeCurves.androidDefaultMedia(maxIndex), VolumeCurves.DEFAULT_CURVE_UNCERTAINTY_DB)
        }
    }

    /** The device's own curve for [deviceType], or `null` when it is missing, flat or implausibly narrow. */
    private fun platformCurve(
        deviceType: Int,
        maxIndex: Int,
    ): List<Double>? {
        val curve =
            (0..maxIndex).map { index ->
                runCatching { audio.getStreamVolumeDb(AudioManager.STREAM_MUSIC, index, deviceType).toDouble() }.getOrDefault(Double.NaN)
            }
        val usable =
            CurveOutputLevelModel.isUsableCurve(curve) &&
                curve.last() - curve[1] >= MIN_DYNAMIC_RANGE_DB
        return curve.takeIf { usable }
    }

    private companion object {
        /** Real media curves span 40 dB or more between the quietest audible step and the top. */
        const val MIN_DYNAMIC_RANGE_DB = 20.0
    }
}
