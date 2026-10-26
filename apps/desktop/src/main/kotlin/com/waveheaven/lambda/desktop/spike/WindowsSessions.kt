// Spike #4 prototype: throw-away code on a branch that is never merged, so production quality rules are waived.
@file:Suppress("MagicNumber", "TooManyFunctions")

package com.waveheaven.lambda.desktop.spike

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Windows: WASAPI audio sessions through COM, called with JNA by vtable index (no native helper to ship).
 *
 * IMMDeviceEnumerator → default render endpoint → IAudioSessionManager2 → IAudioSessionEnumerator →
 * IAudioSessionControl2 (process id, state) and ISimpleAudioVolume (per-session volume, 0.0..1.0).
 */
class WindowsSessions : AppSessions {
    override val name = "WASAPI (JNA COM)"

    init {
        val hr = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
        check(hr.toInt() == 0 || hr.toInt() == S_FALSE) { "CoInitializeEx failed: 0x%08x".format(hr.toInt()) }
    }

    override fun list(): List<AppSession> =
        withSessions { controls ->
            controls.map { (control, volume) ->
                val pid = IntByReference().also { control.call(IAUDIOSESSIONCONTROL2_GET_PROCESS_ID, it) }.value.toLong()
                val state = IntByReference().also { control.call(IAUDIOSESSIONCONTROL_GET_STATE, it) }.value
                val level = FloatByReference().also { volume.call(ISIMPLEAUDIOVOLUME_GET_MASTER_VOLUME, it) }.value
                val binary = if (pid == 0L) null else runCatching { Kernel32Util.QueryFullProcessImageName(pid.toInt(), 0) }.getOrNull()
                AppSession(
                    id = pid.toString(),
                    app = binary?.substringAfterLast('\\') ?: "System sounds",
                    processId = pid,
                    binary = binary,
                    volume = level.toDouble(),
                    active = state == AUDIO_SESSION_STATE_ACTIVE,
                )
            }
        }

    /** Sets every session of the process [id]: an app such as Chrome may open several. */
    override fun setVolume(
        id: String,
        linear: Double,
    ) {
        withSessions { controls ->
            controls.forEach { (control, volume) ->
                val pid = IntByReference().also { control.call(IAUDIOSESSIONCONTROL2_GET_PROCESS_ID, it) }.value
                if (pid.toString() == id) volume.call(ISIMPLEAUDIOVOLUME_SET_MASTER_VOLUME, linear.toFloat(), null)
            }
        }
    }

    private fun <T> withSessions(block: (List<Pair<ComObject, ComObject>>) -> T): T {
        val enumerator = create(CLSID_MM_DEVICE_ENUMERATOR, IID_IMM_DEVICE_ENUMERATOR)
        val device = enumerator.out { enumerator.call(IMMDEVICEENUMERATOR_GET_DEFAULT_AUDIO_ENDPOINT, E_RENDER, E_MULTIMEDIA, it) }
        val manager =
            device.out { device.call(IMMDEVICE_ACTIVATE, Guid.REFIID(IID_IAUDIO_SESSION_MANAGER2), WTypes.CLSCTX_ALL, null, it) }
        val sessions = manager.out { manager.call(IAUDIOSESSIONMANAGER2_GET_SESSION_ENUMERATOR, it) }
        val count = IntByReference().also { sessions.call(IAUDIOSESSIONENUMERATOR_GET_COUNT, it) }.value
        val controls =
            (0 until count).map { i ->
                val control = sessions.out { sessions.call(IAUDIOSESSIONENUMERATOR_GET_SESSION, i, it) }
                val control2 = control.query(IID_IAUDIO_SESSION_CONTROL2)
                val volume = control.query(IID_ISIMPLE_AUDIO_VOLUME)
                control.Release()
                control2 to volume
            }
        return try {
            block(controls)
        } finally {
            controls.forEach { (c, v) ->
                c.Release()
                v.Release()
            }
            listOf(sessions, manager, device, enumerator).forEach { it.Release() }
        }
    }

    /** A COM interface pointer whose methods are called by vtable index. */
    private class ComObject(
        pointer: Pointer,
    ) : Unknown(pointer) {
        fun call(
            vtableIndex: Int,
            vararg args: Any?,
        ) {
            val hr = _invokeNativeInt(vtableIndex, arrayOf(pointer, *args))
            check(hr >= 0) { "COM call #$vtableIndex failed: 0x%08x".format(hr) }
        }

        fun out(call: (PointerByReference) -> Unit): ComObject = PointerByReference().also(call).let { ComObject(it.value) }

        fun query(iid: Guid.IID): ComObject {
            val ref = PointerByReference()
            val hr = QueryInterface(Guid.REFIID(iid), ref)
            check(hr.toInt() >= 0) { "QueryInterface failed: 0x%08x".format(hr.toInt()) }
            return ComObject(ref.value)
        }
    }

    private fun create(
        clsid: Guid.CLSID,
        iid: Guid.IID,
    ): ComObject {
        val ref = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(clsid, null, WTypes.CLSCTX_ALL, iid, ref)
        check(WinNT.S_OK == hr) { "CoCreateInstance failed: 0x%08x".format(hr.toInt()) }
        return ComObject(ref.value)
    }

    private companion object {
        const val S_FALSE = 1
        const val E_RENDER = 0
        const val E_MULTIMEDIA = 1
        const val AUDIO_SESSION_STATE_ACTIVE = 1

        // vtable indices: IUnknown occupies 0..2.
        const val IMMDEVICEENUMERATOR_GET_DEFAULT_AUDIO_ENDPOINT = 4
        const val IMMDEVICE_ACTIVATE = 3
        const val IAUDIOSESSIONMANAGER2_GET_SESSION_ENUMERATOR = 5
        const val IAUDIOSESSIONENUMERATOR_GET_COUNT = 3
        const val IAUDIOSESSIONENUMERATOR_GET_SESSION = 4
        const val IAUDIOSESSIONCONTROL_GET_STATE = 3
        const val IAUDIOSESSIONCONTROL2_GET_PROCESS_ID = 14
        const val ISIMPLEAUDIOVOLUME_SET_MASTER_VOLUME = 3
        const val ISIMPLEAUDIOVOLUME_GET_MASTER_VOLUME = 4

        val CLSID_MM_DEVICE_ENUMERATOR = Guid.CLSID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
        val IID_IMM_DEVICE_ENUMERATOR = Guid.IID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
        val IID_IAUDIO_SESSION_MANAGER2 = Guid.IID("{77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F}")
        val IID_IAUDIO_SESSION_CONTROL2 = Guid.IID("{BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D}")
        val IID_ISIMPLE_AUDIO_VOLUME = Guid.IID("{87CE5498-68D6-44E5-9215-6DA47EF883D8}")
    }
}
