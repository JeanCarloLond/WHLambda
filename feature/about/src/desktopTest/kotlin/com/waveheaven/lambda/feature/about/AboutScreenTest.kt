package com.waveheaven.lambda.feature.about

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AboutScreenTest {
    private lateinit var systemLocale: Locale

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
    fun `shows the app version`() =
        runComposeUiTest {
            setContent { AboutScreen(appVersion = "1.2.3", onBack = {}) }

            onNodeWithText("Version 1.2.3").assertIsDisplayed()
        }

    @Test
    fun `opens the source code repository`() =
        runComposeUiTest {
            val opened = mutableListOf<String>()
            val uriHandler =
                object : UriHandler {
                    override fun openUri(uri: String) {
                        opened += uri
                    }
                }
            setContent {
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    AboutScreen(appVersion = "1.2.3", onBack = {})
                }
            }

            onNodeWithText("View source code").performScrollTo().performClick()

            assertEquals(listOf(SOURCE_CODE_URL), opened)
        }

    @Test
    fun `the back button reports the intent`() =
        runComposeUiTest {
            var backs = 0
            setContent { AboutScreen(appVersion = "1.2.3", onBack = { backs++ }) }

            onNodeWithContentDescription("Back").performClick()

            assertEquals(1, backs)
        }
}
