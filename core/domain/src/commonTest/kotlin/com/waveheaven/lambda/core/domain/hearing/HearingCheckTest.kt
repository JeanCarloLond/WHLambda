package com.waveheaven.lambda.core.domain.hearing

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HearingCheckTest {
    /** Runs a whole check against a listener who hears a tone when it is at or above [trueThreshold]. */
    private fun run(
        trueThreshold: (Ear, Int) -> Double,
        missChance: Double = 0.0,
        random: Random = Random(7),
    ): Pair<HearingResult, Int> {
        val check = HearingCheck()
        var presentations = 0
        while (true) {
            val tone = check.next() ?: break
            presentations++
            val audible = tone.levelDb >= trueThreshold(tone.ear, tone.frequencyHz)
            check.answer(audible && random.nextDouble() >= missChance)
        }
        return check.result() to presentations
    }

    @Test
    fun `finds each threshold within one up-step`() {
        val truth = mapOf(1000 to -52.0, 4000 to -33.0, 8000 to -61.0)
        val (result, _) = run({ _, f -> truth[f] ?: -45.0 })

        Ear.entries.forEach { ear ->
            HearingCheck.FREQUENCIES_HZ.forEach { f ->
                val found = result.threshold(ear, f)!!
                val expected = truth[f] ?: -45.0
                assertTrue(found >= expected && found - expected <= HearingCheck.STEP_UP_DB, "$ear $f Hz: $found vs $expected")
            }
        }
    }

    @Test
    fun `covers both ears at eight frequencies from 250 Hz to 8 kHz`() {
        val (result, presentations) = run({ _, _ -> -40.0 })

        assertEquals(setOf(Ear.RIGHT, Ear.LEFT), result.thresholds.keys)
        assertEquals(HearingCheck.FREQUENCIES_HZ.toSet(), result.thresholds.getValue(Ear.LEFT).keys)
        assertTrue(presentations <= 16 * HearingCheck.MAX_PRESENTATIONS)
    }

    @Test
    fun `starts with the right ear at 1 kHz and never plays above the reference`() {
        val check = HearingCheck()
        assertEquals(Presentation(Ear.RIGHT, 1000, HearingCheck.START_DB), check.next())

        repeat(20) {
            val tone = check.next() ?: return
            assertTrue(tone.levelDb <= HearingCheck.CEILING_DB)
            check.answer(false)
        }
    }

    @Test
    fun `a tone not heard even at the reference has no threshold`() {
        val (result, _) = run({ ear, f -> if (ear == Ear.LEFT && f == 6000) 10.0 else -40.0 })

        assertNull(result.threshold(Ear.LEFT, 6000))
        assertEquals(-40.0, result.threshold(Ear.RIGHT, 6000)!!, HearingCheck.STEP_UP_DB)
    }

    @Test
    fun `an inattentive listener still finishes, near the truth`() {
        val (result, presentations) = run({ _, _ -> -50.0 }, missChance = 0.2)

        assertTrue(presentations <= 16 * HearingCheck.MAX_PRESENTATIONS)
        result.thresholds.values
            .flatMap { it.values }
            .forEach { assertTrue(it != null && it in -60.0..-30.0, "$it") }
    }

    @Test
    fun `progress goes from 0 to 1 and answering after the end fails`() {
        val check = HearingCheck()
        assertEquals(0.0, check.progress)
        while (check.next() != null) check.answer(check.next()!!.levelDb >= -40.0)

        assertEquals(1.0, check.progress)
        assertFailsWith<IllegalStateException> { check.answer(true) }
    }
}
