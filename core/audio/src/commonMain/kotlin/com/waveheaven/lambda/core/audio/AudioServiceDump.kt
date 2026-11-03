package com.waveheaven.lambda.core.audio

/** A player found in the audio service's dump: the app's user id and its playback session. */
data class DumpedPlayer(
    val uid: Int,
    val sessionId: Int,
)

/**
 * Reads the media players out of Android's `dumpsys audio` text (ADR-0010). Only an app holding the `DUMP`
 * permission, which the user grants once over ADB, can get this text; it lists players, never audio.
 *
 * A player line looks like this on Android 16 (one line in the dump, verified on the emulator):
 * ```
 * AudioPlaybackConfiguration piid:119 deviceIds:[2] type:android.media.AudioTrack u/pid:10123/2204 state:started
 *   attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_MUSIC flags=0xA00 tags= bundle=null sessionId:97 …
 * ```
 * The order and separators of the fields change between Android versions, so each field is matched on its own. A
 * version that prints no session yields nothing, and the app falls back to the sessions players announce. The
 * player event log in the same dump (`new player piid:… uid/pid:…`) has no state and is skipped.
 */
object AudioServiceDump {
    private val UID = Regex("""\bu/pid:(\d+)/""")
    private val STATE = Regex("""\bstate:(\w+)""")
    private val USAGE = Regex("""\busage=(\w+)""")
    private val SESSION = Regex("""\bsession(?:Id)?:(\d+)""")

    /** Usages that carry music: games and untagged players included, notifications, calls and alarms not. */
    private val MUSIC_USAGES = setOf("USAGE_MEDIA", "USAGE_GAME", "USAGE_UNKNOWN")

    /** Players that never play again; idle, started, paused and stopped ones can. */
    private const val RELEASED = "released"

    /**
     * Whether this dump can be trusted as the full list of sessions: it names a session on its player lines, or has
     * no players at all. `false` on versions that print players without sessions, where only announced sessions count.
     */
    fun listsSessions(dump: String): Boolean {
        val players = dump.lineSequence().filter { UID.containsMatchIn(it) && STATE.containsMatchIn(it) }.toList()
        return players.isEmpty() || players.any { SESSION.containsMatchIn(it) }
    }

    /** Media players with a session of their own, once each, in dump order. */
    fun mediaPlayers(dump: String): List<DumpedPlayer> =
        dump
            .lineSequence()
            .mapNotNull(::parse)
            .distinctBy { it.sessionId }
            .toList()

    private fun parse(line: String): DumpedPlayer? {
        val state = STATE.group(line)
        val playable = state != null && state != RELEASED && USAGE.group(line) in MUSIC_USAGES
        val uid = UID.group(line)?.toIntOrNull()
        val session = SESSION.group(line)?.toIntOrNull()?.takeIf { it > 0 }
        return if (playable && uid != null && session != null) DumpedPlayer(uid, session) else null
    }

    private fun Regex.group(line: String): String? = find(line)?.groupValues?.get(1)
}
