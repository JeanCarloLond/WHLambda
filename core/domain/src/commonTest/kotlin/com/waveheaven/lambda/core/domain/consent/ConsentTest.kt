package com.waveheaven.lambda.core.domain.consent

import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ConsentTest {
    private val at = Instant.parse("2026-10-26T15:00:00Z")

    @Test
    fun `no consent means no metering`() {
        val none: Consent? = null

        assertEquals(ConsentStatus.MISSING, none.status())
        assertFalse(none.allowsMetering())
    }

    @Test
    fun `consent to the current text allows metering`() {
        val consent = Consent(Consent.CURRENT_VERSION, at)

        assertEquals(ConsentStatus.GIVEN, consent.status())
        assertTrue(consent.allowsMetering())
    }

    @Test
    fun `a new consent text asks again before metering`() {
        val consent = Consent(version = 1, acceptedAt = at)

        assertEquals(ConsentStatus.OUTDATED, consent.status(currentVersion = 2))
        assertFalse(consent.allowsMetering(currentVersion = 2))
    }

    @Test
    fun `versions start at 1`() {
        assertFailsWith<IllegalArgumentException> { Consent(0, at) }
    }

    @Test
    fun `in-memory preferences default to WHO adult and no consent`() =
        runTest {
            val preferences = InMemoryUserPreferences()

            assertEquals(DoseStandard.WHO_ADULT, preferences.weeklyStandard.first())
            assertNull(preferences.consent.first())
            assertFalse(preferences.onboardingCompleted.first())
        }

    @Test
    fun `in-memory preferences record and withdraw consent`() =
        runTest {
            val preferences = InMemoryUserPreferences()

            preferences.giveConsent(1, at)
            preferences.setWeeklyStandard(DoseStandard.WHO_SENSITIVE)
            preferences.completeOnboarding()
            assertEquals(Consent(1, at), preferences.consent.first())
            assertEquals(DoseStandard.WHO_SENSITIVE, preferences.weeklyStandard.first())
            assertTrue(preferences.onboardingCompleted.first())

            preferences.withdrawConsent()
            assertNull(preferences.consent.first())
            preferences.setMicrophoneAdjustmentDb(5.0)
            preferences.setMediaApp("Spotify")
            assertEquals(5.0, preferences.microphoneAdjustmentDb.first())
            assertEquals("Spotify", preferences.mediaApp.first())
            assertFailsWith<IllegalArgumentException> { preferences.setMicrophoneAdjustmentDb(-41.0) }
            assertFailsWith<IllegalArgumentException> { preferences.setWeeklyStandard(DoseStandard.NIOSH) }
        }
}
