// Spike #4 prototype: throw-away code on a branch that is never merged, so production quality rules are waived.
@file:Suppress("MagicNumber", "TooManyFunctions")

package com.waveheaven.lambda.desktop.spike

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Linux: PulseAudio or PipeWire (through pipewire-pulse) via the `pactl` command, whose JSON output (pactl 16+)
 * lists every playback stream ("sink input") with its application properties and volume.
 *
 * Pulse volumes are cubic: raw value / 65536 = cbrt(linear amplitude).
 */
class PulseSessions : AppSessions {
    override val name = pactl("--version").lineSequence().first()

    override fun list(): List<AppSession> =
        Json.parseToJsonElement(pactl("-f", "json", "list", "sink-inputs")).jsonArray.map { element ->
            val input = element.jsonObject
            val properties = input["properties"]!!.jsonObject
            val raw =
                (input["volume"] as JsonObject)
                    .values
                    .first()
                    .jsonObject["value"]!!
                    .jsonPrimitive.long
            AppSession(
                id = input["index"]!!.jsonPrimitive.content,
                app = properties.string("application.name") ?: properties.string("media.name") ?: "?",
                processId = properties.string("application.process.id")?.toLongOrNull(),
                binary = properties.string("application.process.binary"),
                volume = (raw.toDouble() / NORM).pow(3),
                active = !input["corked"]!!.jsonPrimitive.boolean,
            )
        }

    override fun setVolume(
        id: String,
        linear: Double,
    ) {
        pactl("set-sink-input-volume", id, (cbrt(linear) * NORM).roundToLong().toString())
    }

    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.content

    private fun pactl(vararg args: String): String {
        val process =
            ProcessBuilder(listOf("pactl") + args)
                .redirectErrorStream(true)
                .apply { environment()["LC_ALL"] = "C" }
                .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "pactl ${args.joinToString(" ")} failed: $output" }
        return output
    }

    private companion object {
        const val NORM = 65536.0
    }
}
