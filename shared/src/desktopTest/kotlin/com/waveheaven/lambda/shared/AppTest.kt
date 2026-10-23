package com.waveheaven.lambda.shared

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppTest {
    private lateinit var systemLocale: Locale

    @BeforeTest
    fun rememberLocale() {
        systemLocale = Locale.getDefault()
    }

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(systemLocale)
    }

    @Test
    fun `shows the NIOSH safe time for the default 85 dBA level`() =
        runComposeUiTest {
            Locale.setDefault(Locale.ENGLISH)
            setContent { App() }

            onNodeWithText("Level: 85 dB(A)").assertIsDisplayed()
            onNodeWithText("Safe time per day (NIOSH)").assertIsDisplayed()
            onNodeWithText("8 h").assertIsDisplayed()
        }

    @Test
    fun `is translated to Spanish`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("es"))
            setContent { App() }

            onNodeWithText("Nivel: 85 dB(A)").assertIsDisplayed()
            onNodeWithText("Tiempo seguro por semana (OMS)").assertIsDisplayed()
        }
}
