package com.waveheaven.lambda.core.domain.volume

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HeadphoneProfileTest {
    @Test
    fun `unknown headphones are assumed louder than the European cap`() {
        val unknown = HeadphoneProfile.UNKNOWN_HEADPHONES

        assertTrue(unknown.maxOutput.decibels > 100.0)
        assertEquals(8.0, unknown.uncertaintyDb)
    }

    @Test
    fun `the phone speaker is quieter than headphones`() {
        assertTrue(HeadphoneProfile.PHONE_SPEAKER.maxOutput < HeadphoneProfile.UNKNOWN_HEADPHONES.maxOutput)
    }

    @Test
    fun `datasheet in dB per milliwatt gives the level of music at full volume`() {
        // 100 dB SPL/mW, 32 Ω, 1 V RMS → 31.25 mW → 100 + 14.95 dB at full scale, minus 10 dB for programme material.
        val profile = HeadphoneProfile.fromSensitivityPerMilliwatt("x", "X", sensitivityDbSplPerMw = 100.0, impedanceOhms = 32.0)

        assertEquals(104.95, profile.maxOutput.decibels, 0.01)
        assertEquals(4.0, profile.uncertaintyDb)
    }

    @Test
    fun `datasheet in dB per volt gives the same answer for the same headphones`() {
        // 100 dB/mW at 32 Ω equals 114.95 dB/V.
        val perVolt = HeadphoneProfile.fromSensitivityPerVolt("x", "X", sensitivityDbSplPerVolt = 114.95, sourceVoltageRms = 0.5)

        assertEquals(98.93, perVolt.maxOutput.decibels, 0.01)
    }

    @Test
    fun `rejects invalid profiles`() {
        assertFailsWith<IllegalArgumentException> { HeadphoneProfile(" ", "X", HeadphoneProfile.PHONE_SPEAKER.maxOutput, 1.0) }
        assertFailsWith<IllegalArgumentException> { HeadphoneProfile("x", "X", HeadphoneProfile.PHONE_SPEAKER.maxOutput, -1.0) }
        assertFailsWith<IllegalArgumentException> { HeadphoneProfile.fromSensitivityPerMilliwatt("x", "X", 100.0, impedanceOhms = 0.0) }
        assertFailsWith<IllegalArgumentException> { HeadphoneProfile.fromSensitivityPerVolt("x", "X", 110.0, sourceVoltageRms = 0.0) }
    }
}
