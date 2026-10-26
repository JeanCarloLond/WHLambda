package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.domain.consent.Consent
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SqlUserPreferencesTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = testDriver()
    private val preferences = SqlUserPreferences(createDatabase(driver), dispatcher)
    private val at = Instant.parse("2026-10-26T15:00:00.123Z")

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun `starts with onboarding pending, WHO adult and no consent`() =
        test {
            assertFalse(preferences.onboardingCompleted.first())
            assertEquals(DoseStandard.WHO_ADULT, preferences.weeklyStandard.first())
            assertNull(preferences.consent.first())
        }

    @Test
    fun `stores consent with its version and time, and withdraws it`() =
        test {
            preferences.giveConsent(1, at)
            assertEquals(Consent(1, at), preferences.consent.first())

            preferences.withdrawConsent()
            assertNull(preferences.consent.first())
        }

    @Test
    fun `stores the weekly standard and onboarding, across instances`() =
        test {
            preferences.setWeeklyStandard(DoseStandard.WHO_SENSITIVE)
            preferences.completeOnboarding()

            val reopened = SqlUserPreferences(createDatabase(driver), dispatcher)
            assertEquals(DoseStandard.WHO_SENSITIVE, reopened.weeklyStandard.first())
            assertTrue(reopened.onboardingCompleted.first())
        }

    @Test
    fun `only weekly standards can be chosen`() =
        test {
            assertFailsWith<IllegalArgumentException> { preferences.setWeeklyStandard(DoseStandard.NIOSH) }
        }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }
}
