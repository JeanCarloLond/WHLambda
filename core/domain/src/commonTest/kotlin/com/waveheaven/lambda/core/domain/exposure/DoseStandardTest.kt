package com.waveheaven.lambda.core.domain.exposure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

class DoseStandardTest {
    @Test
    fun `NIOSH allows 8 hours at the 85 dBA criterion`() {
        assertDurationEquals(8.hours, DoseStandard.NIOSH.allowedDuration(85.dBA))
    }

    @Test
    fun `NIOSH halves the allowed time every 3 dB`() {
        assertDurationEquals(4.hours, DoseStandard.NIOSH.allowedDuration(88.dBA))
        assertDurationEquals(1.hours, DoseStandard.NIOSH.allowedDuration(94.dBA))
        assertDurationEquals(15.minutes, DoseStandard.NIOSH.allowedDuration(100.dBA))
    }

    @Test
    fun `NIOSH doubles the allowed time below the criterion`() {
        assertDurationEquals(16.hours, DoseStandard.NIOSH.allowedDuration(82.dBA))
    }

    @Test
    fun `a full shift at the criterion is exactly 100 percent`() {
        val dose = DoseStandard.NIOSH.doseOf(listOf(ExposureSegment(85.dBA, 8.hours)))

        assertEquals(1.0, dose.fraction, TOLERANCE)
        assertFalse(dose.isExceeded)
    }

    @Test
    fun `partial doses add up across segments`() {
        val dose =
            DoseStandard.NIOSH.doseOf(
                listOf(
                    ExposureSegment(85.dBA, 4.hours), // 50 %
                    ExposureSegment(88.dBA, 2.hours), // 50 %
                    ExposureSegment(91.dBA, 30.minutes), // 25 %
                ),
            )

        assertEquals(125.0, dose.percent, TOLERANCE)
        assertTrue(dose.isExceeded)
    }

    @Test
    fun `WHO adult budget is 40 hours at 80 dBA, about 1_6 Pa2h`() {
        val week = listOf(ExposureSegment(80.dBA, 40.hours))

        assertEquals(1.0, DoseStandard.WHO_ADULT.doseOf(week).fraction, TOLERANCE)
        assertEquals(1.6, soundExposure(week), 0.01)
    }

    @Test
    fun `WHO sensitive budget is 40 hours at 75 dBA, about 0_51 Pa2h`() {
        val week = listOf(ExposureSegment(75.dBA, 40.hours))

        assertEquals(1.0, DoseStandard.WHO_SENSITIVE.doseOf(week).fraction, TOLERANCE)
        assertEquals(0.51, soundExposure(week), 0.01)
    }

    @Test
    fun `WHO uses equal energy, so plus 10 dB means a tenth of the time`() {
        assertDurationEquals(4.hours, DoseStandard.WHO_ADULT.allowedDuration(90.dBA))
    }

    @Test
    fun `remaining time accounts for the dose already consumed`() {
        val remaining = DoseStandard.NIOSH.remainingDuration(alreadyConsumed = Dose(0.5), level = 91.dBA)

        assertDurationEquals(1.hours, remaining)
    }

    @Test
    fun `remaining time is zero once the budget is spent`() {
        assertEquals(Duration.ZERO, DoseStandard.NIOSH.remainingDuration(Dose(1.2), 85.dBA))
    }

    @Test
    fun `no exposure means no dose`() {
        assertEquals(Dose.NONE, DoseStandard.WHO_ADULT.doseOf(emptyList()))
    }

    private fun assertDurationEquals(
        expected: Duration,
        actual: Duration,
    ) = assertEquals(
        expected.toDouble(DurationUnit.SECONDS),
        actual.toDouble(DurationUnit.SECONDS),
        absoluteTolerance = 1.0,
        message = "expected $expected but was $actual",
    )

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
