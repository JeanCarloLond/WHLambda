package com.waveheaven.lambda.shared

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.koin.core.context.stopKoin
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {
    private lateinit var systemLocale: Locale

    @BeforeTest
    fun setUp() {
        systemLocale = Locale.getDefault()
        initKoin()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Locale.setDefault(systemLocale)
    }

    @Test
    fun `starts on the dose explorer`() =
        runComposeUiTest {
            Locale.setDefault(Locale.ENGLISH)
            setContent { App(appVersion = "test") }

            onNodeWithText("Level: 85 dB(A)").assertIsDisplayed()
            onNodeWithText("8 h").assertIsDisplayed()
        }

    @Test
    fun `navigates to about and back`() =
        runComposeUiTest {
            Locale.setDefault(Locale.ENGLISH)
            setContent { App(appVersion = "test") }

            onNodeWithContentDescription("About WaveHeaven").performClick()
            onNodeWithText("Version test").assertIsDisplayed()

            onNodeWithContentDescription("Back").performClick()
            onNodeWithText("Level: 85 dB(A)").assertIsDisplayed()
        }

    @Test
    fun `is translated to Spanish`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("es"))
            setContent { App(appVersion = "test") }

            onNodeWithText("Nivel: 85 dB(A)").assertIsDisplayed()
            onNodeWithText("Tiempo seguro por semana (OMS)").assertIsDisplayed()
        }
}
