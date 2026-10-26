package com.waveheaven.lambda.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.db.SqlDriver
import com.waveheaven.lambda.core.data.createDesktopDriver
import com.waveheaven.lambda.core.domain.consent.MicrophoneAccess
import com.waveheaven.lambda.shared.App
import com.waveheaven.lambda.shared.initKoin
import org.koin.dsl.module

/** Set by the jpackage launcher of installed builds; absent when running from Gradle. */
private val appVersion: String = System.getProperty("jpackage.app-version") ?: "dev"

fun main() {
    initKoin {
        modules(module { single<SqlDriver> { createDesktopDriver() } })
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "WaveHeaven Lambda",
            state = rememberWindowState(width = 480.dp, height = 720.dp),
        ) {
            App(appVersion = appVersion, microphoneAccess = DesktopMicrophoneAccess)
        }
    }
}

/** Desktop systems do not prompt Java applications for the microphone; access is managed in the OS settings. */
private object DesktopMicrophoneAccess : MicrophoneAccess {
    override fun isGranted(): Boolean = true

    override fun request(onResult: (granted: Boolean) -> Unit) = onResult(true)

    override fun openSystemSettings() = Unit
}
