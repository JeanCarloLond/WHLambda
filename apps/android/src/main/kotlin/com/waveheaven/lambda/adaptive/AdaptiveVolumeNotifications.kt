package com.waveheaven.lambda.adaptive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.waveheaven.lambda.MainActivity
import com.waveheaven.lambda.R
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeStatus
import kotlin.math.roundToInt

/** The persistent notification of the adaptive-volume service: what it measures, today's dose, Pause and Stop (WH-021). */
object AdaptiveVolumeNotifications {
    const val ID = 1
    private const val CHANNEL_ID = "adaptive_volume"
    private const val PERCENT = 100

    /** Title and text, also used to skip updates that would not change what the user sees. */
    data class Content(
        val title: String,
        val text: String,
    )

    fun content(
        context: Context,
        status: AdaptiveVolumeStatus,
    ): Content {
        val today = status.dailyDose?.let { (it.fraction * PERCENT).roundToInt() }
        return when (status.state) {
            AdaptiveVolumeState.RUNNING -> {
                Content(
                    context.getString(R.string.adaptive_running_title),
                    listOfNotNull(
                        status.ambient?.let { context.getString(R.string.adaptive_noise, it.decibels.roundToInt()) },
                        today?.let { context.getString(R.string.adaptive_today, it) },
                    ).joinToString(" · "),
                )
            }

            AdaptiveVolumeState.WAITING_FOR_MUSIC -> {
                Content(
                    context.getString(R.string.adaptive_waiting_title),
                    listOfNotNull(
                        context.getString(R.string.adaptive_microphone_off),
                        today?.let { context.getString(R.string.adaptive_today, it) },
                    ).joinToString(" · "),
                )
            }

            else -> {
                Content(context.getString(R.string.adaptive_paused_title), context.getString(R.string.adaptive_microphone_off))
            }
        }
    }

    fun build(
        context: Context,
        status: AdaptiveVolumeStatus,
    ): Notification {
        ensureChannel(context)
        val content = content(context, status)
        val paused = status.state == AdaptiveVolumeState.PAUSED
        val builder = Notification.Builder(context, CHANNEL_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Show it at once: the user just tapped Start and should see the microphone is in use.
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).addAction(
                if (paused) {
                    action(context, AdaptiveVolumeService.ACTION_RESUME, R.string.adaptive_resume)
                } else {
                    action(context, AdaptiveVolumeService.ACTION_PAUSE, R.string.adaptive_pause)
                },
            ).addAction(action(context, AdaptiveVolumeService.ACTION_STOP, R.string.adaptive_stop))
            .build()
    }

    private fun action(
        context: Context,
        serviceAction: String,
        label: Int,
    ): Notification.Action =
        Notification.Action
            .Builder(
                null,
                context.getString(label),
                PendingIntent.getService(
                    context,
                    serviceAction.hashCode(),
                    AdaptiveVolumeService.intent(context, serviceAction),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).build()

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            // Low importance: no sound and no heads-up for a status that changes every second.
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.adaptive_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
