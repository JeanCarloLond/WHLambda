package com.waveheaven.lambda.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import com.waveheaven.lambda.core.designsystem.icon.WhIcons
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolume
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeState
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeStatus
import com.waveheaven.lambda.desktop.resources.Res
import com.waveheaven.lambda.desktop.resources.tray_open
import com.waveheaven.lambda.desktop.resources.tray_pause
import com.waveheaven.lambda.desktop.resources.tray_paused
import com.waveheaven.lambda.desktop.resources.tray_quit
import com.waveheaven.lambda.desktop.resources.tray_resume
import com.waveheaven.lambda.desktop.resources.tray_running
import com.waveheaven.lambda.desktop.resources.tray_start
import com.waveheaven.lambda.desktop.resources.tray_stop
import com.waveheaven.lambda.desktop.resources.tray_stopped
import com.waveheaven.lambda.desktop.resources.tray_today
import com.waveheaven.lambda.desktop.resources.tray_waiting
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The system-tray icon (#17, WH-201): metering state and today's dose in the tooltip and the menu, and pause, resume,
 * stop, open and quit. Metering is always visible while it runs (ADR-0007).
 */
@Composable
internal fun ApplicationScope.WaveHeavenTray(
    session: AdaptiveVolume,
    state: TrayState,
    onOpen: () -> Unit,
    onQuit: () -> Unit,
) {
    val status by session.status.collectAsState()
    val summary = summary(status)
    Tray(
        icon = rememberVectorPainter(WhIcons.AppMark),
        state = state,
        tooltip = "WaveHeaven Lambda · $summary",
        onAction = onOpen,
        menu = {
            Item(summary, enabled = false, onClick = {})
            when (status.state) {
                AdaptiveVolumeState.RUNNING, AdaptiveVolumeState.WAITING_FOR_MUSIC -> {
                    Item(stringResource(Res.string.tray_pause), onClick = session::pause)
                    Item(stringResource(Res.string.tray_stop), onClick = session::stop)
                }

                AdaptiveVolumeState.PAUSED -> {
                    Item(stringResource(Res.string.tray_resume), onClick = session::resume)
                    Item(stringResource(Res.string.tray_stop), onClick = session::stop)
                }

                AdaptiveVolumeState.STOPPED, AdaptiveVolumeState.UNAVAILABLE -> {
                    Item(stringResource(Res.string.tray_start), onClick = session::start)
                }
            }
            Separator()
            Item(stringResource(Res.string.tray_open), onClick = onOpen)
            Item(stringResource(Res.string.tray_quit), onClick = onQuit)
        },
    )
}

@Composable
private fun summary(status: AdaptiveVolumeStatus): String {
    val state =
        when (status.state) {
            AdaptiveVolumeState.RUNNING -> stringResource(Res.string.tray_running, status.ambient?.decibels?.roundToInt() ?: 0)
            AdaptiveVolumeState.WAITING_FOR_MUSIC -> stringResource(Res.string.tray_waiting)
            AdaptiveVolumeState.PAUSED -> stringResource(Res.string.tray_paused)
            AdaptiveVolumeState.STOPPED, AdaptiveVolumeState.UNAVAILABLE -> stringResource(Res.string.tray_stopped)
        }
    val today = status.dailyDose?.let { stringResource(Res.string.tray_today, (it.fraction * PERCENT).roundToInt()) }
    return listOfNotNull(state, today).joinToString(" · ")
}

private const val PERCENT = 100
