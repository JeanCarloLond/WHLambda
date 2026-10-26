package com.waveheaven.lambda.core.audio

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
 * Windows: WASAPI audio sessions through COM, called with JNA by vtable index, so there is no native code to ship
 * (ADR-0009). Sessions of the default render endpoint are grouped by process; the session volume
 * (`ISimpleAudioVolume`) is taken as linear amplitude, which the spike #4 verification run on Windows must confirm.
 */
internal class WasapiSessions : AppSessions {
    override fun streams(): List<AppStream> =
        withEndpoint { device ->
            sessions(device) { control, volume ->
                val pid = control.outInt(IAUDIOSESSIONCONTROL2_GET_PROCESS_ID)
                AppStream(
                    id = pid.toString(),
                    app = if (pid == 0) SYSTEM_SOUNDS else processName(pid),
                    linearVolume = volume.outFloat(ISIMPLEAUDIOVOLUME_GET_MASTER_VOLUME).toDouble(),
                    isPlaying = control.outInt(IAUDIOSESSIONCONTROL_GET_STATE) == AUDIO_SESSION_STATE_ACTIVE,
                )
            }
        }

    /** Sets every session of the process [streamId]: an app such as Chrome may open several. */
    override fun setVolume(
        streamId: String,
        linear: Double,
    ) {
        withEndpoint { device ->
            sessions(device) { control, volume ->
                if (control.outInt(IAUDIOSESSIONCONTROL2_GET_PROCESS_ID).toString() == streamId) {
                    volume.call(ISIMPLEAUDIOVOLUME_SET_MASTER_VOLUME, linear.coerceIn(0.0, 1.0).toFloat(), null)
                }
            }
        }
    }

    override fun outputId(): String =
        withEndpoint { device ->
            val ref = PointerByReference()
            device.call(IMMDEVICE_GET_ID, ref)
            try {
                ref.value.getWideString(0)
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(ref.value)
            }
        }

    override fun outputGainDb(): Double =
        withEndpoint { device ->
            val endpoint =
                device.out {
                    device.call(
                        IMMDEVICE_ACTIVATE,
                        Guid.REFIID(IID_IAUDIO_ENDPOINT_VOLUME),
                        WTypes.CLSCTX_ALL,
                        null,
                        it,
                    )
                }
            try {
                endpoint.outFloat(IAUDIOENDPOINTVOLUME_GET_MASTER_VOLUME_LEVEL).toDouble()
            } finally {
                endpoint.Release()
            }
        }

    private fun <T> withEndpoint(block: (ComObject) -> T): T {
        val hr = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED).toInt()
        check(hr == 0 || hr == S_FALSE || hr == RPC_E_CHANGED_MODE) { "CoInitializeEx failed: 0x%08x".format(hr) }
        val enumerator = create(CLSID_MM_DEVICE_ENUMERATOR, IID_IMM_DEVICE_ENUMERATOR)
        try {
            val device = enumerator.out { enumerator.call(IMMDEVICEENUMERATOR_GET_DEFAULT_AUDIO_ENDPOINT, E_RENDER, E_MULTIMEDIA, it) }
            try {
                return block(device)
            } finally {
                device.Release()
            }
        } finally {
            enumerator.Release()
        }
    }

    private fun <T> sessions(
        device: ComObject,
        block: (control: ComObject, volume: ComObject) -> T,
    ): List<T> {
        val manager = device.out { device.call(IMMDEVICE_ACTIVATE, Guid.REFIID(IID_IAUDIO_SESSION_MANAGER2), WTypes.CLSCTX_ALL, null, it) }
        try {
            val enumerator = manager.out { manager.call(IAUDIOSESSIONMANAGER2_GET_SESSION_ENUMERATOR, it) }
            try {
                return (0 until enumerator.outInt(IAUDIOSESSIONENUMERATOR_GET_COUNT)).map { i ->
                    val session = enumerator.out { enumerator.call(IAUDIOSESSIONENUMERATOR_GET_SESSION, i, it) }
                    val control = session.query(IID_IAUDIO_SESSION_CONTROL2)
                    val volume = session.query(IID_ISIMPLE_AUDIO_VOLUME)
                    session.Release()
                    try {
                        block(control, volume)
                    } finally {
                        control.Release()
                        volume.Release()
                    }
                }
            } finally {
                enumerator.Release()
            }
        } finally {
            manager.Release()
        }
    }

    private fun processName(pid: Int): String =
        runCatching { Kernel32Util.QueryFullProcessImageName(pid, 0) }
            .getOrNull()
            ?.substringAfterLast('\\')
            ?.removeSuffix(".exe")
            ?.removeSuffix(".EXE")
            ?: "pid $pid"

    private fun create(
        clsid: Guid.CLSID,
        iid: Guid.IID,
    ): ComObject {
        val ref = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(clsid, null, WTypes.CLSCTX_ALL, iid, ref)
        check(WinNT.S_OK == hr) { "CoCreateInstance failed: 0x%08x".format(hr.toInt()) }
        return ComObject(ref.value)
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

        fun outInt(vtableIndex: Int): Int = IntByReference().also { call(vtableIndex, it) }.value

        fun outFloat(vtableIndex: Int): Float = FloatByReference().also { call(vtableIndex, it) }.value

        fun query(iid: Guid.IID): ComObject {
            val ref = PointerByReference()
            val hr = QueryInterface(Guid.REFIID(iid), ref)
            check(hr.toInt() >= 0) { "QueryInterface failed: 0x%08x".format(hr.toInt()) }
            return ComObject(ref.value)
        }
    }

    private companion object {
        const val SYSTEM_SOUNDS = "System sounds"
        const val S_FALSE = 1
        const val RPC_E_CHANGED_MODE = 0x80010106.toInt()
        const val E_RENDER = 0
        const val E_MULTIMEDIA = 1
        const val AUDIO_SESSION_STATE_ACTIVE = 1

        // vtable indices: IUnknown occupies 0..2.
        const val IMMDEVICEENUMERATOR_GET_DEFAULT_AUDIO_ENDPOINT = 4
        const val IMMDEVICE_ACTIVATE = 3
        const val IMMDEVICE_GET_ID = 5
        const val IAUDIOSESSIONMANAGER2_GET_SESSION_ENUMERATOR = 5
        const val IAUDIOSESSIONENUMERATOR_GET_COUNT = 3
        const val IAUDIOSESSIONENUMERATOR_GET_SESSION = 4
        const val IAUDIOSESSIONCONTROL_GET_STATE = 3
        const val IAUDIOSESSIONCONTROL2_GET_PROCESS_ID = 14
        const val ISIMPLEAUDIOVOLUME_SET_MASTER_VOLUME = 3
        const val ISIMPLEAUDIOVOLUME_GET_MASTER_VOLUME = 4
        const val IAUDIOENDPOINTVOLUME_GET_MASTER_VOLUME_LEVEL = 8

        val CLSID_MM_DEVICE_ENUMERATOR = Guid.CLSID("{BCDE0395-E52F-467C-8E3D-C4579291692E}")
        val IID_IMM_DEVICE_ENUMERATOR = Guid.IID("{A95664D2-9614-4F35-A746-DE8DB63617E6}")
        val IID_IAUDIO_SESSION_MANAGER2 = Guid.IID("{77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F}")
        val IID_IAUDIO_SESSION_CONTROL2 = Guid.IID("{BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D}")
        val IID_ISIMPLE_AUDIO_VOLUME = Guid.IID("{87CE5498-68D6-44E5-9215-6DA47EF883D8}")
        val IID_IAUDIO_ENDPOINT_VOLUME = Guid.IID("{5CDF2C82-841E-4546-9722-0CF74078229A}")
    }
}
