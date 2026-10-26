package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlin.math.log10

/**
 * What an output device can play at the ear: the A-weighted level of typical music at maximum volume, and how
 * sure we are of it. See "Output level at the ear" in `docs/domain/hearing-safety.md`.
 *
 * @property maxOutput level at the ear at maximum volume with programme material at the level of the
 *   EN 50332-1 programme-simulation noise (about 10 dB below a full-scale sine).
 * @property uncertaintyDb ± margin of [maxOutput].
 */
public data class HeadphoneProfile(
    val id: String,
    val name: String,
    val maxOutput: SoundLevel,
    val uncertaintyDb: Double,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(uncertaintyDb >= 0.0 && uncertaintyDb.isFinite()) { "uncertaintyDb must be non-negative" }
    }

    public companion object {
        /**
         * Headphones whose model we do not know. Deliberately loud: 5 dB above the 100 dB(A) cap that EN 50332
         * sets for players sold in Europe, because phones and headphones elsewhere often exceed it. Overestimating
         * only makes the dose ceiling stricter, never laxer (WH-031).
         */
        public val UNKNOWN_HEADPHONES: HeadphoneProfile =
            HeadphoneProfile("unknown-headphones", "Unknown headphones", SoundLevel(UNKNOWN_MAX_OUTPUT_DBA), UNKNOWN_UNCERTAINTY_DB)

        /**
         * The phone's own speaker heard at arm's length. Phone speakers rarely exceed 80 dB(A) at 30 cm; 85 dB(A)
         * leaves room for holding the phone closer.
         */
        public val PHONE_SPEAKER: HeadphoneProfile =
            HeadphoneProfile("phone-speaker", "Phone speaker", SoundLevel(SPEAKER_MAX_OUTPUT_DBA), SPEAKER_UNCERTAINTY_DB)

        /**
         * Profile of wired headphones from their datasheet: sensitivity in dB SPL per milliwatt, impedance, and the
         * RMS voltage the source delivers at full scale (about 1 V for phones and laptops). Datasheets and source
         * voltages vary, so the margin is ±4 dB.
         */
        public fun fromSensitivityPerMilliwatt(
            id: String,
            name: String,
            sensitivityDbSplPerMw: Double,
            impedanceOhms: Double,
            sourceVoltageRms: Double = DEFAULT_SOURCE_VOLTAGE_RMS,
        ): HeadphoneProfile {
            require(impedanceOhms > 0.0 && sourceVoltageRms > 0.0) { "impedance and voltage must be positive" }
            val powerMilliwatts = MILLIWATTS_PER_WATT * sourceVoltageRms * sourceVoltageRms / impedanceOhms
            val fullScale = sensitivityDbSplPerMw + DECIBELS_PER_DECADE_POWER * log10(powerMilliwatts)
            return HeadphoneProfile(id, name, SoundLevel(fullScale - PROGRAMME_BELOW_FULL_SCALE_DB), DATASHEET_UNCERTAINTY_DB)
        }

        /** Like [fromSensitivityPerMilliwatt], for datasheets that give sensitivity in dB SPL per volt. */
        public fun fromSensitivityPerVolt(
            id: String,
            name: String,
            sensitivityDbSplPerVolt: Double,
            sourceVoltageRms: Double = DEFAULT_SOURCE_VOLTAGE_RMS,
        ): HeadphoneProfile {
            require(sourceVoltageRms > 0.0) { "voltage must be positive" }
            val fullScale = sensitivityDbSplPerVolt + DECIBELS_PER_DECADE_AMPLITUDE * log10(sourceVoltageRms)
            return HeadphoneProfile(id, name, SoundLevel(fullScale - PROGRAMME_BELOW_FULL_SCALE_DB), DATASHEET_UNCERTAINTY_DB)
        }

        private const val UNKNOWN_MAX_OUTPUT_DBA = 105.0
        private const val UNKNOWN_UNCERTAINTY_DB = 8.0
        private const val SPEAKER_MAX_OUTPUT_DBA = 85.0
        private const val SPEAKER_UNCERTAINTY_DB = 6.0
        private const val DATASHEET_UNCERTAINTY_DB = 4.0
        private const val DEFAULT_SOURCE_VOLTAGE_RMS = 1.0
        private const val PROGRAMME_BELOW_FULL_SCALE_DB = 10.0
        private const val MILLIWATTS_PER_WATT = 1000.0
        private const val DECIBELS_PER_DECADE_POWER = 10.0
        private const val DECIBELS_PER_DECADE_AMPLITUDE = 20.0
    }
}
