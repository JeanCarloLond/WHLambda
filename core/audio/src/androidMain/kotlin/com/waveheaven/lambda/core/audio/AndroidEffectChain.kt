package com.waveheaven.lambda.core.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.waveheaven.lambda.core.domain.effects.EqBands
import com.waveheaven.lambda.core.domain.effects.ResolvedEffects
import com.waveheaven.lambda.core.domain.effects.ReverbPreset
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Android effects on one session (ADR-0010): `DynamicsProcessing` for the equaliser, bass boost, extra volume and
 * the limiter after them; `PresetReverb`; and `Virtualizer`, which phones only apply on headphones. The system
 * `BassBoost` is not used: on the emulator it did nothing on the speaker, while the equaliser's low shelf works on
 * every output.
 *
 * `Virtualizer` is deprecated since Android 16 in favour of `Spatializer`, which only the user can turn on, for every
 * app at once, in the system settings. It is still the only surround effect an app can put on another app's session
 * and still works where the phone ships it, so it is used, optionally, behind [AndroidEffectsSupport.surroundSupported].
 */
@Suppress("DEPRECATION") // Virtualizer: see above.
internal class AndroidEffectChain private constructor(
    private val dynamics: DynamicsProcessing,
    private val reverb: PresetReverb?,
    private val surround: Virtualizer?,
) : EffectChain {
    override val hasControl: Boolean
        get() = runCatching { dynamics.hasControl() }.getOrDefault(false)

    override fun apply(effects: ResolvedEffects) {
        // An effect can be taken over or released by the system at any moment; the engine retries on the next change.
        runCatching {
            dynamics.setPreEqAllChannelsTo(equaliser(effects.bandGainsDb))
            dynamics.setInputGainAllChannelsTo(effects.inputGainDb.toFloat())
            dynamics.setEnabled(true)
        }
        reverb?.let { effect ->
            runCatching {
                val preset = PRESETS[effects.reverb]
                if (preset != null) effect.preset = preset
                effect.setEnabled(preset != null)
            }
        }
        surround?.let { effect ->
            runCatching {
                if (effects.surround > 0.0) effect.setStrength((effects.surround * MAX_STRENGTH).roundToInt().toShort())
                effect.setEnabled(effects.surround > 0.0)
            }
        }
    }

    override fun release() {
        runCatching { dynamics.release() }
        runCatching { reverb?.release() }
        runCatching { surround?.release() }
    }

    companion object {
        /** Slightly above the default, so a system-wide preset does not win, while an equaliser app that asks for more does. */
        private const val PRIORITY = 1
        private const val CHANNELS = 2
        private const val MAX_STRENGTH = 1000
        private const val TOP_HZ = 20_000f

        // Keeps peaks under full scale whatever the boosts: fast attack, gentle release, no make-up gain.
        private const val LIMITER_ATTACK_MS = 1f
        private const val LIMITER_RELEASE_MS = 60f
        private const val LIMITER_RATIO = 10f
        private const val LIMITER_THRESHOLD_DB = -1f

        private val PRESETS =
            mapOf(
                ReverbPreset.SMALL_ROOM to PresetReverb.PRESET_SMALLROOM,
                ReverbPreset.MEDIUM_ROOM to PresetReverb.PRESET_MEDIUMROOM,
                ReverbPreset.LARGE_ROOM to PresetReverb.PRESET_LARGEROOM,
                ReverbPreset.MEDIUM_HALL to PresetReverb.PRESET_MEDIUMHALL,
                ReverbPreset.LARGE_HALL to PresetReverb.PRESET_LARGEHALL,
                ReverbPreset.PLATE to PresetReverb.PRESET_PLATE,
            )

        /** Upper edge of each band: halfway, on a log scale, to the next centre. */
        private val CUTOFFS_HZ =
            EqBands.CENTERS_HZ.zipWithNext { a, b -> sqrt(a * b).toFloat() } + TOP_HZ

        /**
         * A chain on [session], or `null` when the phone refuses `DynamicsProcessing` there. Reverb and surround are
         * optional: a phone without them still gets the equaliser.
         */
        fun create(
            session: Int,
            surroundSupported: Boolean,
            onControlChanged: () -> Unit,
        ): EffectChain? {
            val dynamics =
                try {
                    DynamicsProcessing(PRIORITY, session, config())
                } catch (_: RuntimeException) {
                    return null
                }
            val listener = AudioEffect.OnControlStatusChangeListener { _, _ -> onControlChanged() }
            dynamics.setControlStatusListener(listener)
            val reverb = runCatching { PresetReverb(PRIORITY, session) }.getOrNull()
            val surround = if (surroundSupported) runCatching { Virtualizer(PRIORITY, session) }.getOrNull() else null
            return AndroidEffectChain(dynamics, reverb, surround)
        }

        private fun config(): DynamicsProcessing.Config {
            val config =
                DynamicsProcessing.Config
                    .Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        CHANNELS,
                        true,
                        EqBands.COUNT,
                        false,
                        0,
                        false,
                        0,
                        true,
                    ).build()
            config.setPreEqAllChannelsTo(equaliser(EqBands.FLAT))
            config.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    LIMITER_ATTACK_MS,
                    LIMITER_RELEASE_MS,
                    LIMITER_RATIO,
                    LIMITER_THRESHOLD_DB,
                    0f,
                ),
            )
            return config
        }

        private fun equaliser(gainsDb: List<Double>): DynamicsProcessing.Eq {
            val eq = DynamicsProcessing.Eq(true, true, EqBands.COUNT)
            gainsDb.forEachIndexed { band, gain -> eq.setBand(band, DynamicsProcessing.EqBand(true, CUTOFFS_HZ[band], gain.toFloat())) }
            return eq
        }
    }
}

/** [EffectChainFactory] over [AndroidEffectChain]. Create chains on the main thread, so control callbacks have a looper. */
class AndroidEffectChainFactory(
    private val surroundSupported: Boolean,
) : EffectChainFactory {
    override fun create(
        session: Int,
        onControlChanged: () -> Unit,
    ): EffectChain? = AndroidEffectChain.create(session, surroundSupported, onControlChanged)
}

/** What the phone's audio effects library offers, from `AudioEffect.queryEffects()`. */
object AndroidEffectsSupport {
    private val effects by lazy { runCatching { AudioEffect.queryEffects().map { it.type } }.getOrDefault(emptyList()) }

    /** The equaliser, bass boost and extra volume all need `DynamicsProcessing`. */
    val isSupported: Boolean
        get() = AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING in effects

    val surroundSupported: Boolean
        get() = AudioEffect.EFFECT_TYPE_VIRTUALIZER in effects
}
