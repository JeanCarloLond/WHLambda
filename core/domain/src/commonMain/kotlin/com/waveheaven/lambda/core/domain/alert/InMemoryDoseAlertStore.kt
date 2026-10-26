package com.waveheaven.lambda.core.domain.alert

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** [DoseAlertStore] kept in memory: for tests and previews. */
public class InMemoryDoseAlertStore(
    settings: DoseAlertSettings = DoseAlertSettings(),
) : DoseAlertStore {
    private val current = MutableStateFlow(settings)
    private var passed: Set<DoseThreshold> = emptySet()

    override val settings: Flow<DoseAlertSettings> = current

    override suspend fun setSettings(settings: DoseAlertSettings) {
        current.value = settings
    }

    override suspend fun passedThresholds(): Set<DoseThreshold> = passed

    override suspend fun setPassedThresholds(passed: Set<DoseThreshold>) {
        this.passed = passed
    }
}
