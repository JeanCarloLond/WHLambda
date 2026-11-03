package com.waveheaven.lambda.effects

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.waveheaven.lambda.core.domain.effects.SoundEffects
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Brings the effects back after a reboot or an app update when they were on (WH-096). Both broadcasts allow starting
 * a foreground service from the background. The adaptive-volume service, which uses the microphone, is never
 * started this way (WH-024).
 */
class SoundEffectsBootReceiver :
    BroadcastReceiver(),
    KoinComponent {
    private val store: SoundEffectsStore by inject()
    private val effects: SoundEffects by inject()

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (store.settings.first().enabled) effects.start()
            } finally {
                pending.finish()
            }
        }
    }
}
