package com.waveheaven.lambda.core.domain.effects

import com.waveheaven.lambda.core.domain.profile.EqCurve
import com.waveheaven.lambda.core.domain.profile.EqPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundEffectsSettingsTest {
    @Test
    fun `every preset has a gain per band within the user range`() {
        EqPreset.entries.filter { it != EqPreset.CUSTOM }.forEach { preset ->
            val gains = preset.gainsDb!!
            assertEquals(EqBands.COUNT, gains.size, "$preset")
            assertTrue(gains.all { it in EqBands.GAIN_RANGE_DB }, "$preset")
        }
        assertNull(EqPreset.CUSTOM.gainsDb)
    }

    @Test
    fun `defaults change nothing`() {
        val resolved = SoundEffectsSettings().resolve(profileEq = null)

        assertEquals(EqBands.FLAT, resolved.bandGainsDb)
        assertEquals(0.0, resolved.inputGainDb)
        assertEquals(ReverbPreset.NONE, resolved.reverb)
        assertEquals(0.0, resolved.maxBoostDb)
        assertFalse(resolved.raisesLevel)
        assertFalse(resolved.usesProfileEq)
    }

    @Test
    fun `a preset gives its curve and custom gives the user's`() {
        assertEquals(EqPreset.ROCK.gainsDb, SoundEffectsSettings(preset = EqPreset.ROCK).resolve(null).bandGainsDb)

        val mine = listOf(1.0, 2.0, 3.0, 4.0, 5.0, -5.0, -4.0, -3.0, -2.0, -1.0)
        assertEquals(mine, SoundEffectsSettings(preset = EqPreset.CUSTOM, customGainsDb = mine).resolve(null).bandGainsDb)
        // The custom curve is kept while a preset is selected, so going back to custom restores it.
        assertEquals(EqPreset.POP.gainsDb, SoundEffectsSettings(preset = EqPreset.POP, customGainsDb = mine).curveDb)
    }

    @Test
    fun `bass boost is a low shelf, full in the sub-bass and half at the corner`() {
        val bands = SoundEffectsSettings(bassBoostDb = 10.0).resolve(null).bandGainsDb

        assertEquals(10.0 / (1 + (31.0 / 120).let { it * it }), bands[0], 1e-9)
        assertTrue(bands[0] > 9.0)
        assertEquals(5.0, 10.0 * SoundEffectsSettings.bassShelfWeight(SoundEffectsSettings.BASS_SHELF_HZ), 1e-9)
        assertTrue(bands.zipWithNext().all { (a, b) -> b <= a }, "falls with frequency")
        assertTrue(bands[EqBands.COUNT - 1] < 0.01, "treble untouched")
    }

    @Test
    fun `a profile curve replaces the global one and bass boost still adds`() {
        val profile = EqCurve(listOf(EqPoint(1_000.0, 0.0), EqPoint(4_000.0, 8.0)))
        val settings = SoundEffectsSettings(preset = EqPreset.ROCK, bassBoostDb = 6.0)

        val resolved = settings.resolve(profile)

        assertTrue(resolved.usesProfileEq)
        assertEquals(8.0, resolved.bandGainsDb[7], 0.01) // 4 kHz from the profile, not rock's 3 dB
        assertEquals(EqBands.sample(profile)[0] + 6.0 * SoundEffectsSettings.bassShelfWeight(31.0), resolved.bandGainsDb[0], 1e-9)
    }

    @Test
    fun `stacked boosts are clamped per band`() {
        val loud = EqCurve(listOf(EqPoint(20.0, 24.0), EqPoint(20_000.0, -24.0)))

        val bands = SoundEffectsSettings(bassBoostDb = 12.0).resolve(loud).bandGainsDb

        assertEquals(SoundEffectsSettings.MAX_BAND_GAIN_DB, bands[0])
        assertEquals(-SoundEffectsSettings.MAX_BAND_GAIN_DB, bands.last())
    }

    @Test
    fun `boosting by three decibels or more raises the level at the ear`() {
        assertFalse(SoundEffectsSettings(preset = EqPreset.CUSTOM, customGainsDb = List(10) { 2.0 }).resolve(null).raisesLevel)
        assertFalse(SoundEffectsSettings(preset = EqPreset.CUSTOM, customGainsDb = List(10) { -6.0 }).resolve(null).raisesLevel)
        assertTrue(SoundEffectsSettings(preset = EqPreset.BASS).resolve(null).raisesLevel)
        assertTrue(SoundEffectsSettings(extraVolumeDb = 3.0).resolve(null).raisesLevel)

        val both = SoundEffectsSettings(preset = EqPreset.BASS, extraVolumeDb = 4.0).resolve(null)
        assertEquals(10.0, both.maxBoostDb, 1e-9) // 6 dB of the bass preset plus 4 dB before the limiter
    }

    @Test
    fun `out of range settings are rejected`() {
        assertFailsWith<IllegalArgumentException> { SoundEffectsSettings(customGainsDb = List(9) { 0.0 }) }
        assertFailsWith<IllegalArgumentException> { SoundEffectsSettings(customGainsDb = List(10) { 13.0 }) }
        assertFailsWith<IllegalArgumentException> { SoundEffectsSettings(bassBoostDb = -1.0) }
        assertFailsWith<IllegalArgumentException> { SoundEffectsSettings(bassBoostDb = 13.0) }
        assertFailsWith<IllegalArgumentException> { SoundEffectsSettings(extraVolumeDb = 11.0) }
        assertFailsWith<IllegalArgumentException> { SoundEffectsSettings(surround = 1.5) }
    }

    @Test
    fun `the in-memory store keeps the last settings`() =
        runTest {
            val store = InMemorySoundEffectsStore()
            assertEquals(SoundEffectsSettings(), store.settings.first())

            store.save(SoundEffectsSettings(enabled = true, reverb = ReverbPreset.LARGE_HALL))

            assertEquals(ReverbPreset.LARGE_HALL, store.settings.first().reverb)
        }

    @Test
    fun `the unsupported platform does nothing and the fake records calls`() {
        val unsupported = UnsupportedSoundEffects()
        unsupported.start()
        unsupported.refresh()
        unsupported.stop()
        assertFalse(unsupported.isSupported)
        assertFalse(unsupported.status.value.running)

        val fake = FakeSoundEffects(SoundEffectsStatus(apps = listOf("Spotify")))
        fake.start()
        assertTrue(fake.status.value.running)
        fake.emit(fake.status.value.copy(fullDetection = true))
        fake.refresh()
        fake.stop()
        assertEquals(listOf("start", "refresh", "stop"), fake.calls)
        assertEquals(SoundEffectsStatus(fullDetection = true), fake.status.value)
    }
}
