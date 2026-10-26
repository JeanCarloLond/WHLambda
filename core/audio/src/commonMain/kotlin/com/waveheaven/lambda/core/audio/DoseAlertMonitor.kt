package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.alert.DoseAlertNotifier
import com.waveheaven.lambda.core.domain.alert.DoseAlertPolicy
import com.waveheaven.lambda.core.domain.alert.DoseAlertStore
import com.waveheaven.lambda.core.domain.alert.DoseThreshold
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.volume.AdaptiveVolumeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Checks the weekly dose on every update of the adaptive-volume loop and shows alerts through [notifier] (#16). Which
 * thresholds have passed is kept in [store], so restarting the app never repeats an alert in the same window.
 */
class DoseAlertMonitor(
    private val store: DoseAlertStore,
    private val notifier: DoseAlertNotifier,
    private val weeklyStandard: Flow<DoseStandard>,
) {
    private var passed: Set<DoseThreshold>? = null

    suspend fun check(status: AdaptiveVolumeStatus) {
        val dose = status.weeklyDose ?: return
        val known = passed ?: store.passedThresholds()
        val evaluation = DoseAlertPolicy.evaluate(weeklyStandard.first(), dose, status.ambient, known, store.settings.first())
        evaluation.alert?.let(notifier::show)
        if (evaluation.passed != known) store.setPassedThresholds(evaluation.passed)
        passed = evaluation.passed
    }
}
