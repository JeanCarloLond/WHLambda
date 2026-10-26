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
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

// The dashboard loads from the database off the UI thread, which Compose's idling does not track, so its texts
// are awaited with waitUntilExactlyOneExists instead of asserted right away.
@OptIn(ExperimentalTestApi::class)
class AppTest {
    private lateinit var systemLocale: Locale

    @BeforeTest
    fun setUp() {
        systemLocale = Locale.getDefault()
        initKoin {
            modules(module { single<SqlDriver> { createInMemoryDriver() } })
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Locale.setDefault(systemLocale)
    }

    @Test
    fun `starts on the dashboard`() =
        runComposeUiTest {
            Locale.setDefault(Locale.ENGLISH)
            setContent { App(appVersion = "test") }

            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))
            waitUntilExactlyOneExists(hasText("No listening recorded yet."))
        }

    @Test
    fun `navigates to the dose explorer and back`() =
        runComposeUiTest {
            Locale.setDefault(Locale.ENGLISH)
            setContent { App(appVersion = "test") }

            onNodeWithText("Explore listening levels").performScrollTo().performClick()
            onNodeWithText("Level: 85 dB(A)").assertIsDisplayed()
            onNodeWithText("8 h").assertIsDisplayed()

            onNodeWithContentDescription("Back").performClick()
            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))
        }

    @Test
    fun `navigates to about and back`() =
        runComposeUiTest {
            Locale.setDefault(Locale.ENGLISH)
            setContent { App(appVersion = "test") }

            onNodeWithContentDescription("About WaveHeaven").performClick()
            onNodeWithText("Version test").assertIsDisplayed()

            onNodeWithContentDescription("Back").performClick()
            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))
        }

    @Test
    fun `navigates to settings and back`() =
        runComposeUiTest {
            Locale.setDefault(Locale.ENGLISH)
            setContent { App(appVersion = "test") }

            onNodeWithContentDescription("Settings").performClick()
            onNodeWithText("Delete my listening data").assertIsDisplayed()

            onNodeWithContentDescription("Back").performClick()
            waitUntilExactlyOneExists(hasText("Today (NIOSH)"))
        }

    @Test
    fun `is translated to Spanish`() =
        runComposeUiTest {
            Locale.setDefault(Locale.forLanguageTag("es"))
            setContent { App(appVersion = "test") }

            waitUntilExactlyOneExists(hasText("Hoy (NIOSH)"))
            waitUntilExactlyOneExists(hasText("Últimos 7 días (OMS)"))
        }
}
