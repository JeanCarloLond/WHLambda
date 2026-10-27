package com.waveheaven.lambda.core.designsystem.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationFormatTest {
    @Test
    fun `formats hours and minutes`() {
        assertEquals("7 h 30 min", formatDuration(7.hours + 30.minutes))
    }

    @Test
    fun `omits zero minutes`() {
        assertEquals("8 h", formatDuration(8.hours))
    }

    @Test
    fun `omits zero hours`() {
        assertEquals("15 min", formatDuration(15.minutes))
    }

    @Test
    fun `sub-minute durations are shown as less than a minute`() {
        assertEquals("< 1 min", formatDuration(30.seconds))
    }
}
