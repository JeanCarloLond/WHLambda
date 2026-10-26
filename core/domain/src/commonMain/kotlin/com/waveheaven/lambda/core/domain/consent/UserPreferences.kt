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

    /**
     * The user's correction to the microphone's nominal calibration, in dB, measured against a reference meter
     * (WH-004, spike #3). Zero by default; within [MICROPHONE_ADJUSTMENT_RANGE_DB].
     */
    public val microphoneAdjustmentDb: Flow<Double>

    /** On desktop, the application whose volume is adapted (#17); `null` until chosen. */
    public val mediaApp: Flow<String?>

    public suspend fun completeOnboarding()

    /** @throws IllegalArgumentException if [standard] is not a weekly standard. */
    public suspend fun setWeeklyStandard(standard: DoseStandard)

    public suspend fun giveConsent(
        version: Int,
        at: Instant,
    )

    /** Withdraws consent (WH-073); metering must stop. */
    public suspend fun withdrawConsent()

    /** @throws IllegalArgumentException outside [MICROPHONE_ADJUSTMENT_RANGE_DB]. */
    public suspend fun setMicrophoneAdjustmentDb(adjustmentDb: Double)

    public suspend fun setMediaApp(app: String?)

    public companion object {
        /** Larger corrections point to a wrong setup rather than a bad microphone (spike #3). */
        public val MICROPHONE_ADJUSTMENT_RANGE_DB: ClosedFloatingPointRange<Double> = -40.0..40.0
    }
}
