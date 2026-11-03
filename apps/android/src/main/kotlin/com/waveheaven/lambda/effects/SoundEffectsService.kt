package com.waveheaven.lambda.effects

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.waveheaven.lambda.core.audio.AndroidPlaybackSessions
import com.waveheaven.lambda.core.audio.EffectsInputs
import com.waveheaven.lambda.core.audio.SoundEffectsEngine
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStore
import com.waveheaven.lambda.core.domain.profile.ListeningProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the equaliser and effects on other apps' playback (#40, ADR-0010). Effects live as long as this process
 * holds them, so a foreground service keeps the process alive while they are on. Started from a visible screen, at
 * boot or after an update when effects were on (WH-096), and restarted by the system if it is killed.
 */
class SoundEffectsService : Service() {
    private val engine: SoundEffectsEngine by inject()
    private val store: SoundEffectsStore by inject()
    private val profiles: ListeningProfileStore by inject()
    private val sessions: AndroidPlaybackSessions by inject()

    // Effects are created on the main thread so their control callbacks have a looper (AndroidEffectChain).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var running: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            // A null intent is the system restarting the service after killing it.
            ACTION_START, null -> start()

            ACTION_TURN_OFF -> turnOff()

            else -> stopSelf()
        }
        return START_STICKY
    }

    private fun start() {
        val notification = SoundEffectsNotifications.build(this, engine.status.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(SoundEffectsNotifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(SoundEffectsNotifications.ID, notification)
        }
        if (running != null) return
        running =
            scope.launch {
                if (!store.settings.first().enabled) {
                    stopSelf()
                    return@launch
                }
                launch { stopWhenTurnedOff() }
                launch { updateNotification() }
                engine.run(
                    combine(store.settings, profiles.activeProfile(), sessions.sessions()) { settings, profile, list ->
                        EffectsInputs(settings.resolve(profile.eq), settings.mode, list)
                    },
                )
            }
    }

    private suspend fun stopWhenTurnedOff() {
        store.settings
            .map { it.enabled }
            .distinctUntilChanged()
            .collect { enabled -> if (!enabled) stopSelf() }
    }

    private suspend fun updateNotification() {
        engine.status.collect { status ->
            if (canNotify()) {
                getSystemService(NotificationManager::class.java)
                    .notify(SoundEffectsNotifications.ID, SoundEffectsNotifications.build(this, status))
            }
        }
    }

    /** The notification's "Turn off": the setting goes off too, so effects do not come back at the next boot. */
    private fun turnOff() {
        scope.launch {
            store.save(store.settings.first().copy(enabled = false))
            stopSelf()
        }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        // Cancelling the engine releases every effect, so other apps' audio is left as it was.
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.waveheaven.lambda.effects.START"
        const val ACTION_TURN_OFF = "com.waveheaven.lambda.effects.TURN_OFF"

        fun intent(
            context: Context,
            action: String,
        ): Intent = Intent(context, SoundEffectsService::class.java).setAction(action)
    }
}
