package com.waveheaven.lambda.feature.profiles

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfilesScreenTest {
    private lateinit var systemLocale: Locale

    private val state =
        ProfilesUiState(
            profiles =
                listOf(
                    ProfileItemUi("commute", "Commute", isBuiltIn = true, targetSnrDb = 10, minPercent = 10, maxPercent = 100),
                    ProfileItemUi("study", "Study", isBuiltIn = false, targetSnrDb = 4, minPercent = 5, maxPercent = 40),
                ),
            activeId = "commute",
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
    fun `shows each profile's settings and marks the active one`() =
        runComposeUiTest {
            setContent { ProfilesScreen(state = state, onAction = {}, onBack = {}) }

            onNodeWithText("+10 dB over the noise · volume 10–100\u00A0%").assertIsDisplayed()
            onNodeWithText("Commute").assertIsDisplayed()
            onNodeWithText("Study").assertIsDisplayed()
        }

    @Test
    fun `selecting, editing and deleting report their intents`() =
        runComposeUiTest {
            val actions = mutableListOf<ProfilesAction>()
            setContent { ProfilesScreen(state = state, onAction = { actions += it }, onBack = {}) }

            onNodeWithText("Study").performClick()
            onNodeWithContentDescription("Edit Study").performClick()
            onNodeWithContentDescription("Delete Study").performClick()
            onNodeWithText("New profile").performClick()

            assertEquals(
                listOf(
                    ProfilesAction.Select("study"),
                    ProfilesAction.EditClicked("study"),
                    ProfilesAction.DeleteClicked("study"),
                    ProfilesAction.CreateClicked,
                ),
                actions,
            )
        }

    @Test
    fun `built-in profiles cannot be edited or deleted`() =
        runComposeUiTest {
            setContent { ProfilesScreen(state = state, onAction = {}, onBack = {}) }

            onNodeWithContentDescription("Edit Commute").assertDoesNotExist()
            onNodeWithContentDescription("Delete Commute").assertDoesNotExist()
        }

    @Test
    fun `the editor shows its values and disables saving an unnamed profile`() =
        runComposeUiTest {
            val editor = ProfileEditorUi(id = null, name = "", targetSnrDb = 10f, minPercent = 10f, maxPercent = 100f)
            setContent { ProfilesScreen(state = state.copy(editor = editor), onAction = {}, onBack = {}) }

            onNodeWithText("Margin over the noise: +10 dB").assertIsDisplayed()
            onNodeWithText("Volume range: 10–100\u00A0%").assertIsDisplayed()
            onNodeWithText("Save").assertIsNotEnabled()
        }

    @Test
    fun `built-in names are translated`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("es"))
            setContent { ProfilesScreen(state = state, onAction = {}, onBack = {}) }

            onNodeWithText("Transporte").assertIsDisplayed()
            onNodeWithText("Study").assertIsDisplayed()
        }
}
