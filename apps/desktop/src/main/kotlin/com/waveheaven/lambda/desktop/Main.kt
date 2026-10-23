package com.waveheaven.lambda.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.waveheaven.lambda.shared.App

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "WaveHeaven Lambda",
            state = rememberWindowState(width = 480.dp, height = 720.dp),
        ) {
            App()
        }
    }
