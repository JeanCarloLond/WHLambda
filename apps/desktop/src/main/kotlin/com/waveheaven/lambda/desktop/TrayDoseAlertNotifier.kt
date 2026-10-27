package com.waveheaven.lambda.desktop

import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import com.waveheaven.lambda.core.domain.alert.DoseAdvice
import com.waveheaven.lambda.core.domain.alert.DoseAlert
import com.waveheaven.lambda.core.domain.alert.DoseAlertNotifier
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import com.waveheaven.lambda.desktop.resources.Res
import com.waveheaven.lambda.desktop.resources.alert_advice_break
import com.waveheaven.lambda.desktop.resources.alert_advice_lower_volume
import com.waveheaven.lambda.desktop.resources.alert_advice_noise_cancelling
import com.waveheaven.lambda.desktop.resources.alert_title
import com.waveheaven.lambda.desktop.resources.alert_title_full
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import kotlin.math.roundToInt

/**
 * Dose alerts as desktop notifications through the tray (#16, #17). Computers have no vibration, so alerts are
 * visual, never sound-only (WH-081). Without a system tray there is nowhere to show them; the dashboard still does.
 */
internal class TrayDoseAlertNotifier(
    private val scope: CoroutineScope,
) : DoseAlertNotifier {
    private var tray: TrayState? = null

    fun attach(state: TrayState) {
        tray = state
    }

    override fun show(alert: DoseAlert) {
        val state = tray ?: return
        scope.launch {
            val title =
                if (alert.threshold == DoseThreshold.FULL) {
                    getString(Res.string.alert_title_full)
                } else {
                    getString(Res.string.alert_title, (alert.threshold.fraction * PERCENT).roundToInt())
                }
            val advice =
                getString(
                    when (alert.advice) {
                        DoseAdvice.LOWER_VOLUME -> Res.string.alert_advice_lower_volume
                        DoseAdvice.TAKE_A_BREAK -> Res.string.alert_advice_break
                        DoseAdvice.USE_NOISE_CANCELLING -> Res.string.alert_advice_noise_cancelling
                    },
                )
            state.sendNotification(Notification(title, advice, Notification.Type.Warning))
        }
    }

    private companion object {
        const val PERCENT = 100
    }
}
