package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.alert.DoseAdvice
import com.waveheaven.lambda.core.domain.alert.DoseAlert
import com.waveheaven.lambda.core.domain.alert.DoseAlertSettings
import com.waveheaven.lambda.core.domain.alert.DoseAlertStore
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import com.waveheaven.lambda.core.domain.exposure.Dose
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.dBA
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DoseAlertMonitorTest {
    private val store = FakeStore()
    private val shown = mutableListOf<DoseAlert>()

    @Test
    fun `alerts as the weekly dose crosses each threshold, once`() =
        runTest {
            val monitor = DoseAlertMonitor(store, { shown += it }, MutableStateFlow(DoseStandard.WHO_SENSITIVE))

            listOf(0.3, 0.51, 0.6, 0.81, 0.9, 1.0, 1.1).forEach { monitor.check(status(it)) }

            assertEquals(listOf(DoseThreshold.HALF, DoseThreshold.HIGH, DoseThreshold.FULL), shown.map { it.threshold })
            assertEquals(DoseStandard.WHO_SENSITIVE, shown.first().standard)
            assertEquals(DoseAdvice.USE_NOISE_CANCELLING, shown[1].advice) // 80 dB(A) around: noisy
        }

    @Test
    fun `what has passed survives a restart`() =
        runTest {
            DoseAlertMonitor(store, { shown += it }, MutableStateFlow(DoseStandard.WHO_ADULT)).check(status(0.55))

            DoseAlertMonitor(store, { shown += it }, MutableStateFlow(DoseStandard.WHO_ADULT)).check(status(0.6))

            assertEquals(1, shown.size)
        }

    @Test
    fun `respects the user's settings and ignores updates without a dose`() =
        runTest {
            store.settings.value = DoseAlertSettings(emptySet())
            val monitor = DoseAlertMonitor(store, { shown += it }, MutableStateFlow(DoseStandard.WHO_ADULT))

            monitor.check(AdaptiveVolumeStatus(AdaptiveVolumeState.WAITING_FOR_MUSIC))
            monitor.check(status(1.2))

            assertEquals(emptyList(), shown)
            assertEquals(DoseThreshold.entries.toSet(), store.passedThresholds())
        }

    private fun status(dose: Double) = AdaptiveVolumeStatus(AdaptiveVolumeState.RUNNING, ambient = 80.dBA, weeklyDose = Dose(dose))

    private class FakeStore : DoseAlertStore {
        override val settings = MutableStateFlow(DoseAlertSettings())
        private var passed = emptySet<DoseThreshold>()

        override suspend fun setSettings(settings: DoseAlertSettings) {
            this.settings.value = settings
        }

        override suspend fun passedThresholds() = passed

        override suspend fun setPassedThresholds(passed: Set<DoseThreshold>) {
            this.passed = passed
        }
    }
}
