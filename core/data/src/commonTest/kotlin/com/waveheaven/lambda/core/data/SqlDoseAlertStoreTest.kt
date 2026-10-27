package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.domain.alert.DoseAlertSettings
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDoseAlertStoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = testDriver()
    private val store = SqlDoseAlertStore(createDatabase(driver), dispatcher)

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun `every threshold is on by default and none has passed`() =
        runTest(dispatcher) {
            assertEquals(DoseAlertSettings(), store.settings.first())
            assertTrue(store.passedThresholds().isEmpty())
        }

    @Test
    fun `stores the chosen thresholds, including none`() =
        runTest(dispatcher) {
            store.setSettings(DoseAlertSettings(setOf(DoseThreshold.HIGH, DoseThreshold.FULL)))
            assertEquals(setOf(DoseThreshold.HIGH, DoseThreshold.FULL), store.settings.first().enabled)

            store.setSettings(DoseAlertSettings(emptySet()))
            assertTrue(store.settings.first().isMuted)
        }

    @Test
    fun `remembers which thresholds have passed`() =
        runTest(dispatcher) {
            store.setPassedThresholds(setOf(DoseThreshold.HALF))
            assertEquals(setOf(DoseThreshold.HALF), store.passedThresholds())

            store.setPassedThresholds(emptySet())
            assertTrue(store.passedThresholds().isEmpty())
        }
}
