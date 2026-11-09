package com.waveheaven.lambda.feature.soundeffects

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.waveheaven.lambda.core.domain.effects.EffectsMode
import com.waveheaven.lambda.core.domain.effects.EqPreset
import com.waveheaven.lambda.core.domain.effects.ReverbPreset
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SoundEffectsScreenTest {
    private lateinit var systemLocale: Locale

    private val state =
        SoundEffectsUiState(
            isLoading = false,
            enabled = true,
            preset = EqPreset.ROCK,
            bandGainsDb = listOf(5, 4, 3, 1, -1, -1, 1, 3, 4, 5),
            running = true,
            apps = listOf("Spotify", "VLC"),
            detectionCommand = "adb shell pm grant com.example android.permission.DUMP",
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
    fun `writes out every band and effect value`() =
        runComposeUiTest {
            setContent { SoundEffectsScreen(state = state.copy(bassBoostDb = 6, extraVolumeDb = 3), onAction = {}, onBack = {}) }

            onNodeWithText("Rock").assertIsSelected()
            onNodeWithText("31 Hz: +5 dB").performScrollTo().assertIsDisplayed()
            onNodeWithText("1 kHz: -1 dB").performScrollTo().assertIsDisplayed()
            onNodeWithText("16 kHz: +5 dB").performScrollTo().assertIsDisplayed()
            onNodeWithText("Bass boost: 6 dB").performScrollTo().assertIsDisplayed()
            onNodeWithText("Extra volume: 3 dB").performScrollTo().assertIsDisplayed()
            onNodeWithText("With effects now: Spotify, VLC").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `custom only appears while selected`() =
        runComposeUiTest {
            setContent { SoundEffectsScreen(state = state, onAction = {}, onBack = {}) }
            assertEquals(0, onAllNodesWithText("Custom").fetchSemanticsNodes().size)
        }

    @Test
    fun `the switch, presets, reverb and mode report their intents`() =
        runComposeUiTest {
            val actions = mutableListOf<SoundEffectsAction>()
            setContent { SoundEffectsScreen(state = state, onAction = { actions += it }, onBack = {}) }

            onNodeWithText("Apply to my music").performClick()
            onNodeWithText("Jazz").performClick()
            onNodeWithText("Large hall").performScrollTo().performClick()
            onNodeWithText("All the phone’s audio").performScrollTo().performClick()

            assertEquals(
                listOf(
                    SoundEffectsAction.EnabledChanged(false),
                    SoundEffectsAction.PresetSelected(EqPreset.JAZZ),
                    SoundEffectsAction.ReverbSelected(ReverbPreset.LARGE_HALL),
                    SoundEffectsAction.ModeSelected(EffectsMode.GLOBAL),
                ),
                actions,
            )
        }

    @Test
    fun `a band slider reports its band`() =
        runComposeUiTest {
            val actions = mutableListOf<SoundEffectsAction>()
            setContent { SoundEffectsScreen(state = state, onAction = { actions += it }, onBack = {}) }

            onNodeWithContentDescription("2 kHz: +1 dB").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }

            assertEquals(listOf<SoundEffectsAction>(SoundEffectsAction.BandChanged(6, 4f)), actions)
        }

    @Test
    fun `warns about boosts and lost control`() =
        runComposeUiTest {
            setContent {
                SoundEffectsScreen(
                    state = state.copy(raisesLevel = true, lostControl = listOf("YouTube Music")),
                    onAction = {},
                    onBack = {},
                )
            }

            onNodeWithText("These settings make the sound louder in your ears", substring = true).assertIsDisplayed()
            onNodeWithText("Another equaliser app took over the effects of YouTube Music", substring = true).assertIsDisplayed()
        }

    @Test
    fun `without full detection shows the command and checks again`() =
        runComposeUiTest {
            val actions = mutableListOf<SoundEffectsAction>()
            setContent { SoundEffectsScreen(state = state, onAction = { actions += it }, onBack = {}) }

            onNodeWithText("adb shell pm grant com.example android.permission.DUMP").performScrollTo().assertIsDisplayed()
            onNodeWithText("I ran it, check again").performScrollTo().performClick()

            assertEquals(listOf<SoundEffectsAction>(SoundEffectsAction.CheckDetection), actions)
        }

    @Test
    fun `with full detection the command is not shown`() =
        runComposeUiTest {
            setContent { SoundEffectsScreen(state = state.copy(fullDetection = true), onAction = {}, onBack = {}) }
            onNodeWithText("Full detection is on", substring = true).performScrollTo().assertIsDisplayed()
            assertEquals(0, onAllNodesWithText("I ran it, check again").fetchSemanticsNodes().size)
        }

    @Test
    fun `global mode is described, with lost control of the whole output`() =
        runComposeUiTest {
            setContent {
                SoundEffectsScreen(
                    state = state.copy(mode = EffectsMode.GLOBAL, apps = emptyList(), wholeOutput = true, lostWholeOutput = true),
                    onAction = {},
                    onBack = {},
                )
            }
            onNodeWithText("Applied to all the phone’s audio.").performScrollTo().assertIsDisplayed()
            onNodeWithText("took over the effects of all the phone’s audio", substring = true).performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `effects off, nothing detected, profile curve and surround`() =
        runComposeUiTest {
            setContent {
                SoundEffectsScreen(
                    state =
                        state.copy(
                            enabled = false,
                            running = false,
                            profileWithEq = "My hearing",
                            surroundSupported = true,
                            surroundPercent = 40,
                        ),
                    onAction = {},
                    onBack = {},
                )
            }
            onNodeWithText("Apply to my music").assertIsOff()
            onNodeWithText("Your profile “My hearing” has its own curve", substring = true).assertIsDisplayed()
            onNodeWithText("Surround: 40 %").performScrollTo().assertIsDisplayed()
            onNodeWithText("Effects are off.").performScrollTo().assertIsDisplayed()
        }

    @Test
    fun `running with no app yet asks to play something, and back works`() =
        runComposeUiTest {
            var back = false
            setContent { SoundEffectsScreen(state = state.copy(apps = emptyList()), onAction = {}, onBack = { back = true }) }
            onNodeWithText("No music app detected yet. Play something.").performScrollTo().assertIsDisplayed()
            onNodeWithContentDescription("Back").performClick()
            assertTrue(back)
        }
}
