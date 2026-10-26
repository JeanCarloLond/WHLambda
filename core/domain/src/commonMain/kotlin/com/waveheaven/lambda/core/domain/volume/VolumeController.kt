package com.waveheaven.lambda.core.domain.volume

import com.waveheaven.lambda.core.domain.exposure.Dose
import com.waveheaven.lambda.core.domain.exposure.ExposureSegment
import com.waveheaven.lambda.core.domain.exposure.SoundLevel
import com.waveheaven.lambda.core.domain.exposure.equivalentLevel
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.AT_DOSE_CEILING
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.LOWER_FOR_QUIET
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.LOWER_TO_DOSE_CEILING
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.LOWER_TO_PROFILE_LIMIT
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.MANUAL_OVERRIDE
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.MUTED
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.NO_MUSIC
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.RAISE_FOR_NOISE
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.RATE_LIMITED
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.SETTLING
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.WARMING_UP
import com.waveheaven.lambda.core.domain.volume.VolumeDecisionReason.WITHIN_DEAD_BAND
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Adaptive volume policy (ADR-0006): keep the music [VolumeControllerConfig.targetSnrDb] above the ambient
 * noise, but never above the level the remaining exposure dose allows.
 *
 * Rules are evaluated in strict priority order on every [update]:
 * 1. No music → hold.
 * 2. Our previous change is still settling → adopt whatever index the device reports (Bluetooth rounding).
 * 3. Someone else changed the volume → pause adjustments for [VolumeControllerConfig.manualOverrideDuration].
 * 4. Muted (below [VolumeControllerConfig.minIndex]) → hold; never unmute.
 * 5. **Safety:** above the dose ceiling → drop straight to the ceiling, ignoring rate limits.
 * 6. Above the listening profile's maximum → drop to it.
 * 7. **Comfort:** move one step towards the target when it is outside the hysteresis band, rate-limited.
 *
 * The controller works in volume steps, keeps separate state per [OutputRoute], and has no platform
 * dependencies. It is not thread-safe: call [update] from a single thread.
 */
public class VolumeController(
    private val model: OutputLevelModel,
    config: VolumeControllerConfig = VolumeControllerConfig(),
) {
    private var config: VolumeControllerConfig = validated(config)
    private var autoRange: IntRange = rangeOf(this.config)
    private val ambientReadings = ArrayDeque<SoundLevel>()
    private val routes = mutableMapOf<OutputRoute, RouteState>()

    /**
     * Switches to [config] from the next [update] (e.g. the user picked another listening profile, WH-062), keeping
     * the ambient readings and what is known about each route.
     */
    public fun reconfigure(config: VolumeControllerConfig) {
        this.config = validated(config)
        autoRange = rangeOf(this.config)
    }

    private fun validated(config: VolumeControllerConfig): VolumeControllerConfig {
        require(config.minIndex in model.indices) { "minIndex ${config.minIndex} outside ${model.indices}" }
        require(config.maxIndex == null || config.maxIndex in model.indices) { "maxIndex ${config.maxIndex} outside ${model.indices}" }
        return config
    }

    private fun rangeOf(config: VolumeControllerConfig): IntRange = config.minIndex..(config.maxIndex ?: model.indices.last)

    /** Processes one observation and returns what to do with the volume. */
    public fun update(tick: VolumeTick): VolumeDecision {
        require(tick.currentIndex in model.indices) { "Index ${tick.currentIndex} outside ${model.indices}" }
        recordAmbient(tick.ambient)
        val route = routes.getOrPut(tick.route) { RouteState() }
        val settling = route.observe(tick.currentIndex, tick.now)
        val situation = Situation(tick, ambientLeq(), ceilingLevel(tick.doseConsumed))

        val decision =
            when {
                !tick.isMusicActive -> situation.hold(NO_MUSIC)
                settling -> situation.hold(SETTLING)
                route.isOverridden(tick.now) -> situation.hold(MANUAL_OVERRIDE)
                tick.currentIndex < config.minIndex -> situation.hold(MUTED)
                tick.currentIndex > situation.ceilingIndex -> situation.change(situation.ceilingIndex, LOWER_TO_DOSE_CEILING)
                tick.currentIndex > autoRange.last -> situation.change(autoRange.last, LOWER_TO_PROFILE_LIMIT)
                situation.ambientLeq == null -> situation.hold(WARMING_UP)
                else -> comfort(situation, situation.ambientLeq, route)
            }
        decision.newIndex?.let { route.recordChange(it, tick.now) }
        return decision
    }

    private fun comfort(
        situation: Situation,
        ambientLeq: SoundLevel,
        route: RouteState,
    ): VolumeDecision {
        val current = situation.tick.currentIndex
        val target = SoundLevel(ambientLeq.decibels + config.targetSnrDb)
        val targetIndex = autoRange.firstOrNull { model.levelAt(it) >= target } ?: autoRange.last
        val capped = targetIndex > situation.ceilingIndex
        val desired = minOf(targetIndex, situation.ceilingIndex)
        val gapDb = target.decibels - model.levelAt(current).decibels

        fun step(
            index: Int,
            reason: VolumeDecisionReason,
            interval: Duration,
        ) = if (route.canChange(situation.tick.now, interval)) {
            situation.change(index, reason, target, capped)
        } else {
            situation.hold(RATE_LIMITED, target, capped)
        }

        return when {
            desired > current && gapDb >= config.hysteresisDb -> step(current + 1, RAISE_FOR_NOISE, config.raiseInterval)
            desired < current && -gapDb >= config.hysteresisDb -> step(current - 1, LOWER_FOR_QUIET, config.lowerInterval)
            capped -> situation.hold(AT_DOSE_CEILING, target, capped = true)
            else -> situation.hold(WITHIN_DEAD_BAND, target)
        }
    }

    private fun recordAmbient(level: SoundLevel) {
        ambientReadings.addLast(level)
        if (ambientReadings.size > config.ambientWindowTicks) ambientReadings.removeFirst()
    }

    private fun ambientLeq(): SoundLevel? =
        if (ambientReadings.size < config.ambientWindowTicks) {
            null
        } else {
            equivalentLevel(ambientReadings.map { ExposureSegment(it, READING_DURATION) })
        }

    private fun ceilingLevel(consumed: Dose): SoundLevel {
        val sustainable =
            if (consumed.remaining > 0.0) {
                config.standard.levelSustainableFor(config.planningHorizon, consumed.remaining)
            } else {
                config.minimumCeiling
            }
        return maxOf(sustainable, config.minimumCeiling)
    }

    /** Everything known about one tick, and helpers to build decisions from it. */
    private inner class Situation(
        val tick: VolumeTick,
        val ambientLeq: SoundLevel?,
        val ceilingLevel: SoundLevel,
    ) {
        /** Highest index the dose allows, regardless of the profile's maximum. */
        val ceilingIndex: Int = (config.minIndex..model.indices.last).lastOrNull { model.levelAt(it) <= ceilingLevel } ?: config.minIndex
        private val isAboveCeiling = model.levelAt(tick.currentIndex) > ceilingLevel

        fun hold(
            reason: VolumeDecisionReason,
            target: SoundLevel? = null,
            capped: Boolean = false,
        ) = decision(newIndex = null, reason, target, capped)

        fun change(
            index: Int,
            reason: VolumeDecisionReason,
            target: SoundLevel? = null,
            capped: Boolean = false,
        ) = decision(index, reason, target, capped)

        private fun decision(
            newIndex: Int?,
            reason: VolumeDecisionReason,
            target: SoundLevel?,
            capped: Boolean,
        ) = VolumeDecision(newIndex, reason, ambientLeq, target, ceilingLevel, capped, isAboveCeiling)
    }

    /** Per-route memory: what we last set, when, and whether a manual change is being respected. */
    private inner class RouteState {
        private var lastSetIndex: Int? = null
        private var lastSetAt: Duration? = null
        private var overrideUntil: Duration? = null

        /**
         * Compares the observed [index] with what we expect. Returns `true` while our own last change is
         * settling, in which case the observed index is adopted (the device may round it).
         */
        fun observe(
            index: Int,
            now: Duration,
        ): Boolean {
            val settling = lastSetAt?.let { now - it < config.settleTime } ?: false
            when {
                lastSetIndex == null || settling -> {
                    lastSetIndex = index
                }

                index != lastSetIndex -> {
                    lastSetIndex = index
                    overrideUntil = now + config.manualOverrideDuration
                }
            }
            return settling
        }

        fun isOverridden(now: Duration): Boolean = overrideUntil?.let { now < it } ?: false

        fun canChange(
            now: Duration,
            interval: Duration,
        ): Boolean = lastSetAt?.let { now - it >= interval } ?: true

        fun recordChange(
            index: Int,
            now: Duration,
        ) {
            lastSetIndex = index
            lastSetAt = now
        }
    }

    private companion object {
        val READING_DURATION = 1.seconds
    }
}
