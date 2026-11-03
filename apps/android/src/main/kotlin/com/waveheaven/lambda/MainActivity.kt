package com.waveheaven.lambda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.waveheaven.lambda.core.domain.effects.SoundEffects
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStore
import com.waveheaven.lambda.shared.App
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val microphoneAccess = AndroidMicrophoneAccess(this)
    private val notificationAccess = AndroidNotificationAccess(this)
    private val soundEffects: SoundEffects by inject()
    private val soundEffectsStore: SoundEffectsStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App(appVersion = BuildConfig.VERSION_NAME, microphoneAccess = microphoneAccess, notificationAccess = notificationAccess)
        }
        // Effects that were on come back whenever the app is opened, e.g. after the user force-stopped it (WH-096).
        lifecycleScope.launch { if (soundEffectsStore.settings.first().enabled) soundEffects.start() }
    }

    override fun onResume() {
        super.onResume()
        // The diagnostic permission may have been granted over ADB while the app was in the background.
        soundEffects.refresh()
    }
}
