package com.waveheaven.lambda.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioServiceDumpTest {
    @Test
    fun `finds media players with their session in an android 16 dump`() {
        // piid 103, 119 and the event line are verbatim from `dumpsys audio` on the API 36 emulator; the others follow
        // the same format to cover paused, notification, released and duplicated players.
        val dump =
            """
            playback configurations (last 10 piids):
              (not logged)  AudioPlaybackConfiguration piid:103 deviceIds:[] type:android.media.SoundPool u/pid:1000/664 state:idle attr:AudioAttributes: usage=USAGE_ASSISTANCE_SONIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null sessionId:0 mutedState:none  FormatInfo{isSpatialized=false, channelMask=0x0, sampleRate=0}
              AudioPlaybackConfiguration piid:119 deviceIds:[2] type:android.media.AudioTrack u/pid:2000/2204 state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0xA00 tags= bundle=null sessionId:97 mutedState:none  FormatInfo{isSpatialized=false, channelMask=0x3, sampleRate=48000}
              AudioPlaybackConfiguration piid:127 deviceIds:[2] type:android.media.MediaPlayer u/pid:10187/5120 state:paused attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x800 tags= bundle=null sessionId:145 mutedState:none
              AudioPlaybackConfiguration piid:135 deviceIds:[2] type:android.media.AudioTrack u/pid:10190/5300 state:started attr:AudioAttributes: usage=USAGE_NOTIFICATION content=CONTENT_TYPE_SONIFICATION flags=0x800 tags= bundle=null sessionId:161 mutedState:none
              AudioPlaybackConfiguration piid:143 deviceIds:[2] type:android.media.AudioTrack u/pid:10191/5400 state:released attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0x800 tags= bundle=null sessionId:177 mutedState:none
              AudioPlaybackConfiguration piid:151 deviceIds:[2] type:android.media.AudioTrack u/pid:10192/5500 state:started attr:AudioAttributes: usage=USAGE_GAME content=CONTENT_TYPE_UNKNOWN flags=0x800 tags= bundle=null sessionId:185 mutedState:none
              AudioPlaybackConfiguration piid:152 deviceIds:[2] type:android.media.AudioTrack u/pid:10192/5500 state:started attr:AudioAttributes: usage=USAGE_GAME content=CONTENT_TYPE_UNKNOWN flags=0x800 tags= bundle=null sessionId:185 mutedState:none

            11-02 19:56:38:382 new player piid:119 uid/pid:2000/2204 package:com.android.shell type:android.media.AudioTrack attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0xA00 tags= bundle=null session:97
            """.trimIndent()

        assertEquals(
            listOf(DumpedPlayer(2000, 97), DumpedPlayer(10187, 145), DumpedPlayer(10192, 185)),
            AudioServiceDump.mediaPlayers(dump),
        )
    }

    @Test
    fun `reads fields in another order and separator`() {
        val line =
            "  ID:12 -- type:android.media.AudioTrack -- u/pid:10200/6000 -- state:started -- " +
                "attr:AudioAttributes: usage=USAGE_UNKNOWN content=CONTENT_TYPE_UNKNOWN -- sessionId:201"

        assertEquals(listOf(DumpedPlayer(10200, 201)), AudioServiceDump.mediaPlayers(line))
    }

    @Test
    fun `finds nothing without sessions or in other text`() {
        val noSession =
            "  ID:12 -- type:android.media.AudioTrack -- u/pid:10200/6000 -- state:started -- attr:AudioAttributes: usage=USAGE_MEDIA"

        assertEquals(emptyList(), AudioServiceDump.mediaPlayers(noSession))
        assertEquals(emptyList(), AudioServiceDump.mediaPlayers(""))
        assertEquals(emptyList(), AudioServiceDump.mediaPlayers("Permission Denial: can't dump audio from pid=1, uid=10200"))
    }

    @Test
    fun `a dump lists sessions when its players name them or when nothing plays`() {
        val withSession =
            "AudioPlaybackConfiguration piid:119 u/pid:2000/2204 state:started attr:AudioAttributes: usage=USAGE_MEDIA sessionId:97"
        val withoutSession =
            "  ID:12 -- type:android.media.AudioTrack -- u/pid:10200/6000 -- state:started -- attr:AudioAttributes: usage=USAGE_MEDIA"

        assertTrue(AudioServiceDump.listsSessions(withSession))
        assertTrue(AudioServiceDump.listsSessions("playback configurations (last 10 piids):\n"))
        assertFalse(AudioServiceDump.listsSessions(withoutSession))
    }
}
