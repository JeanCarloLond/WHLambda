package com.waveheaven.lambda.core.domain.alert

import com.waveheaven.lambda.core.domain.exposure.Dose
import com.waveheaven.lambda.core.domain.exposure.DoseStandard
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import kotlinx.coroutines.flow.Flow

/** The points of the active window's budget at which the user is told (WH-080). */
public enum class DoseThreshold(
    public val fraction: Double,
) {
    HALF(0.5),
    HIGH(0.8),
    FULL(1.0),
}

/** What an alert tells the user to do (WH-082). */
public enum class DoseAdvice {
    LOWER_VOLUME,
    TAKE_A_BREAK,
    USE_NOISE_CANCELLING,
}

/** Which thresholds the user wants to hear about; none means muted (WH-083). */
public data class DoseAlertSettings(
    val enabled: Set<DoseThreshold> = DoseThreshold.entries.toSet(),
) {
    val isMuted: Boolean
        get() = enabled.isEmpty()
}

/** One alert to show. */
public data class DoseAlert(
    val threshold: DoseThreshold,
    val standard: DoseStandard,
    val dose: Dose,
    val advice: DoseAdvice,
)

/**
 * Decides when to alert, without nagging (WH-080, WH-082):
 *
 * - Each threshold alerts **once per window**. The weekly window rolls, so a threshold re-arms only after the dose has
 *   fallen [REARM_MARGIN] below it again, which happens as old listening leaves the 7 days.
 * - When the dose jumps past several thresholds at once, only the highest one alerts.
 * - Disabled thresholds never alert, but are still marked as passed, so turning them back on does not fire late.
 */
public object DoseAlertPolicy {
    /** How far below a threshold the dose must fall before that threshold can alert again. */
    public const val REARM_MARGIN: Double = 0.05

    /** Above this ambient level, noise-cancelling helps more than a break: the noise is what pushes the volume. */
    public val NOISY: SoundLevel = SoundLevel(NOISY_DBA)

    /**
     * @param passed thresholds already alerted (or skipped) in the current window.
     * @return the alert to show, if any, and the new set of passed thresholds to store.
     */
    public fun evaluate(
        standard: DoseStandard,
        dose: Dose,
        ambient: SoundLevel?,
        passed: Set<DoseThreshold>,
        settings: DoseAlertSettings,
    ): Evaluation {
        val stillPassed = passed.filterTo(mutableSetOf()) { dose.fraction > it.fraction - REARM_MARGIN }
        val crossed = DoseThreshold.entries.filter { dose.fraction >= it.fraction && it !in stillPassed }
        val alert =
            crossed
                .maxByOrNull { it.fraction }
                ?.takeIf { it in settings.enabled }
                ?.let { DoseAlert(it, standard, dose, adviceFor(it, ambient)) }
        return Evaluation(alert, stillPassed + crossed)
    }

    /** At half the budget a lower volume is enough; later, a break or, in noise, noise-cancelling. */
    public fun adviceFor(
        threshold: DoseThreshold,
        ambient: SoundLevel?,
    ): DoseAdvice =
        when {
            threshold == DoseThreshold.HALF -> DoseAdvice.LOWER_VOLUME
            ambient != null && ambient >= NOISY -> DoseAdvice.USE_NOISE_CANCELLING
            else -> DoseAdvice.TAKE_A_BREAK
        }

    public data class Evaluation(
        val alert: DoseAlert?,
        val passed: Set<DoseThreshold>,
    )
}

private const val NOISY_DBA = 75.0

/** Port: the user's alert settings and which thresholds already alerted, stored on the device. */
public interface DoseAlertStore {
    public val settings: Flow<DoseAlertSettings>

    public suspend fun setSettings(settings: DoseAlertSettings)

    public suspend fun passedThresholds(): Set<DoseThreshold>

    public suspend fun setPassedThresholds(passed: Set<DoseThreshold>)
}

/** Port: shows an alert, visually and haptically, never by sound alone (WH-081). */
public fun interface DoseAlertNotifier {
    public fun show(alert: DoseAlert)
}
