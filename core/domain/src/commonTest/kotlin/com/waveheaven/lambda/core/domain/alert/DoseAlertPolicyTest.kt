package com.waveheaven.lambda.core.domain.alert

import com.waveheaven.lambda.core.domain.alert.DoseThreshold.FULL
import com.waveheaven.lambda.core.domain.alert.DoseThreshold.HALF
import com.waveheaven.lambda.core.domain.alert.DoseThreshold.HIGH
import com.waveheaven.lambda.core.domain.exposure.Dose
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.dBA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoseAlertPolicyTest {
    private val all = DoseAlertSettings()

    @Test
    fun `alerts once when a threshold is crossed`() {
        val first = evaluate(0.51, passed = emptySet())
        val again = evaluate(0.55, passed = first.passed)

        assertEquals(HALF, first.alert!!.threshold)
        assertEquals(setOf(HALF), first.passed)
        assertNull(again.alert)
    }

    @Test
    fun `nothing below the first threshold`() {
        val evaluation = evaluate(0.49, passed = emptySet())

        assertNull(evaluation.alert)
        assertTrue(evaluation.passed.isEmpty())
    }

    @Test
    fun `a jump past several thresholds alerts only the highest`() {
        val evaluation = evaluate(1.2, passed = emptySet())

        assertEquals(FULL, evaluation.alert!!.threshold)
        assertEquals(setOf(HALF, HIGH, FULL), evaluation.passed)
    }

    @Test
    fun `a threshold re-arms only once the dose falls clearly below it`() {
        val slightlyBelow = evaluate(0.47, passed = setOf(HALF))
        val wellBelow = evaluate(0.44, passed = setOf(HALF))
        val crossedAgain = evaluate(0.5, passed = wellBelow.passed)

        assertEquals(setOf(HALF), slightlyBelow.passed)
        assertTrue(wellBelow.passed.isEmpty())
        assertEquals(HALF, crossedAgain.alert!!.threshold)
    }

    @Test
    fun `disabled thresholds stay silent but count as passed`() {
        val settings = DoseAlertSettings(enabled = setOf(FULL))

        val evaluation = DoseAlertPolicy.evaluate(DoseStandard.WHO_ADULT, Dose(0.85), null, emptySet(), settings)

        assertNull(evaluation.alert)
        assertEquals(setOf(HALF, HIGH), evaluation.passed)
    }

    @Test
    fun `muted means no thresholds`() {
        assertTrue(DoseAlertSettings(emptySet()).isMuted)
        assertNull(DoseAlertPolicy.evaluate(DoseStandard.WHO_ADULT, Dose(1.5), null, emptySet(), DoseAlertSettings(emptySet())).alert)
    }

    @Test
    fun `each alert says what to do`() {
        assertEquals(DoseAdvice.LOWER_VOLUME, DoseAlertPolicy.adviceFor(HALF, 80.dBA))
        assertEquals(DoseAdvice.USE_NOISE_CANCELLING, DoseAlertPolicy.adviceFor(HIGH, 80.dBA))
        assertEquals(DoseAdvice.TAKE_A_BREAK, DoseAlertPolicy.adviceFor(HIGH, 60.dBA))
        assertEquals(DoseAdvice.TAKE_A_BREAK, DoseAlertPolicy.adviceFor(FULL, null))
    }

    @Test
    fun `the alert carries the standard and the dose`() {
        val alert = evaluate(0.81, passed = setOf(HALF)).alert!!

        assertEquals(DoseStandard.WHO_ADULT, alert.standard)
        assertEquals(0.81, alert.dose.fraction)
        assertEquals(HIGH, alert.threshold)
    }

    private fun evaluate(
        dose: Double,
        passed: Set<DoseThreshold>,
    ) = DoseAlertPolicy.evaluate(DoseStandard.WHO_ADULT, Dose(dose), ambient = null, passed = passed, settings = all)
}
