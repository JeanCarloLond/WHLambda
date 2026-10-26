package com.waveheaven.lambda.core.domain.consent

import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** Port: choices the user makes during onboarding and in settings, stored on the device. */
public interface UserPreferences {
    /** Whether the user went through onboarding (accepting or not). */
    public val onboardingCompleted: Flow<Boolean>

    /** WHO adult by default (WH-075). */
    public val weeklyStandard: Flow<DoseStandard>

    /** `null` when never given or withdrawn. */
    public val consent: Flow<Consent?>

    public suspend fun completeOnboarding()

    /** @throws IllegalArgumentException if [standard] is not a weekly standard. */
    public suspend fun setWeeklyStandard(standard: DoseStandard)

    public suspend fun giveConsent(
        version: Int,
        at: Instant,
    )

    /** Withdraws consent (WH-073); metering must stop. */
    public suspend fun withdrawConsent()
}
