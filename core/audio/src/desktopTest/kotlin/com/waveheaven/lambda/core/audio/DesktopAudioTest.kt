package com.waveheaven.lambda.core.audio

import com.waveheaven.lambda.core.domain.volume.HeadphoneProfile
import com.waveheaven.lambda.core.domain.volume.OutputRoute
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAudioTest {
    @Test
    fun `parses pactl sink inputs, cubic volume included`() {
        val streams = PulseSessions.parseSinkInputs(SINK_INPUTS)

        assertEquals(listOf("Spotify", "Firefox"), streams.map { it.app })
        assertEquals("113", streams[0].id)
        assertEquals(0.125, streams[0].linearVolume, 1e-6) // raw 32768 = 50 % in pactl = cbrt(0.125)
        assertTrue(streams[0].isPlaying)
        assertFalse(streams[1].isPlaying) // corked
    }

    @Test
    fun `pulse raw values round-trip linear amplitude`() {
        listOf(1.0, 0.5, 0.25, 0.01).forEach { linear ->
            assertEquals(linear, PulseSessions.fromRaw(PulseSessions.toRaw(linear)), 1e-4)
        }
        assertEquals(65536, PulseSessions.toRaw(1.0))
    }

    @Test
    fun `steps are 2 dB apart, with silence at 0`() {
        assertEquals(1.0, DesktopMediaVolume.linearOf(24), 1e-9)
        assertEquals(-2.0, 20 * log10(DesktopMediaVolume.linearOf(23)), 1e-9)
        assertEquals(-46.0, 20 * log10(DesktopMediaVolume.linearOf(1)), 1e-9)
        assertEquals(0.0, DesktopMediaVolume.linearOf(0))
        for (index in 0..24) assertEquals(index, DesktopMediaVolume.indexOf(DesktopMediaVolume.linearOf(index)))
        assertEquals(23, DesktopMediaVolume.indexOf(0.75)) // a value the user set (−2.5 dB) rounds to the nearest step
    }

    @Test
    fun `adapts only the chosen app, all of its streams, by name`() {
        val sessions = FakeSessions()
        val volume = DesktopMediaVolume(sessions) { "spotify" }

        volume.setIndex(21)

        assertEquals(setOf("113", "114"), sessions.set.keys)
        assertEquals(DesktopMediaVolume.linearOf(21), sessions.set.getValue("113"), 1e-9)
        assertEquals(21, volume.currentIndex())
        assertTrue(volume.isMusicActive())
        assertEquals(OutputRoute("desktop:headphones"), volume.currentRoute())
        assertEquals(listOf("Firefox", "Spotify"), volume.runningApps())
    }

    @Test
    fun `without a chosen app it needs setup and plays nothing`() {
        val volume = DesktopMediaVolume(FakeSessions()) { null }

        assertTrue(volume.needsSetup())
        assertFalse(volume.isMusicActive())
    }

    @Test
    fun `the output level adds the device volume and the app step to the profile`() {
        val sessions = FakeSessions().apply { deviceGainDb = -10.0 }
        val model = DesktopOutputLevelModels(sessions, HeadphoneProfile.UNKNOWN_HEADPHONES).modelFor(OutputRoute("desktop:x"))

        assertEquals(95.0, model.levelAt(24).decibels, 1e-9)
        assertEquals(85.0, model.levelAt(19).decibels, 1e-9)
        assertEquals(8.0, model.uncertaintyDb)
        assertTrue(model.levelAt(0) < model.levelAt(1))
    }

    @Test
    fun `chooses the built-in microphone, never a bluetooth headset or a monitor`() {
        assertEquals("alsa_input.pci-0000_00_1f.3.analog-stereo", DesktopAmbientMeter.chooseSource(SOURCES))
        assertNull(DesktopAmbientMeter.chooseSource("""[{"name":"bluez_input.3C:B0","properties":{"device.bus":"bluetooth"}}]"""))
    }

    private class FakeSessions : AppSessions {
        var deviceGainDb = 0.0
        val set = mutableMapOf<String, Double>()
        private val streams =
            listOf(
                AppStream("113", "Spotify", 1.0, isPlaying = true),
                AppStream("114", "Spotify", 1.0, isPlaying = false),
                AppStream("120", "Firefox", 0.5, isPlaying = true),
            )

        override fun streams() = streams.map { it.copy(linearVolume = set[it.id] ?: it.linearVolume) }

        override fun setVolume(
            streamId: String,
            linear: Double,
        ) {
            set[streamId] = linear
        }

        override fun outputId() = "headphones"

        override fun outputGainDb() = deviceGainDb
    }

    private companion object {
        // Trimmed from `pactl -f json list sink-inputs` on PipeWire 1.6.7 (spike #4).
        const val SINK_INPUTS =
            """[{"index":113,"corked":false,"volume":{"front-left":{"value":32768,"value_percent":"50%","db":"-18.06 dB"},
            "front-right":{"value":32768,"value_percent":"50%","db":"-18.06 dB"}},
            "properties":{"application.name":"Spotify","application.process.binary":"spotify"}},
            {"index":120,"corked":true,"volume":{"mono":{"value":65536,"value_percent":"100%","db":"0.00 dB"}},
            "properties":{"application.process.binary":"Firefox"}}]"""

        // Trimmed from `pactl -f json list sources` on the spike #4 laptop with Bluetooth headphones connected.
        const val SOURCES =
            """[{"name":"alsa_output.pci-0000_00_1f.3.analog-stereo.monitor","properties":{"device.bus":"pci","device.class":"monitor"}},
            {"name":"bluez_input.3C:B0:ED:F7:CB:32","properties":{"device.bus":"bluetooth"}},
            {"name":"alsa_input.pci-0000_00_1f.3.analog-stereo","properties":{"device.bus":"pci","device.form_factor":"internal"}}]"""
    }
}
