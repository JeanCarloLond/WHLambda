package com.waveheaven.lambda.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waveheaven.lambda.core.designsystem.theme.WaveHeavenTheme
import com.waveheaven.lambda.shared.navigation.AppNavigation

/**
 * Entry point shared by the Android and desktop apps. Koin must be started first (see [initKoin]).
 *
 * @param appVersion user-visible version, provided by each platform.
 */
@Composable
fun App(
    appVersion: String,
    modifier: Modifier = Modifier,
) {
    WaveHeavenTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            AppNavigation(appVersion = appVersion)
        }
    }
}
