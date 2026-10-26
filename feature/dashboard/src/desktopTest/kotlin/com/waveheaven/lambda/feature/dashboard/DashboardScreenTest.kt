package com.waveheaven.lambda.feature.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.waveheaven.lambda.core.domain.consent.ConsentStatus
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalTestApi::class)
class DashboardScreenTest {
    private lateinit var systemLocale: Locale

    private val state =
        DashboardUiState(
            isLoading = false,
            daily = BudgetUi(DoseStandard.NIOSH, 50, BudgetStatus.ON_TRACK, 30.minutes),
            weekly = BudgetUi(DoseStandard.WHO_ADULT, 85, BudgetStatus.CLOSE_TO_LIMIT, 2.hours),
            latestLevel = LevelUi(94, 6),
            history = (1..7).map { HistoryDayUi(isoDayOfWeek = it, percent = it * 20, isToday = it == 7) },
            profile = ActiveProfileUi("gym", "Gym", isBuiltIn = true),
        )

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
    fun `shows both budgets with their status and time left, with the level's margin`() =
        runComposeUiTest {
            setContent { Dashboard(state) }

            onNodeWithText("Today (NIOSH)").assertIsDisplayed()
            onNodeWithText("50 %").assertIsDisplayed()
            onNodeWithText("30 min left at 94 ± 6 dB(A)").assertIsDisplayed()
            onNodeWithText("Close to the limit").assertIsDisplayed()
            onNodeWithText("2 h left at 94 ± 6 dB(A)").assertIsDisplayed()
        }

    @Test
    fun `a spent budget says what to do instead of a time left`() =
        runComposeUiTest {
            setContent { Dashboard(state.copy(daily = BudgetUi(DoseStandard.NIOSH, 120, BudgetStatus.LIMIT_REACHED, null))) }

            onNodeWithText("Budget used up: take a break or lower the volume").assertIsDisplayed()
        }

    @Test
    fun `history bars are read out with their day and value`() =
        runComposeUiTest {
            setContent { Dashboard(state) }

            onNodeWithContentDescription("Mon: 20 %").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Today: 140 %").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `tells the user when nothing was recorded yet`() =
        runComposeUiTest {
            setContent { Dashboard(DashboardUiState(isLoading = false)) }

            onNodeWithText("No listening recorded yet.").assertIsDisplayed()
        }

    @Test
    fun `navigation buttons report their intents`() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            setContent {
                DashboardScreen(
                    state = state,
                    onAction = {},
                    onOpenMicrophoneSettings = {},
                    onOpenExplorer = { opened += "explorer" },
                    onOpenProfiles = { opened += "profiles" },
                    onOpenSettings = { opened += "settings" },
                    onOpenAbout = { opened += "about" },
                    onReviewConsent = { opened += "consent" },
                )
            }

            onNodeWithContentDescription("Settings").performClick()
            onNodeWithContentDescription("About WaveHeaven").performClick()
            onNodeWithText("Profile: Gym").performClick()
            onNodeWithText("Explore listening levels").performScrollTo().performClick()

            assertEquals(listOf("settings", "about", "profiles", "explorer"), opened)
        }

    @Test
    fun `without consent explains that adaptive volume is off and leads to consent`() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            setContent {
                DashboardScreen(
                    state = state.copy(consentStatus = com.waveheaven.lambda.core.domain.consent.ConsentStatus.MISSING),
                    onAction = {},
                    onOpenMicrophoneSettings = {},
                    onOpenExplorer = {},
                    onOpenProfiles = {},
                    onOpenSettings = {},
                    onOpenAbout = {},
                    onReviewConsent = { opened += "consent" },
                )
            }

            onNodeWithText("Adaptive volume is off").assertIsDisplayed()
            onNodeWithText("Review and give consent").performClick()

            assertEquals(listOf("consent"), opened)
        }

    @Test
    fun `is translated to Spanish`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("es"))
            setContent { Dashboard(state) }

            onNodeWithText("Hoy (NIOSH)").assertIsDisplayed()
            onNodeWithText("Te quedan 30 min a 94 ± 6 dB(A)").assertIsDisplayed()
            onNodeWithText("Perfil: Gimnasio").assertIsDisplayed()
        }

    @Test
    fun `adaptive volume can be started when off`() =
        runComposeUiTest {
            val actions = mutableListOf<DashboardAction>()
            setContent { Dashboard(state.copy(adaptive = AdaptiveUi(AdaptiveVolumeState.STOPPED)), actions::add) }

            onNodeWithText("Off. Turn it on", substring = true).assertIsDisplayed()
            onNodeWithText("Start").performClick()

            assertEquals(listOf<DashboardAction>(DashboardAction.Start), actions)
        }

    @Test
    fun `without consent adaptive volume cannot start`() =
        runComposeUiTest {
            setContent { Dashboard(state.copy(consentStatus = ConsentStatus.MISSING, adaptive = AdaptiveUi(AdaptiveVolumeState.STOPPED))) }

            onNodeWithText("Start").assertIsNotEnabled()
        }

    @Test
    fun `while running it shows the noise and the music level with its margin, and says when the limit holds it down`() =
        runComposeUiTest {
            val actions = mutableListOf<DashboardAction>()
            val running =
                AdaptiveUi(AdaptiveVolumeState.RUNNING, ambientDb = 78, output = LevelUi(86, 8), hint = AdaptiveHint.LIMITED_BY_DOSE)
            setContent { Dashboard(state.copy(adaptive = running), actions::add) }

            onNodeWithText("Noise 78 dB(A) · music 86 ± 8 dB(A)").assertIsDisplayed()
            onNodeWithText("Your safe-listening limit is holding the volume down", substring = true).assertIsDisplayed()
            onNodeWithText("Pause").performClick()
            onNodeWithText("Stop").performClick()

            assertEquals(listOf(DashboardAction.Pause, DashboardAction.Stop), actions)
        }

    @Test
    fun `paused can resume, and waiting says the microphone is off`() =
        runComposeUiTest {
            val actions = mutableListOf<DashboardAction>()
            setContent { Dashboard(state.copy(adaptive = AdaptiveUi(AdaptiveVolumeState.PAUSED)), actions::add) }

            onNodeWithText("Paused. The microphone is off.").assertIsDisplayed()
            onNodeWithText("Resume").performClick()

            assertEquals(listOf<DashboardAction>(DashboardAction.Resume), actions)
        }

    @Test
    fun `a refused microphone is explained with a way to settings`() =
        runComposeUiTest {
            var opened = 0
            setContent {
                DashboardScreen(
                    state = state.copy(adaptive = AdaptiveUi(AdaptiveVolumeState.STOPPED, microphoneDenied = true)),
                    onAction = {},
                    onOpenMicrophoneSettings = { opened++ },
                    onOpenExplorer = {},
                    onOpenProfiles = {},
                    onOpenSettings = {},
                    onOpenAbout = {},
                    onReviewConsent = {},
                )
            }

            onNodeWithText("WaveHeaven needs the microphone", substring = true).assertIsDisplayed()
            onNodeWithText("Open system settings").performClick()

            assertEquals(1, opened)
        }

    @Test
    fun `the adaptive card is hidden where the loop is unavailable`() =
        runComposeUiTest {
            setContent { Dashboard(state) }

            onNodeWithText("Adaptive volume").assertDoesNotExist()
        }

    @androidx.compose.runtime.Composable
    private fun Dashboard(
        state: DashboardUiState,
        onAction: (DashboardAction) -> Unit = {},
    ) = DashboardScreen(
        state,
        onAction = onAction,
        onOpenMicrophoneSettings = {},
        onOpenExplorer = {},
        onOpenProfiles = {},
        onOpenSettings = {},
        onOpenAbout = {},
        onReviewConsent = {},
    )
}
