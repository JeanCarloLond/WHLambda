package com.waveheaven.lambda.feature.dashboard

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
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
                    onOpenExplorer = { opened += "explorer" },
                    onOpenProfiles = { opened += "profiles" },
                    onOpenSettings = { opened += "settings" },
                    onOpenAbout = { opened += "about" },
                )
            }

            onNodeWithContentDescription("Settings").performClick()
            onNodeWithContentDescription("About WaveHeaven").performClick()
            onNodeWithText("Profile: Gym").performClick()
            onNodeWithText("Explore listening levels").performScrollTo().performClick()

            assertEquals(listOf("settings", "about", "profiles", "explorer"), opened)
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

    @androidx.compose.runtime.Composable
    private fun Dashboard(state: DashboardUiState) =
        DashboardScreen(state = state, onOpenExplorer = {}, onOpenProfiles = {}, onOpenSettings = {}, onOpenAbout = {})
}
