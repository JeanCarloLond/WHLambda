package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.Dose
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import com.waveheaven.lambda.core.domain.exposure.dBA
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.AT_DOSE_CEILING
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.LOWER_FOR_QUIET
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.LOWER_TO_DOSE_CEILING
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.MANUAL_OVERRIDE
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.MUTED
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.NO_MUSIC
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.RAISE_FOR_NOISE
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.RATE_LIMITED
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.SETTLING
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.WARMING_UP
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.WITHIN_DEAD_BAND
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/*
 * Model used throughout: 16 steps, 2 dB per step, index 15 = 100 dB(A).
 *   index:  1   5   8  10  13  15
 *   dB(A): 72  80  86  90  96 100
 * With no dose consumed, the WHO 1 h ceiling is ≈96 dB(A) → index 13.
 */
class VolumeControllerTest {
    @Test
    fun `waits for enough ambient readings before comfort changes`() {
        val phone = Phone(index = 5)

        assertEquals(WARMING_UP, phone.tick(ambient = 85.dBA).reason)
        assertEquals(WARMING_UP, phone.tick(ambient = 85.dBA).reason)
        assertEquals(RAISE_FOR_NOISE, phone.tick(ambient = 85.dBA).reason)
    }

    @Test
    fun `raises one step at a time in rising noise, no faster than the raise interval`() {
        val phone = Phone(index = 5)

        val decisions = List(40) { phone.tick(ambient = 85.dBA) } // target 95 dB(A)

        val raises = decisions.filter { it.reason == RAISE_FOR_NOISE }
        // Stops at index 12 (94 dB(A)): 1 dB short of the target is inside the 3 dB hysteresis band.
        assertEquals(12, phone.index)
        assertEquals(7, raises.size)
        assertTrue(decisions.any { it.reason == RATE_LIMITED })
        assertTrue(raises.zipWithNext().all { (a, b) -> b.newIndex == a.newIndex!! + 1 })
        assertTrue(phone.changeTimes.zipWithNext().all { (a, b) -> b - a >= 3.seconds })
    }

    @Test
    fun `lowers faster than it raises and never mutes`() {
        val phone = Phone(index = 13)

        repeat(20) { phone.tick(ambient = 50.dBA) } // target 60 dB(A), below the quietest step

        assertEquals(1, phone.index)
        assertTrue(phone.changeTimes.zipWithNext().all { (a, b) -> b - a >= 1.seconds })
        assertEquals(12, phone.changeTimes.size)
    }

    @Test
    fun `holds steady while noise fluctuates inside the dead band`() {
        val phone = Phone(index = 8) // 86 dB(A), matching 76 dB(A) ambient + 10 dB

        val decisions = List(30) { i -> phone.tick(ambient = if (i % 2 == 0) 75.dBA else 77.5.dBA) }

        assertEquals(8, phone.index)
        assertTrue(decisions.drop(3).all { it.reason == WITHIN_DEAD_BAND && it.newIndex == null })
    }

    @Test
    fun `drops straight to the dose ceiling, even while warming up`() {
        val phone = Phone(index = 14)

        // 90 % of the WHO budget used: 1 h at 86 dB(A) would spend the rest → ceiling index 8.
        val decision = phone.tick(ambient = 90.dBA, dose = Dose(0.9))

        assertEquals(LOWER_TO_DOSE_CEILING, decision.reason)
        assertEquals(8, decision.newIndex)
        assertEquals(86.0, decision.ceilingLevel.decibels, 0.1)
        assertTrue(decision.isAboveDoseCeiling)
    }

    @Test
    fun `holds at the ceiling and flags it when the noise demands more`() {
        val phone = Phone(index = 8)

        val decisions = List(10) { phone.tick(ambient = 90.dBA, dose = Dose(0.9)) }

        assertEquals(8, phone.index)
        val settled = decisions.drop(3)
        assertTrue(settled.all { it.reason == AT_DOSE_CEILING && it.isTargetCappedByDose && it.newIndex == null })
    }

    @Test
    fun `a spent budget falls back to the minimum ceiling instead of silence`() {
        val phone = Phone(index = 10)

        val decision = phone.tick(ambient = 60.dBA, dose = Dose(1.5))

        assertEquals(70.0, decision.ceilingLevel.decibels, 1e-9)
        assertEquals(LOWER_TO_DOSE_CEILING, decision.reason)
        assertEquals(1, decision.newIndex) // quietest non-muted step
    }

    @Test
    fun `respects a manual change for the override period, then resumes`() {
        val phone = Phone(index = 8)
        repeat(5) { phone.tick(ambient = 76.dBA) }

        phone.index = 12 // the user presses volume-up
        val during = List(9.minutes.inWholeSeconds.toInt()) { phone.tick(ambient = 76.dBA) }
        repeat(2.minutes.inWholeSeconds.toInt()) { phone.tick(ambient = 76.dBA) }

        assertTrue(during.all { it.reason == MANUAL_OVERRIDE && it.newIndex == null })
        // Back near the comfort target (86 dB(A)) once the override expired: 88 dB(A) is inside the dead band.
        assertEquals(9, phone.index)
        assertTrue(phone.reasons.contains(LOWER_FOR_QUIET))
    }

    @Test
    fun `a manual change above the ceiling is respected but flagged`() {
        val phone = Phone(index = 8)
        repeat(5) { phone.tick(ambient = 90.dBA, dose = Dose(0.9)) }

        phone.index = 14
        val decision = phone.tick(ambient = 90.dBA, dose = Dose(0.9))

        assertEquals(MANUAL_OVERRIDE, decision.reason)
        assertNull(decision.newIndex)
        assertTrue(decision.isAboveDoseCeiling)
    }

    @Test
    fun `the device rounding our change while settling is not a manual override`() {
        val phone = Phone(index = 5)
        repeat(3) { phone.tick(ambient = 85.dBA) } // third tick raises to 6

        phone.index = 5 // Bluetooth absolute volume pushes back one step…
        val settling = phone.tick(ambient = 85.dBA, after = 300.milliseconds) // …within the settle time
        val next = phone.tick(ambient = 85.dBA, after = 3.seconds)

        assertEquals(SETTLING, settling.reason)
        assertEquals(RAISE_FOR_NOISE, next.reason)
    }

    @Test
    fun `each output route keeps its own state`() {
        val phone = Phone(index = 8)
        repeat(5) { phone.tick(ambient = 76.dBA) }
        phone.index = 12
        assertEquals(MANUAL_OVERRIDE, phone.tick(ambient = 76.dBA).reason)

        val onHeadphones = phone.tick(ambient = 76.dBA, route = HEADPHONES)
        phone.index = 12
        val backOnSpeaker = phone.tick(ambient = 76.dBA)

        assertEquals(LOWER_FOR_QUIET, onHeadphones.reason)
        assertEquals(MANUAL_OVERRIDE, backOnSpeaker.reason)
    }

    @Test
    fun `does nothing while no music plays, even above the ceiling`() {
        val phone = Phone(index = 15)

        val decision = phone.tick(ambient = 90.dBA, music = false, dose = Dose(0.9))

        assertEquals(NO_MUSIC, decision.reason)
        assertNull(decision.newIndex)
    }

    @Test
    fun `never unmutes`() {
        val phone = Phone(index = 0)

        val decisions = List(5) { phone.tick(ambient = 90.dBA) }

        assertTrue(decisions.all { it.reason == MUTED })
        assertEquals(0, phone.index)
    }

    @Test
    fun `reports ambient Leq, target and ceiling for the UI`() {
        val phone = Phone(index = 8)

        val decision = List(3) { phone.tick(ambient = 76.dBA) }.last()

        assertEquals(76.0, decision.ambientLeq!!.decibels, 1e-9)
        assertEquals(86.0, decision.targetLevel!!.decibels, 1e-9)
        assertEquals(96.0, decision.ceilingLevel.decibels, 0.1)
    }

    @Test
    fun `rejects indices outside the model`() {
        assertFailsWith<IllegalArgumentException> { Phone(index = 16).tick(ambient = 70.dBA) }
    }

    @Test
    fun `rejects a minimum index outside the model`() {
        assertFailsWith<IllegalArgumentException> { VolumeController(MODEL, VolumeControllerConfig(minIndex = 20)) }
    }

    @Test
    fun `rejects invalid configuration`() {
        assertFailsWith<IllegalArgumentException> { VolumeControllerConfig(hysteresisDb = -1.0) }
        assertFailsWith<IllegalArgumentException> { VolumeControllerConfig(ambientWindowTicks = 0) }
        assertFailsWith<IllegalArgumentException> { VolumeControllerConfig(planningHorizon = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { VolumeControllerConfig(raiseInterval = (-1).seconds) }
    }

    /** Simulated phone: feeds ticks to the controller and applies its decisions like the OS would. */
    private class Phone(
        var index: Int,
        config: VolumeControllerConfig = VolumeControllerConfig(),
    ) {
        private val controller = VolumeController(MODEL, config)
        private var now = Duration.ZERO
        val changeTimes = mutableListOf<Duration>()
        val reasons = mutableListOf<VolumeDecisionReason>()

        fun tick(
            ambient: SoundLevel,
            music: Boolean = true,
            dose: Dose = Dose.NONE,
            route: OutputRoute = SPEAKER,
            after: Duration = 1.seconds,
        ): VolumeDecision {
            now += after
            val decision = controller.update(VolumeTick(now, route, ambient, index, music, dose))
            reasons += decision.reason
            decision.newIndex?.let {
                index = it
                changeTimes += now
            }
            return decision
        }
    }

    private companion object {
        val MODEL = LinearOutputLevelModel(maxIndex = 15, levelAtMax = 100.dBA, decibelsPerStep = 2.0)
        val SPEAKER = OutputRoute("speaker")
        val HEADPHONES = OutputRoute("bt:REDMI Buds 8 Pro")
    }
}
