package com.waveheaven.lambda.feature.exposure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class DoseExplorerViewModelTest {
    private val viewModel = DoseExplorerViewModel()

    @Test
    fun `starts at 85 dBA with the NIOSH and WHO safe times`() {
        val state = viewModel.state.value

        assertEquals(85.0, state.level.decibels)
        assertEquals(8.hours, state.nioshDailySafeTime)
        assertEquals((12.hours + 38.minutes).inWholeMinutes, state.whoWeeklySafeTime.inWholeMinutes)
    }

    @Test
    fun `moving the slider recomputes the safe times for the rounded level`() {
        viewModel.onAction(DoseExplorerAction.LevelChanged(88.4f))

        val state = viewModel.state.value
        assertEquals(88.4f, state.sliderValue)
        assertEquals(88.0, state.level.decibels)
        assertEquals(4.hours.inWholeMinutes, state.nioshDailySafeTime.inWholeMinutes)
    }

    @Test
    fun `slider positions outside the range are clamped`() {
        viewModel.onAction(DoseExplorerAction.LevelChanged(200f))
        assertEquals(110.0, viewModel.state.value.level.decibels)

        viewModel.onAction(DoseExplorerAction.LevelChanged(-5f))
        assertEquals(60.0, viewModel.state.value.level.decibels)
    }
}
