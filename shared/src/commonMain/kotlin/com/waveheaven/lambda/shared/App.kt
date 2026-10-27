package com.waveheaven.lambda.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.waveheaven.lambda.core.designsystem.theme.WaveHeavenTheme
import com.waveheaven.lambda.core.domain.consent.MicrophoneAccess
import com.waveheaven.lambda.core.domain.consent.NotificationAccess
import com.waveheaven.lambda.shared.navigation.AppNavigation

/**
 * Entry point shared by the Android and desktop apps. Koin must be started first (see [initKoin]).
 *
 * @param appVersion user-visible version, provided by each platform.
 * @param microphoneAccess the platform's microphone permission; on Android it is tied to the activity, so it is
 *   passed in here rather than through Koin.
 * @param notificationAccess the platform's notification permission, likewise.
 */
@Composable
fun App(
    appVersion: String,
    microphoneAccess: MicrophoneAccess,
    notificationAccess: NotificationAccess,
    modifier: Modifier = Modifier,
) {
    WaveHeavenTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            AppNavigation(appVersion = appVersion, microphoneAccess = microphoneAccess, notificationAccess = notificationAccess)
        }
    }
}
