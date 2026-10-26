package com.waveheaven.lambda.feature.settings

import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import com.waveheaven.lambda.core.domain.alert.InMemoryDoseAlertStore
import com.waveheaven.lambda.core.domain.consent.Consent
import com.waveheaven.lambda.core.domain.consent.ConsentStatus
import com.waveheaven.lambda.core.domain.consent.InMemoryUserPreferences
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.ExposureMinute
import com.waveheaven.lambda.core.domain.exposure.InMemoryExposureLog
import com.waveheaven.lambda.core.domain.exposure.dBA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// setMain is still experimental, but it is the supported way to give viewModelScope a test dispatcher.
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val log = InMemoryExposureLog()
    private val consentAt = Instant.parse("2026-10-27T03:00:00Z") // 26 October, 22:00 in Bogotá
    private val preferences = InMemoryUserPreferences(consent = Consent(1, consentAt))
    private val alerts = InMemoryDoseAlertStore()
    private lateinit var viewModel: SettingsViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(log, preferences, alerts) { TimeZone.of("America/Bogota") }
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `shows the consent with its local date and version, and the standard`() {
        val state = viewModel.state.value

        assertEquals(ConsentStatus.GIVEN, state.consentStatus)
        assertEquals("2026-10-26", state.consentDate)
        assertEquals(1, state.consentVersion)
        assertEquals(DoseStandard.WHO_ADULT, state.weeklyStandard)
    }

    @Test
    fun `changing the standard saves it`() =
        runTest {
            viewModel.onAction(SettingsAction.StandardSelected(DoseStandard.WHO_SENSITIVE))

            assertEquals(DoseStandard.WHO_SENSITIVE, preferences.weeklyStandard.first())
            assertEquals(DoseStandard.WHO_SENSITIVE, viewModel.state.value.weeklyStandard)
        }

    @Test
    fun `alerts can be turned off one by one, muted and turned back on`() =
        runTest {
            assertEquals(DoseThreshold.entries.toSet(), viewModel.state.value.alerts)

            viewModel.onAction(SettingsAction.AlertToggled(DoseThreshold.HALF, enabled = false))
            assertEquals(setOf(DoseThreshold.HIGH, DoseThreshold.FULL), alerts.settings.first().enabled)

            viewModel.onAction(SettingsAction.AllAlertsMuted(true))
            assertTrue(alerts.settings.first().isMuted)
            assertTrue(
                viewModel.state.value.alerts
                    .isEmpty(),
            )

            viewModel.onAction(SettingsAction.AllAlertsMuted(false))
            viewModel.onAction(SettingsAction.AlertToggled(DoseThreshold.FULL, enabled = false))
            assertEquals(setOf(DoseThreshold.HALF, DoseThreshold.HIGH), viewModel.state.value.alerts)
        }

    @Test
    fun `the microphone correction is saved in whole decibels, clamped, and can be reset`() =
        runTest {
            viewModel.onAction(SettingsAction.MicrophoneAdjustmentChanged(-12.4f))
            assertEquals(-12.0, preferences.microphoneAdjustmentDb.first())
            assertEquals(-12, viewModel.state.value.microphoneAdjustmentDb)

            viewModel.onAction(SettingsAction.MicrophoneAdjustmentChanged(55f))
            assertEquals(40.0, preferences.microphoneAdjustmentDb.first())

            viewModel.onAction(SettingsAction.MicrophoneAdjustmentReset)
            assertEquals(0.0, preferences.microphoneAdjustmentDb.first())
        }

    @Test
    fun `without a desktop app list the music app section is hidden`() {
        assertNull(viewModel.state.value.runningApps)
    }

    @Test
    fun `on desktop lists running apps and saves the chosen one`() =
        runTest {
            var apps = listOf("Firefox", "Spotify")
            val desktop = SettingsViewModel(log, preferences, alerts, runningApps = { apps }) { TimeZone.of("America/Bogota") }
            assertEquals(listOf("Firefox", "Spotify"), desktop.state.value.runningApps)

            desktop.onAction(SettingsAction.MediaAppSelected("Spotify"))
            assertEquals("Spotify", preferences.mediaApp.first())
            assertEquals("Spotify", desktop.state.value.mediaApp)

            apps = listOf("Spotify", "VLC")
            desktop.onAction(SettingsAction.RefreshRunningApps)
            assertEquals(listOf("Spotify", "VLC"), desktop.state.value.runningApps)
        }

    @Test
    fun `withdrawing consent keeps the data unless asked`() =
        runTest {
            record()

            viewModel.onAction(SettingsAction.WithdrawConsentClicked)
            assertTrue(viewModel.state.value.isConfirmingWithdrawal)
            viewModel.onAction(SettingsAction.WithdrawConsentConfirmed)

            assertNull(preferences.consent.first())
            assertEquals(ConsentStatus.MISSING, viewModel.state.value.consentStatus)
            assertEquals(SettingsNotice.CONSENT_WITHDRAWN, viewModel.state.value.notice)
            assertEquals(1, log.minutesSince(Instant.DISTANT_PAST).size)
        }

    @Test
    fun `withdrawing consent can delete the data too`() =
        runTest {
            record()

            viewModel.onAction(SettingsAction.WithdrawConsentClicked)
            viewModel.onAction(SettingsAction.DeleteDataOnWithdrawalChanged(true))
            viewModel.onAction(SettingsAction.WithdrawConsentConfirmed)

            assertNull(preferences.consent.first())
            assertTrue(log.minutesSince(Instant.DISTANT_PAST).isEmpty())
            assertEquals(SettingsNotice.CONSENT_WITHDRAWN_AND_DATA_DELETED, viewModel.state.value.notice)
        }

    @Test
    fun `dismissing the withdrawal keeps consent`() =
        runTest {
            viewModel.onAction(SettingsAction.WithdrawConsentClicked)
            viewModel.onAction(SettingsAction.WithdrawConsentDismissed)

            assertFalse(viewModel.state.value.isConfirmingWithdrawal)
            assertEquals(Consent(1, consentAt), preferences.consent.first())
        }

    @Test
    fun `deleting asks for confirmation first`() =
        runTest {
            record()

            viewModel.onAction(SettingsAction.DeleteDataClicked)

            assertTrue(viewModel.state.value.isConfirmingDeletion)
            assertEquals(1, log.minutesSince(Instant.DISTANT_PAST).size)
        }

    @Test
    fun `confirming erases every aggregate and shows a notice once`() =
        runTest {
            record()

            viewModel.onAction(SettingsAction.DeleteDataClicked)
            viewModel.onAction(SettingsAction.DeleteDataConfirmed)

            assertTrue(log.minutesSince(Instant.DISTANT_PAST).isEmpty())
            assertEquals(SettingsNotice.DATA_DELETED, viewModel.state.value.notice)
            assertFalse(viewModel.state.value.isDeleting)

            viewModel.onAction(SettingsAction.NoticeShown)
            assertNull(viewModel.state.value.notice)
        }

    @Test
    fun `dismissing the deletion keeps the data`() =
        runTest {
            record()

            viewModel.onAction(SettingsAction.DeleteDataClicked)
            viewModel.onAction(SettingsAction.DeleteDataDismissed)

            assertFalse(viewModel.state.value.isConfirmingDeletion)
            assertEquals(1, log.minutesSince(Instant.DISTANT_PAST).size)
        }

    private suspend fun record() = log.record(ExposureMinute(Instant.parse("2026-10-26T08:15:00Z"), 1.minutes, 85.dBA, 6.0, null))
}
