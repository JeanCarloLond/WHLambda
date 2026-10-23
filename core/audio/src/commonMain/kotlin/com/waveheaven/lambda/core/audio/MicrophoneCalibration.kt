package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlin.math.log10

/**
 * Converts a microphone level in dBFS (10·log10 of the mean square of samples in −1…1) into dB SPL:
 * `dB SPL = dBFS + offsetDb`.
 */
data class MicrophoneCalibration(
    val offsetDb: Double,
) {
    init {
        require(offsetDb.isFinite()) { "Calibration offset must be finite" }
    }

    fun toSoundLevel(dbfs: Double): SoundLevel = SoundLevel(dbfs + offsetDb)

    companion object {
        /**
         * Provisional default for Android's `VOICE_RECOGNITION` source until per-device calibration exists
         * (spike #3). The Android CDD asks that a 90 dB SPL tone at 1 kHz yields an RMS of 2500 on 16-bit
         * samples, i.e. 20·log10(2500 / 32768) ≈ −22.35 dBFS, hence an offset of ≈ 112.35 dB.
         * Devices deviate by several dB; the dose ceiling does not depend on this value.
         */
        val ANDROID_VOICE_RECOGNITION_NOMINAL: MicrophoneCalibration =
            MicrophoneCalibration(CDD_REFERENCE_SPL_DB - 20.0 * log10(CDD_REFERENCE_RMS / FULL_SCALE_16_BIT))

        private const val CDD_REFERENCE_SPL_DB = 90.0
        private const val CDD_REFERENCE_RMS = 2500.0
        private const val FULL_SCALE_16_BIT = 32768.0
    }
}
