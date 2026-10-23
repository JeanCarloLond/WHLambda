package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.dBA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinearOutputLevelModelTest {
    private val model = LinearOutputLevelModel(maxIndex = 15, levelAtMax = 100.dBA, decibelsPerStep = 1.9)

    @Test
    fun `maximum index plays the anchor level`() {
        assertEquals(100.0, model.levelAt(15).decibels, 1e-9)
    }

    @Test
    fun `each step down removes a fixed number of decibels`() {
        assertEquals(96.2, model.levelAt(13).decibels, 1e-9)
        assertEquals(71.5, model.levelAt(0).decibels, 1e-9)
    }

    @Test
    fun `covers every index from mute to maximum`() {
        assertEquals(0..15, model.indices)
    }

    @Test
    fun `rejects indices outside the range`() {
        assertFailsWith<IllegalArgumentException> { model.levelAt(16) }
        assertFailsWith<IllegalArgumentException> { model.levelAt(-1) }
    }

    @Test
    fun `rejects invalid construction`() {
        assertFailsWith<IllegalArgumentException> { LinearOutputLevelModel(0, 100.dBA, 2.0) }
        assertFailsWith<IllegalArgumentException> { LinearOutputLevelModel(15, 100.dBA, 0.0) }
    }
}
