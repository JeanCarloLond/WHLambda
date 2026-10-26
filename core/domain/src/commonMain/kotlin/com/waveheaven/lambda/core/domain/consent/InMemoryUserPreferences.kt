package com.waveheaven.lambda.core.domain.consent

import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.DoseWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant

/** [UserPreferences] kept in memory: for tests and previews. */
public class InMemoryUserPreferences(
    onboardingCompleted: Boolean = false,
    weeklyStandard: DoseStandard = DoseStandard.WHO_ADULT,
    consent: Consent? = null,
) : UserPreferences {
    private val completed = MutableStateFlow(onboardingCompleted)
    private val standard = MutableStateFlow(weeklyStandard)
    private val given = MutableStateFlow(consent)

    override val onboardingCompleted: Flow<Boolean> = completed
    override val weeklyStandard: Flow<DoseStandard> = standard
    override val consent: Flow<Consent?> = given

    override suspend fun completeOnboarding() {
        completed.value = true
    }

    override suspend fun setWeeklyStandard(standard: DoseStandard) {
        require(standard.window == DoseWindow.WEEKLY) { "$standard is not a weekly standard" }
        this.standard.value = standard
    }

    override suspend fun giveConsent(
        version: Int,
        at: Instant,
    ) {
        given.value = Consent(version, at)
    }

    override suspend fun withdrawConsent() {
        given.value = null
    }
}
