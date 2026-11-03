package com.waveheaven.lambda.core.audio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

/**
 * The playback sessions of other apps (ADR-0010), from two sources:
 * 1. **Announced sessions.** Players that support equaliser apps broadcast
 *    [AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION] when they start and the matching close when they stop.
 * 2. **Detected sessions.** With the `DUMP` permission, which the user grants once over ADB, the audio service's
 *    dump lists every player and its session ([AudioServiceDump]); it is read again whenever playback changes and
 *    every [RESCAN_MILLIS] as a fallback. Without the permission the dump is refused and this source is unused.
 *
 * When the dump lists sessions it is the whole truth, so an announced session missing from it (its player ended
 * without the close broadcast) is dropped.
 *
 * Only player metadata is read, never audio.
 */
class AndroidPlaybackSessions(
    private val context: Context,
) {
    private val packages: PackageManager = context.packageManager
    private val audio: AudioManager = context.getSystemService(AudioManager::class.java)

    /** Whether the diagnostic permission is granted, so every player is detected. */
    fun fullDetection(): Boolean = context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

    /** Sessions of other apps' players, updated as they start and stop. Collect on the main thread. */
    fun sessions(): Flow<List<PlaybackSession>> =
        combine(announced(), detected()) { announced, detected ->
            (detected ?: announced).sortedBy { it.id }
        }.distinctUntilChanged()

    private fun announced(): Flow<List<PlaybackSession>> =
        callbackFlow {
            val open = mutableMapOf<Int, PlaybackSession>()
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
                        val owner = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
                        if (session <= 0 || owner == context.packageName) return
                        when (intent.action) {
                            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> open[session] = PlaybackSession(session, label(owner))
                            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> open.remove(session)
                        }
                        trySend(open.values.toList())
                    }
                }
            val filter =
                IntentFilter().apply {
                    addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                    addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                }
            // Exported: the broadcasts come from the music apps.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            trySend(emptyList())
            awaitClose { context.unregisterReceiver(receiver) }
        }

    /** `null` while detection is not possible: no permission, or a dump that does not list sessions. */
    private fun detected(): Flow<List<PlaybackSession>?> =
        callbackFlow {
            val changes = MutableStateFlow(0)
            val callback =
                object : AudioManager.AudioPlaybackCallback() {
                    override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                        changes.value += 1
                    }
                }
            audio.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
            val ticker =
                launch {
                    while (true) {
                        delay(RESCAN_MILLIS)
                        changes.value += 1
                    }
                }
            launch {
                changes.collect { send(if (fullDetection()) withContext(Dispatchers.IO) { scan() } else null) }
            }
            awaitClose {
                ticker.cancel()
                audio.unregisterAudioPlaybackCallback(callback)
            }
        }.flowOn(Dispatchers.Main)

    private fun scan(): List<PlaybackSession>? {
        val dump = dumpAudioService()
        if (!AudioServiceDump.listsSessions(dump)) return null
        val own = Process.myUid()
        return AudioServiceDump
            .mediaPlayers(dump)
            .filter { it.uid != own }
            .map { PlaybackSession(it.sessionId, label(appOf(it.uid))) }
    }

    /** The audio service's dump; a "Permission Denial" line without the `DUMP` permission. */
    private fun dumpAudioService(): String {
        val (read, write) = ParcelFileDescriptor.createPipe()
        // The service writes into the pipe while it is read, so a dump larger than the pipe buffer cannot block.
        thread(name = "audio-dump") {
            write.use { runCatching { Debug.dumpService(Context.AUDIO_SERVICE, it.fileDescriptor, emptyArray()) } }
        }
        return ParcelFileDescriptor.AutoCloseInputStream(read).bufferedReader().use { it.readText() }
    }

    private fun appOf(uid: Int): String? = packages.getPackagesForUid(uid)?.firstOrNull() ?: packages.getNameForUid(uid)

    /** The app's name as the launcher shows it, or its package when it is not visible to this app. */
    private fun label(packageName: String?): String {
        if (packageName == null) return UNKNOWN_APP
        return try {
            packages.getApplicationLabel(packages.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private companion object {
        const val RESCAN_MILLIS = 10_000L
        const val UNKNOWN_APP = "?"
    }
}
