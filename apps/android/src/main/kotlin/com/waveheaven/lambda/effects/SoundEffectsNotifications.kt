package com.waveheaven.lambda.effects

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.waveheaven.lambda.MainActivity
import com.waveheaven.lambda.R
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStatus

/** The persistent notification of [SoundEffectsService]: which apps have effects, and Turn off. */
object SoundEffectsNotifications {
    const val ID = 3
    private const val CHANNEL_ID = "sound_effects"

    fun text(
        context: Context,
        status: SoundEffectsStatus,
    ): String =
        when {
            SoundEffectsStatus.OUTPUT_MIX in status.apps -> context.getString(R.string.effects_whole_output)
            status.apps.isNotEmpty() -> status.apps.joinToString(", ")
            else -> context.getString(R.string.effects_waiting)
        }

    fun build(
        context: Context,
        status: SoundEffectsStatus,
    ): Notification {
        ensureChannel(context)
        return Notification
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.effects_title))
            .setContentText(text(context, status))
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
                Notification.Action
                    .Builder(
                        null,
                        context.getString(R.string.effects_turn_off),
                        PendingIntent.getService(
                            context,
                            SoundEffectsService.ACTION_TURN_OFF.hashCode(),
                            SoundEffectsService.intent(context, SoundEffectsService.ACTION_TURN_OFF),
                            PendingIntent.FLAG_IMMUTABLE,
                        ),
                    ).build(),
            ).build()
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            // Low importance: a quiet, persistent status.
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.effects_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
