package com.waveheaven.lambda.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import com.waveheaven.lambda.core.domain.consent.ConsentStatus
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    private lateinit var systemLocale: Locale
    private val given = SettingsUiState(consentStatus = ConsentStatus.GIVEN, consentDate = "2026-10-26", consentVersion = 1)

    @BeforeTest
    fun useEnglish() {
        systemLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    @Test
    fun `choosing a standard reports the intent`() =
        runComposeUiTest {
            val actions = mutableListOf<SettingsAction>()
            setContent { Screen(given, actions::add) }

            onNodeWithText("WHO sensitive").performClick()

            assertEquals(listOf<SettingsAction>(SettingsAction.StandardSelected(DoseStandard.WHO_SENSITIVE)), actions)
        }

    @Test
    fun `alert switches report their intents`() =
        runComposeUiTest {
            val actions = mutableListOf<SettingsAction>()
            setContent { Screen(given.copy(alerts = setOf(DoseThreshold.HIGH)), actions::add) }

            onNodeWithText("At 50\u00A0%").performScrollTo().performClick()
            onNodeWithText("At 80\u00A0%").performScrollTo().performClick()
            onNodeWithText("Mute all alerts").performScrollTo().performClick()

            assertEquals(
                listOf(
                    SettingsAction.AlertToggled(DoseThreshold.HALF, enabled = true),
                    SettingsAction.AlertToggled(DoseThreshold.HIGH, enabled = false),
                    SettingsAction.AllAlertsMuted(true),
                ),
                actions,
            )
        }

    @Test
    fun `the music app can be chosen on desktop, the chosen one listed even when silent`() =
        runComposeUiTest {
            val actions = mutableListOf<SettingsAction>()
            setContent { Screen(given.copy(mediaApp = "Spotify", runningApps = listOf("Firefox")), actions::add) }

            onNodeWithText("Spotify").assertIsDisplayed()
            onNodeWithText("Firefox").performClick()
            onNodeWithText("Refresh list").performScrollTo().performClick()

            assertEquals(listOf(SettingsAction.MediaAppSelected("Firefox"), SettingsAction.RefreshRunningApps), actions)
        }

    @Test
    fun `the music app section only exists on desktop`() =
        runComposeUiTest {
            setContent { Screen(given) }

            onNodeWithText("Music app").assertDoesNotExist()
        }

    @Test
    fun `the microphone correction shows its sign and can be reset`() =
        runComposeUiTest {
            val actions = mutableListOf<SettingsAction>()
            setContent { Screen(given.copy(microphoneAdjustmentDb = 6), actions::add) }

            onNodeWithText("Correction: +6 dB").performScrollTo().assertIsDisplayed()
            onNodeWithText("Reset to default").performScrollTo().performClick()

            assertEquals(listOf<SettingsAction>(SettingsAction.MicrophoneAdjustmentReset), actions)
        }

    @Test
    fun `given consent shows its date and can be withdrawn, with the data if wanted`() =
        runComposeUiTest {
            val actions = mutableListOf<SettingsAction>()
            setContent { Screen(given.copy(isConfirmingWithdrawal = true), actions::add) }

            onNodeWithText("You gave consent on 2026-10-26 (version 1)", substring = true).performScrollTo().assertIsDisplayed()
            onNodeWithText("Also delete my listening data").performClick()
            onNodeWithText("Withdraw").performClick()

            assertEquals(listOf(SettingsAction.DeleteDataOnWithdrawalChanged(true), SettingsAction.WithdrawConsentConfirmed), actions)
        }

    @Test
    fun `missing consent leads to reviewing it`() =
        runComposeUiTest {
            var reviews = 0
            setContent { Screen(SettingsUiState(consentStatus = ConsentStatus.MISSING), onReviewConsent = { reviews++ }) }

            onNodeWithText("Review and give consent").performScrollTo().performClick()

            assertEquals(1, reviews)
        }

    @Test
    fun `outdated consent asks to review the changes`() =
        runComposeUiTest {
            setContent { Screen(SettingsUiState(consentStatus = ConsentStatus.OUTDATED)) }

            onNodeWithText("Review the changes").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `the delete button and its dialog report their intents`() =
        runComposeUiTest {
            val actions = mutableListOf<SettingsAction>()
            setContent { Screen(given.copy(isConfirmingDeletion = true), actions::add) }

            onNodeWithText("Delete all listening data?").assertIsDisplayed()
            onNodeWithText("Cancel").performClick()
            onNodeWithText("Delete").performClick()

            assertEquals(listOf(SettingsAction.DeleteDataDismissed, SettingsAction.DeleteDataConfirmed), actions)
        }

    @Test
    fun `notices are announced`() =
        runComposeUiTest {
            setContent { Screen(given.copy(notice = SettingsNotice.CONSENT_WITHDRAWN_AND_DATA_DELETED)) }

            onNodeWithText("Consent withdrawn and listening data deleted").assertIsDisplayed()
        }

    @Test
    fun `the back button reports the intent`() =
        runComposeUiTest {
            var backs = 0
            setContent { Screen(given, onBack = { backs++ }) }

            onNodeWithContentDescription("Back").performClick()

            assertEquals(1, backs)
        }

    @androidx.compose.runtime.Composable
    private fun Screen(
        state: SettingsUiState,
        onAction: (SettingsAction) -> Unit = {},
        onBack: () -> Unit = {},
        onReviewConsent: () -> Unit = {},
    ) = SettingsScreen(state, onAction, onBack, onReviewConsent, onOpenHearingCheck = {})
}
