package com.waveheaven.lambda.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import app.cash.sqldelight.db.SqlDriver
import com.waveheaven.lambda.core.audio.AdaptiveVolumeSession
import com.waveheaven.lambda.core.audio.DesktopAmbientMeter
import com.waveheaven.lambda.core.audio.DesktopMediaVolume
import com.waveheaven.lambda.core.audio.DesktopOutputLevelModels
import com.waveheaven.lambda.core.audio.DesktopTonePlayer
import com.waveheaven.lambda.core.data.createDesktopDriver
import com.waveheaven.lambda.core.domain.alert.DoseAlertNotifier
import com.waveheaven.lambda.core.domain.audio.AmbientMeter
import com.waveheaven.lambda.core.domain.consent.MicrophoneAccess
import com.waveheaven.lambda.core.domain.consent.NotificationAccess
import com.waveheaven.lambda.core.domain.consent.UserPreferences
import com.waveheaven.lambda.core.domain.hearing.TonePlayer
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolume
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.core.domain.volume.MediaVolume
import com.waveheaven.lambda.core.domain.volume.OutputLevelModels
import com.waveheaven.lambda.core.domain.volume.RunningAudioApps
import com.waveheaven.lambda.shared.App
import com.waveheaven.lambda.shared.initKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import org.koin.dsl.module
import java.awt.SystemTray

/** Set by the jpackage launcher of installed builds; absent when running from Gradle. */
private val appVersion: String = System.getProperty("jpackage.app-version") ?: "dev"

/** Lives as long as the app: follows preferences the audio adapters read synchronously. */
private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Desktop bindings: per-application volume and capture (ADR-0009), the loop itself, and tray alerts. */
private val desktopModule =
    module {
        single<SqlDriver> { createDesktopDriver() }
        single {
            val mediaApp = get<UserPreferences>().mediaApp.stateIn(appScope, SharingStarted.Eagerly, null)
            DesktopMediaVolume { mediaApp.value }
        }
        single<MediaVolume> { get<DesktopMediaVolume>() }
        single<RunningAudioApps> { RunningAudioApps { get<DesktopMediaVolume>().runningApps() } }
        single<OutputLevelModels> { DesktopOutputLevelModels() }
        single<AmbientMeter> {
            val preferences = get<UserPreferences>()
            DesktopAmbientMeter { preferences.microphoneAdjustmentDb.first() }
        }
        single { TrayDoseAlertNotifier(appScope) }
        single<DoseAlertNotifier> { get<TrayDoseAlertNotifier>() }
        single<AdaptiveVolume> { get<AdaptiveVolumeSession>() }
        single<TonePlayer> { DesktopTonePlayer() }
    }

fun main() {
    val koin = initKoin { modules(desktopModule) }.koin
    val session = koin.get<AdaptiveVolumeSession>()
    val alerts = koin.get<TrayDoseAlertNotifier>()
    // Some Linux desktops have no system tray; then closing the window quits, or the app would be unreachable.
    val hasTray = SystemTray.isSupported()

    application {
        var windowVisible by remember { mutableStateOf(true) }
        val trayState = rememberTrayState()
        LaunchedEffect(trayState) { alerts.attach(trayState) }
        if (hasTray) {
            WaveHeavenTray(
                session = session,
                state = trayState,
                onOpen = { windowVisible = true },
                onQuit = {
                    session.stop()
                    exitApplication()
                },
            )
        }
        if (windowVisible) {
            Window(
                onCloseRequest = {
                    // While the loop runs, closing the window keeps it going in the tray (#17).
                    if (hasTray && session.status.value.state != AdaptiveVolumeState.STOPPED) {
                        windowVisible = false
                    } else {
                        session.stop()
                        exitApplication()
                    }
                },
                title = "WaveHeaven Lambda",
                state = rememberWindowState(width = 480.dp, height = 720.dp),
            ) {
                App(appVersion = appVersion, microphoneAccess = DesktopMicrophoneAccess, notificationAccess = DesktopNotificationAccess)
            }
        }
    }
}

/** Desktop systems do not prompt Java applications for the microphone; access is managed in the OS settings. */
private object DesktopMicrophoneAccess : MicrophoneAccess {
    override fun isGranted(): Boolean = true

    override fun request(onResult: (granted: Boolean) -> Unit) = onResult(true)

    override fun openSystemSettings() = Unit
}

/** Desktop notifications need no permission from Java applications. */
private object DesktopNotificationAccess : NotificationAccess {
    override fun isGranted(): Boolean = true

    override fun request(onResult: (granted: Boolean) -> Unit) = onResult(true)
}
