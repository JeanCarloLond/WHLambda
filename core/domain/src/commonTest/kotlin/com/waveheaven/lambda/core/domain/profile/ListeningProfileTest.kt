package com.waveheaven.lambda.core.domain.profile

import com.waveheaven.lambda.core.domain.volume.VolumeControllerConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ListeningProfileTest {
    @Test
    fun `four built-in profiles, commute by default`() {
        assertEquals(listOf("commute", "gym", "office", "home"), ListeningProfile.BUILT_IN.map { it.id })
        assertTrue(ListeningProfile.BUILT_IN.all { it.isBuiltIn })
        assertEquals(ListeningProfile.COMMUTE, ListeningProfile.DEFAULT)
    }

    @Test
    fun `applies its margin and volume range to the controller config`() {
        val config = ListeningProfile.OFFICE.applyTo(VolumeControllerConfig(), indices = 0..15)

        assertEquals(6.0, config.targetSnrDb)
        assertEquals(1, config.minIndex) // 5 % of 15 steps rounds up to index 1
        assertEquals(9, config.maxIndex) // 60 % of 15 = 9
    }

    @Test
    fun `safety settings of the base config are kept`() {
        val base = VolumeControllerConfig(hysteresisDb = 4.0)

        val config = ListeningProfile.GYM.applyTo(base, 0..15)

        assertEquals(base.standard, config.standard)
        assertEquals(base.minimumCeiling, config.minimumCeiling)
        assertEquals(4.0, config.hysteresisDb)
        assertEquals(15, config.maxIndex)
    }

    @Test
    fun `the minimum never mutes and the maximum never falls below it`() {
        val narrow = ListeningProfile("x", "X", isBuiltIn = false, targetSnrDb = 5.0, minVolume = 0.0, maxVolume = 0.01)

        val config = narrow.applyTo(VolumeControllerConfig(), 0..15)

        assertEquals(1, config.minIndex)
        assertEquals(1, config.maxIndex)
    }

    @Test
    fun `rejects invalid profiles`() {
        assertFailsWith<IllegalArgumentException> { ListeningProfile("", "X", false, 10.0, 0.1, 1.0) }
        assertFailsWith<IllegalArgumentException> { ListeningProfile("x", " ", false, 10.0, 0.1, 1.0) }
        assertFailsWith<IllegalArgumentException> { ListeningProfile("x", "X", false, 25.0, 0.1, 1.0) }
        assertFailsWith<IllegalArgumentException> { ListeningProfile("x", "X", false, 10.0, 0.5, 0.5) }
        assertFailsWith<IllegalArgumentException> { ListeningProfile("x", "X", false, 10.0, -0.1, 1.0) }
    }

    @Test
    fun `an EQ curve interpolates on a log frequency axis and holds its ends`() {
        val eq = EqCurve(listOf(EqPoint(100.0, 0.0), EqPoint(1000.0, 6.0), EqPoint(10_000.0, -6.0)))

        assertEquals(3.0, eq.gainAt(316.227766), 1e-6) // halfway between 100 Hz and 1 kHz in octaves
        assertEquals(0.0, eq.gainAt(50.0))
        assertEquals(-6.0, eq.gainAt(16_000.0))
        assertEquals(6.0, eq.gainAt(1000.0))
    }

    @Test
    fun `rejects invalid EQ curves`() {
        assertFailsWith<IllegalArgumentException> { EqCurve(emptyList()) }
        assertFailsWith<IllegalArgumentException> { EqCurve(listOf(EqPoint(10.0, 0.0))) }
        assertFailsWith<IllegalArgumentException> { EqCurve(listOf(EqPoint(100.0, 30.0))) }
        assertFailsWith<IllegalArgumentException> { EqCurve(listOf(EqPoint(1000.0, 0.0), EqPoint(100.0, 0.0))) }
        assertFailsWith<IllegalArgumentException> { EqCurve(listOf(EqPoint(1000.0, 0.0))).gainAt(0.0) }
    }
}
