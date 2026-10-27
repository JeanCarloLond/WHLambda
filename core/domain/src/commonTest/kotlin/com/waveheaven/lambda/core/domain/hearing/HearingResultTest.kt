package com.waveheaven.lambda.core.domain.hearing

import com.waveheaven.lambda.core.domain.hearing.HearingResult.Assessment
import kotlin.test.Test
import kotlin.test.assertEquals

class HearingResultTest {
    private fun result(
        right: (Int) -> Double?,
        left: (Int) -> Double? = right,
    ) = HearingResult(
        mapOf(
            Ear.RIGHT to HearingCheck.FREQUENCIES_HZ.associateWith(right),
            Ear.LEFT to HearingCheck.FREQUENCIES_HZ.associateWith(left),
        ),
    )

    @Test
    fun `even hearing needs no referral`() {
        assertEquals(Assessment.EVEN, result({ -50.0 }).assessment())
        assertEquals(Assessment.EVEN, result({ f -> if (f == 4000) -35.0 else -50.0 }).assessment())
    }

    @Test
    fun `ears 15 dB apart at two frequencies are asymmetric`() {
        val one = result(right = { -50.0 }, left = { f -> if (f == 4000) -30.0 else -50.0 })
        val two = result(right = { -50.0 }, left = { f -> if (f >= 4000) -30.0 else -50.0 })

        assertEquals(Assessment.EVEN, one.assessment())
        assertEquals(Assessment.ASYMMETRIC, two.assessment())
    }

    @Test
    fun `a dip of 25 dB at two frequencies, or a tone never heard, is uneven`() {
        assertEquals(Assessment.UNEVEN, result({ f -> if (f in 3000..4000) -25.0 else -55.0 }).assessment())
        assertEquals(Assessment.UNEVEN, result({ f -> if (f == 8000) HearingResult.NOT_HEARD else -50.0 }).assessment())
    }

    @Test
    fun `the compensation lifts weaker frequencies by half the gap, capped`() {
        val curve =
            result({ f ->
                if (f == 4000) {
                    -30.0
                } else if (f == 8000) {
                    HearingResult.NOT_HEARD
                } else {
                    -50.0
                }
            }).compensationCurve()

        assertEquals(0.0, curve.gainAt(1000.0), 1e-9)
        assertEquals(10.0, curve.gainAt(4000.0), 1e-9) // 20 dB worse than the best → +10 dB
        assertEquals(HearingResult.MAX_BOOST_DB, curve.gainAt(8000.0), 1e-9) // not heard: capped
        assertEquals(HearingCheck.FREQUENCIES_HZ.size, curve.points.size)
    }
}
