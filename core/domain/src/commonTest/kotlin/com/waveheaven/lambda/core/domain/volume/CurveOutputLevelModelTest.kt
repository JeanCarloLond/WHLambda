package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.dBA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurveOutputLevelModelTest {
    private val profile = HeadphoneProfile("test", "Test", 100.dBA, 4.0)

    @Test
    fun `adds each index's attenuation to the profile's maximum`() {
        val model = CurveOutputLevelModel(profile, listOf(-96.0, -40.0, -20.0, 0.0))

        assertEquals(0..3, model.indices)
        assertEquals(100.0, model.levelAt(3).decibels, 1e-9)
        assertEquals(80.0, model.levelAt(2).decibels, 1e-9)
        assertEquals(60.0, model.levelAt(1).decibels, 1e-9)
    }

    @Test
    fun `normalises the curve to 0 dB at the top and floors mute`() {
        // Android reports gains that may not end at 0 dB, and mute as a huge negative number.
        val model = CurveOutputLevelModel(profile, listOf(-1000.0, -45.0, -5.0))

        assertEquals(100.0, model.levelAt(2).decibels, 1e-9)
        assertEquals(60.0, model.levelAt(1).decibels, 1e-9)
        assertEquals(100.0 + CurveOutputLevelModel.MUTE_FLOOR_DB, model.levelAt(0).decibels, 1e-9)
    }

    @Test
    fun `the margin combines the profile's and the curve's`() {
        assertEquals(7.0, CurveOutputLevelModel(profile, listOf(-50.0, 0.0), curveUncertaintyDb = 3.0).uncertaintyDb)
        assertEquals(4.0, CurveOutputLevelModel(profile, listOf(-50.0, 0.0)).uncertaintyDb)
    }

    @Test
    fun `a flat curve, as with Bluetooth absolute volume, is not usable`() {
        assertFalse(CurveOutputLevelModel.isUsableCurve(List(16) { 0.0 }))
        assertFalse(CurveOutputLevelModel.isUsableCurve(listOf(0.0)))
        assertFalse(CurveOutputLevelModel.isUsableCurve(listOf(-10.0, Double.NaN)))
        assertTrue(CurveOutputLevelModel.isUsableCurve(VolumeCurves.androidDefaultMedia(15)))
        assertFailsWith<IllegalArgumentException> { CurveOutputLevelModel(profile, List(16) { 0.0 }) }
    }

    @Test
    fun `Android's default media curve follows its reference points`() {
        val curve = VolumeCurves.androidDefaultMedia(100)

        assertEquals(CurveOutputLevelModel.MUTE_FLOOR_DB, curve[0])
        assertEquals(-58.0, curve[1], 1e-9)
        assertEquals(-40.0, curve[20], 1e-9)
        assertEquals(-28.5, curve[40], 1e-9)
        assertEquals(-17.0, curve[60], 1e-9)
        assertEquals(0.0, curve[100], 1e-9)
    }

    @Test
    fun `the default curve on a 15-step phone is about 2 to 4 dB per step at the top`() {
        val model = CurveOutputLevelModel(HeadphoneProfile.UNKNOWN_HEADPHONES, VolumeCurves.androidDefaultMedia(15))

        val topSteps = (12..15).map { model.levelAt(it).decibels - model.levelAt(it - 1).decibels }
        assertTrue(topSteps.all { it in 2.0..5.0 }, "steps were $topSteps")
    }

    @Test
    fun `rejects invalid curves and indices`() {
        assertFailsWith<IllegalArgumentException> { CurveOutputLevelModel(profile, listOf(-10.0, -20.0, 0.0)) }
        assertFailsWith<IllegalArgumentException> { CurveOutputLevelModel(profile, listOf(-10.0, Double.NEGATIVE_INFINITY)) }
        assertFailsWith<IllegalArgumentException> { CurveOutputLevelModel(profile, listOf(-10.0, 0.0), curveUncertaintyDb = -1.0) }
        assertFailsWith<IllegalArgumentException> { CurveOutputLevelModel(profile, listOf(-10.0, 0.0)).levelAt(2) }
        assertFailsWith<IllegalArgumentException> { VolumeCurves.androidDefaultMedia(0) }
    }
}
