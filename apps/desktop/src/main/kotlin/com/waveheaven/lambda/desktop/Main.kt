package com.waveheaven.lambda.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.waveheaven.lambda.shared.App
import com.waveheaven.lambda.shared.initKoin

/** Set by the jpackage launcher of installed builds; absent when running from Gradle. */
private val appVersion: String = System.getProperty("jpackage.app-version") ?: "dev"

fun main() {
    initKoin()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "WaveHeaven Lambda",
            state = rememberWindowState(width = 480.dp, height = 720.dp),
        ) {
            App(appVersion = appVersion)
        }
    }
}
