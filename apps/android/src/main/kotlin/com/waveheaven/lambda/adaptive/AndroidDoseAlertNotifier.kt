package com.waveheaven.lambda.adaptive

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.waveheaven.lambda.MainActivity
import com.waveheaven.lambda.R
import com.waveheaven.lambda.core.domain.alert.DoseAdvice
import com.waveheaven.lambda.core.domain.alert.DoseAlert
import com.waveheaven.lambda.core.domain.alert.DoseAlertNotifier
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import kotlin.math.roundToInt

/**
 * Dose alerts as Android notifications (#16): a heads-up with vibration and **no sound**, so they are visual and
 * haptic, never sound-only (WH-081), and each one says what to do (WH-082).
 */
class AndroidDoseAlertNotifier(
    private val context: Context,
) : DoseAlertNotifier {
    override fun show(alert: DoseAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        ensureChannel(manager)
        val title =
            if (alert.threshold == DoseThreshold.FULL) {
                context.getString(R.string.alert_title_full)
            } else {
                context.getString(R.string.alert_title, (alert.threshold.fraction * PERCENT).roundToInt())
            }
        val advice = context.getString(adviceText(alert.advice))
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(advice)
                .setStyle(Notification.BigTextStyle().bigText(advice))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                ).build()
        // One notification per threshold, so a later alert does not hide an earlier one the user has not read.
        manager.notify(NOTIFICATION_ID_BASE + alert.threshold.ordinal, notification)
    }

    private fun adviceText(advice: DoseAdvice): Int =
        when (advice) {
            DoseAdvice.LOWER_VOLUME -> R.string.alert_advice_lower_volume
            DoseAdvice.TAKE_A_BREAK -> R.string.alert_advice_break
            DoseAdvice.USE_NOISE_CANCELLING -> R.string.alert_advice_noise_cancelling
        }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(CHANNEL_ID, context.getString(R.string.alert_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.alert_channel_description)
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
            }
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "dose_alerts"
        const val NOTIFICATION_ID_BASE = 100
        const val PERCENT = 100

        /** Two short pulses: noticeable without being alarming. */
        val VIBRATION_PATTERN = longArrayOf(0, 250, 150, 250)
    }
}
