package com.waveheaven.lambda.shared

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import app.cash.sqldelight.db.SqlDriver
import com.waveheaven.lambda.core.data.createInMemoryDriver
import com.waveheaven.lambda.core.domain.consent.Consent
import com.waveheaven.lambda.core.domain.consent.MicrophoneAccess
import com.waveheaven.lambda.core.domain.consent.NotificationAccess
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.hearing.Ear
import com.waveheaven.lambda.core.domain.hearing.TonePlayer
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolume
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

// The dashboard loads from the database off the UI thread, which Compose's idling does not track, so its texts
// are awaited with waitUntilExactlyOneExists instead of asserted right away.
@OptIn(ExperimentalTestApi::class)
class AppTest {
    private lateinit var systemLocale: Locale
    private lateinit var preferences: UserPreferences
    private val microphone = FakeMicrophoneAccess()
    private val notifications = FakeNotificationAccess()
    private val adaptive = FakeAdaptiveVolume()

    @BeforeTest
    fun setUp() {
        systemLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        val koin =
            initKoin {
                modules(
                    module {
                        single<SqlDriver> { createInMemoryDriver() }
                        single<AdaptiveVolume> { adaptive }
                        single<TonePlayer> { SilentTonePlayer }
                    },
                )
            }.koin
        preferences = koin.get()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Locale.setDefault(systemLocale)
    }

    @Test
    fun `first launch goes through onboarding, then the dashboard`() =
        runComposeUiTest {
            setContent { App(appVersion = "test", microphoneAccess = microphone, notificationAccess = notifications) }

            waitUntilExactlyOneExists(hasText("Welcome to WaveHeaven"))
            onNodeWithText("Get started").performClick()
            onNodeWithText("Continue").performScrollTo().performClick()
            onNodeWithText("I agree to WaveHeaven processing this data as described.").performScrollTo().performClick()
            onNodeWithText("Accept").performScrollTo().performClick()
            waitUntilExactlyOneExists(hasText("How strict should your weekly limit be?"))
            onNodeWithText("Continue").performScrollTo().performClick()
            onNodeWithText("Allow microphone").performClick()
            onNodeWithText("Finish").performClick()

            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))
            assertEquals(1, microphone.requests)
            runBlocking {
                assertTrue(preferences.onboardingCompleted.first())
                assertEquals(Consent.CURRENT_VERSION, preferences.consent.first()!!.version)
            }
        }

    @Test
    fun `without consent the dashboard explains it and leads back to consent`() =
        runComposeUiTest {
            onboard(withConsent = false)
            setContent { App(appVersion = "test", microphoneAccess = microphone, notificationAccess = notifications) }

            waitUntilExactlyOneExists(hasText("Adaptive volume is off"))
            onNodeWithText("Review and give consent").performClick()
            onNodeWithText("Continue").performScrollTo().performClick()
            onNodeWithText("I agree to WaveHeaven processing this data as described.").performScrollTo().performClick()
            onNodeWithText("Accept").performScrollTo().performClick()
            waitUntilExactlyOneExists(hasText("Microphone access"))
            microphone.granted = true
            onNodeWithText("Allow microphone").performClick()
            onNodeWithText("Finish").performClick()

            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))
            onNodeWithText("Adaptive volume is off").assertDoesNotExist()
        }

    @Test
    fun `starting adaptive volume asks for the microphone and notifications first`() =
        runComposeUiTest {
            onboard()
            setContent { App(appVersion = "test", microphoneAccess = microphone, notificationAccess = notifications) }

            waitUntilExactlyOneExists(hasText("Start"))
            onNodeWithText("Start").performClick()
            waitUntilExactlyOneExists(hasText("Pause"))

            assertEquals(1, microphone.requests)
            assertEquals(1, notifications.requests)
            assertEquals(AdaptiveVolumeState.WAITING_FOR_MUSIC, adaptive.status.value.state)
        }

    @Test
    fun `navigates to the dose explorer and back`() =
        runComposeUiTest {
            onboard()
            setContent { App(appVersion = "test", microphoneAccess = microphone, notificationAccess = notifications) }
            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))

            onNodeWithText("Explore listening levels").performScrollTo().performClick()
            onNodeWithText("Level: 85 dB(A)").assertIsDisplayed()
            onNodeWithText("8 h").assertIsDisplayed()

            onNodeWithContentDescription("Back").performClick()
            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))
        }

    @Test
    fun `picks a listening profile from the dashboard`() =
        runComposeUiTest {
            onboard()
            setContent { App(appVersion = "test", microphoneAccess = microphone, notificationAccess = notifications) }

            waitUntilExactlyOneExists(hasText("Profile: Commute"))
            onNodeWithText("Profile: Commute").performClick()
            onNodeWithText("Gym").performClick()
            onNodeWithContentDescription("Back").performClick()

            waitUntilExactlyOneExists(hasText("Profile: Gym"))
        }

    @Test
    fun `navigates to about and back`() =
        runComposeUiTest {
            onboard()
            setContent { App(appVersion = "test", microphoneAccess = microphone, notificationAccess = notifications) }

            onNodeWithContentDescription("About WaveHeaven").performClick()
            onNodeWithText("Version test").assertIsDisplayed()

            onNodeWithContentDescription("Back").performClick()
            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))
        }

    @Test
    fun `navigates to settings and back`() =
        runComposeUiTest {
            onboard()
            setContent { App(appVersion = "test", microphoneAccess = microphone, notificationAccess = notifications) }

            onNodeWithContentDescription("Settings").performClick()
            onNodeWithText("Delete my listening data").performScrollTo().assertIsDisplayed()

            onNodeWithContentDescription("Back").performClick()
            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))
        }

    @Test
    fun `opens the hearing check from settings`() =
        runComposeUiTest {
            onboard()
            setContent { App(appVersion = "test", microphoneAccess = microphone, notificationAccess = notifications) }

            onNodeWithContentDescription("Settings").performClick()
            onNodeWithText("Take a hearing check").performScrollTo().performClick()

            waitUntilExactlyOneExists(hasText("This is not a medical diagnosis", substring = true))
        }

    @Test
    fun `is translated to Spanish`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("es"))
            onboard()
            setContent { App(appVersion = "test", microphoneAccess = microphone, notificationAccess = notifications) }

            waitUntilExactlyOneExists(hasText("Hoy (NIOSH)"))
            waitUntilExactlyOneExists(hasText("Últimos 7 días (OMS)"))
        }

    private fun onboard(withConsent: Boolean = true) =
        runBlocking {
            preferences.completeOnboarding()
            if (withConsent) preferences.giveConsent(Consent.CURRENT_VERSION, Clock.System.now())
        }

    private object SilentTonePlayer : TonePlayer {
        override suspend fun play(
            ear: Ear?,
            frequencyHz: Int,
            levelDb: Double,
            durationMillis: Long,
        ) = Unit
    }

    private class FakeNotificationAccess : NotificationAccess {
        var requests = 0

        override fun isGranted() = requests > 0

        override fun request(onResult: (granted: Boolean) -> Unit) {
            requests++
            onResult(true)
        }
    }

    private class FakeAdaptiveVolume : AdaptiveVolume {
        override val status = MutableStateFlow(AdaptiveVolumeStatus(AdaptiveVolumeState.STOPPED))

        override fun start() {
            status.value = AdaptiveVolumeStatus(AdaptiveVolumeState.WAITING_FOR_MUSIC)
        }

        override fun pause() {
            status.value = AdaptiveVolumeStatus(AdaptiveVolumeState.PAUSED)
        }

        override fun resume() = start()

        override fun stop() {
            status.value = AdaptiveVolumeStatus(AdaptiveVolumeState.STOPPED)
        }
    }

    private class FakeMicrophoneAccess : MicrophoneAccess {
        var granted = false
        var requests = 0

        override fun isGranted() = granted

        override fun request(onResult: (granted: Boolean) -> Unit) {
            requests++
            granted = true
            onResult(true)
        }

        override fun openSystemSettings() = Unit
    }
}
