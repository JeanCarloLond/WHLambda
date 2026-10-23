package com.waveheaven.lambda.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waveheaven.lambda.core.designsystem.theme.WaveHeavenTheme
import com.waveheaven.lambda.shared.exposure.DoseExplorerScreen

/** Entry point shared by the Android and desktop apps. */
@Composable
fun App(modifier: Modifier = Modifier) {
    WaveHeavenTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            // Keeps content clear of system bars and cutouts on Android; a no-op on desktop.
            DoseExplorerScreen(modifier = Modifier.safeDrawingPadding())
        }
    }
}
