package com.waveheaven.lambda.adaptive

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.waveheaven.lambda.core.audio.AdaptiveVolumeSession
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The `microphone` foreground service that hosts the adaptive-volume loop while the user is in other apps (#10,
 * WH-020 – WH-024). It only starts from [ACTION_START], sent when the user taps Start on a visible screen; it is never
 * restarted by the system ([START_NOT_STICKY]) and never started at boot. It holds no wake lock of its own: while
 * capturing, the audio server keeps the CPU awake (spike #2).
 */
class AdaptiveVolumeService : Service() {
    private val session: AdaptiveVolumeSession by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> session.pause()
            ACTION_RESUME -> session.resume()
            ACTION_STOP -> session.stop()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        val notification = AdaptiveVolumeNotifications.build(this, session.status.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(AdaptiveVolumeNotifications.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            // Android 10 has no microphone service type; capture in the background is not restricted there.
            startForeground(AdaptiveVolumeNotifications.ID, notification)
        }
        session.start()
        if (!observing) {
            observing = true
            scope.launch {
                session.status
                    .map { it to AdaptiveVolumeNotifications.content(this@AdaptiveVolumeService, it) }
                    .distinctUntilChanged { old, new -> old.second == new.second }
                    .collect { (status, _) ->
                        if (status.state == AdaptiveVolumeState.STOPPED || status.state == AdaptiveVolumeState.UNAVAILABLE) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else if (canNotify()) {
                            getSystemService(NotificationManager::class.java)
                                .notify(
                                    AdaptiveVolumeNotifications.ID,
                                    AdaptiveVolumeNotifications.build(this@AdaptiveVolumeService, status),
                                )
                        }
                    }
            }
        }
    }

    /** Updates need the notification permission on Android 13+; without it the loop runs on, unseen in the shade. */
    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        // If the system or the user ends the service, the loop must not keep the microphone.
        session.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.waveheaven.lambda.adaptive.START"
        const val ACTION_PAUSE = "com.waveheaven.lambda.adaptive.PAUSE"
        const val ACTION_RESUME = "com.waveheaven.lambda.adaptive.RESUME"
        const val ACTION_STOP = "com.waveheaven.lambda.adaptive.STOP"

        fun intent(
            context: Context,
            action: String,
        ): Intent = Intent(context, AdaptiveVolumeService::class.java).setAction(action)
    }
}
