package com.waveheaven.lambda.core.data

import com.waveheaven.lambda.core.domain.effects.EffectsMode
import com.waveheaven.lambda.core.domain.effects.EqPreset
import com.waveheaven.lambda.core.domain.effects.ReverbPreset
import com.waveheaven.lambda.core.domain.effects.SoundEffectsSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlSoundEffectsStoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val driver = testDriver()
    private val database = createDatabase(driver)
    private val store = SqlSoundEffectsStore(database, dispatcher)

    @AfterTest
    fun closeDriver() = driver.close()

    @Test
    fun `starts with every effect off`() =
        test {
            assertEquals(SoundEffectsSettings(), store.settings.first())
        }

    @Test
    fun `stores and reads back every field`() =
        test {
            val settings =
                SoundEffectsSettings(
                    enabled = true,
                    preset = EqPreset.CUSTOM,
                    customGainsDb = listOf(1.5, 2.0, -3.0, 0.0, 12.0, -12.0, 0.5, 0.0, 4.0, -1.0),
                    bassBoostDb = 7.0,
                    extraVolumeDb = 2.5,
                    reverb = ReverbPreset.PLATE,
                    surround = 0.6,
                    mode = EffectsMode.GLOBAL,
                )

            store.save(settings)

            assertEquals(settings, store.settings.first())
            assertEquals(settings, SqlSoundEffectsStore(database, dispatcher).settings.first())
        }

    @Test
    fun `invalid or unknown fields fall back to their defaults without losing the rest`() {
        val decoded =
            SqlSoundEffectsStore.decode(
                "enabled=yes;preset=DUBSTEP;custom=1,2,3;bass=99;extra=3.0;reverb=LARGE_HALL;surround=x;mode=GLOBAL;future=1;junk",
            )

        assertEquals(
            SoundEffectsSettings(extraVolumeDb = 3.0, reverb = ReverbPreset.LARGE_HALL, mode = EffectsMode.GLOBAL),
            decoded,
        )
        assertEquals(SoundEffectsSettings(), SqlSoundEffectsStore.decode(""))
        assertEquals(SoundEffectsSettings(), SqlSoundEffectsStore.decode("custom=1,2,3,4,5,6,7,8,9,x"))
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher) { block() }
}
