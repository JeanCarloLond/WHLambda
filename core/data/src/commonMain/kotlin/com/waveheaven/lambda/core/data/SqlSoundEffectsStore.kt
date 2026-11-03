package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.data.db.WhDatabase
import com.waveheaven.lambda.core.domain.effects.EqBands
import com.waveheaven.lambda.core.domain.effects.SoundEffectsSettings
import com.waveheaven.lambda.core.domain.effects.SoundEffectsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/**
 * [SoundEffectsStore] in one key-value setting, as `name=value` fields separated by `;`. Reading is lenient: a
 * missing or invalid field (from an older or newer version) takes its default instead of losing the rest.
 */
class SqlSoundEffectsStore(
    database: WhDatabase,
    ioContext: CoroutineContext = Dispatchers.IO,
) : SoundEffectsStore {
    private val storage = SqlSettings(database, ioContext)

    override val settings: Flow<SoundEffectsSettings> =
        storage
            .observe(KEY)
            .map { it?.let(::decode) ?: SoundEffectsSettings() }
            .distinctUntilChanged()

    override suspend fun save(settings: SoundEffectsSettings) = storage.put(KEY, encode(settings))

    internal companion object {
        const val KEY = "sound_effects"

        fun encode(settings: SoundEffectsSettings): String =
            listOf(
                "enabled" to settings.enabled.toString(),
                "preset" to settings.preset.name,
                "custom" to settings.customGainsDb.joinToString(","),
                "bass" to settings.bassBoostDb.toString(),
                "extra" to settings.extraVolumeDb.toString(),
                "reverb" to settings.reverb.name,
                "surround" to settings.surround.toString(),
                "mode" to settings.mode.name,
            ).joinToString(";") { (name, value) -> "$name=$value" }

        fun decode(encoded: String): SoundEffectsSettings {
            val fields =
                encoded
                    .split(';')
                    .mapNotNull { field -> field.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                    .toMap()
            val defaults = SoundEffectsSettings()
            return SoundEffectsSettings(
                enabled = fields["enabled"]?.toBooleanStrictOrNull() ?: defaults.enabled,
                preset = fields["preset"].toEnum(defaults.preset),
                customGainsDb = fields["custom"]?.let(::decodeGains) ?: defaults.customGainsDb,
                bassBoostDb = fields["bass"].toDoubleIn(SoundEffectsSettings.BASS_BOOST_RANGE_DB, defaults.bassBoostDb),
                extraVolumeDb = fields["extra"].toDoubleIn(SoundEffectsSettings.EXTRA_VOLUME_RANGE_DB, defaults.extraVolumeDb),
                reverb = fields["reverb"].toEnum(defaults.reverb),
                surround = fields["surround"].toDoubleIn(0.0..1.0, defaults.surround),
                mode = fields["mode"].toEnum(defaults.mode),
            )
        }

        private fun decodeGains(encoded: String): List<Double>? =
            encoded
                .split(',')
                .map { it.toDoubleOrNull() ?: return null }
                .takeIf { gains -> gains.size == EqBands.COUNT && gains.all { it in EqBands.GAIN_RANGE_DB } }

        private fun String?.toDoubleIn(
            range: ClosedFloatingPointRange<Double>,
            default: Double,
        ): Double = this?.toDoubleOrNull()?.takeIf { it in range } ?: default

        private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E = enumValues<E>().firstOrNull { it.name == this } ?: default
    }
}
