# Spike: per-application volume control from the JVM on Windows and Linux

- **Issue:** #4
- **Date:** 2026-10-26
- **Prototype:** branch [`spike/4-desktop-app-volume`](https://github.com/JeanCarloLond/WHLambda/tree/spike/4-desktop-app-volume) (throw-away, not merged), run with `./gradlew :apps:desktop:runSpike --args="<app> <mic seconds>"`
- **Verdict:** ✅ **Linux: feasible and validated.** ⏳ **Windows: designed and compiling, not yet run** (no Windows
  machine was available for this session). Decision in [ADR-0009](../adr/0009-desktop-per-app-volume.md).

## Question

What is the simplest robust way for a Compose Desktop (JVM) app to read and set the volume of **another
application** (Spotify, a browser), and to meter the microphone, on Windows and Linux?

## Setup

|             | Linux                                                                                                  |
| ----------- | ------------------------------------------------------------------------------------------------------ |
| Machine     | Laptop, Intel Core i7-1165G7, Realtek ALC256 built-in microphone                                       |
| OS          | Arch Linux, PipeWire 1.6.7 with `pipewire-pulse` (PulseAudio protocol 35), WirePlumber                 |
| Tools       | `pactl` 17.0 (JSON output), `parec`, JDK 21                                                            |
| Output      | Nothing Ear over Bluetooth A2DP (default sink), built-in speakers                                      |
| Test stream | A silent stream from `paplay` named "WHL spike", so tests never changed what the user was listening to |

## Options evaluated

| Platform | Option                                             | Result                                                                                                                |
| -------- | -------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Linux    | **`pactl` subprocess, JSON output**                | ✅ Chosen. Lists every playback stream with its application, process and volume; sets volume per stream               |
| Linux    | `libpulse` through JNA                             | Not needed. Asynchronous main-loop API, far more code than a 6 ms subprocess call once per second                     |
| Linux    | `wpctl` / `pw-dump`                                | `wpctl` has no machine-readable output; `pw-dump` is complete but heavy and PipeWire-only (no plain PulseAudio)       |
| Windows  | **WASAPI sessions through JNA COM** (vtable calls) | Chosen, pending a run. Pure Java dependency (`jna-platform` ships the Windows natives); prototype compiles            |
| Windows  | Small native helper (C++ executable or DLL)        | Another toolchain, build and code-signing step for two people                                                         |
| Windows  | PowerShell with inline C#                          | Seconds of start-up per call; too slow for a 1 s loop                                                                 |
| Both     | Microphone via `javax.sound.sampled`               | Works on Linux, but only reaches the ALSA `default` device (see finding 5). Expected to work on Windows (DirectSound) |

## Linux results

From the prototype (times include starting the `pactl` process):

| Operation                                      | Time                     | Result                                                                   |
| ---------------------------------------------- | ------------------------ | ------------------------------------------------------------------------ |
| List streams (first call)                      | 35 ms                    | Application name, process id, binary, volume and corked state per stream |
| List streams (next five)                       | 5.2 – 6.4 ms             |                                                                          |
| Set volume to 0.5 / 0.25 / 0.8 / 1.0 (linear)  | 5.5 – 6.8 ms per call    | Read back exactly: −6.02, −12.04, −1.94, 0.00 dB                         |
| Microphone, 8 s via javax.sound + A-weighting  | 40.7 ms CPU over 8149 ms | **0.5 % of one core**, levels −42 to −23 dB(A)FS in a quiet room         |
| Microphone via `parec --device=<built-in mic>` | —                        | 1 s windows read correctly; Bluetooth profile stayed on A2DP             |

## Findings

1. **`pactl` works the same on PipeWire and PulseAudio**, since PipeWire implements the PulseAudio protocol. The
   JSON output (`pactl -f json`) needs PulseAudio 16 or later; older systems would need the text output parsed.
   `pactl` comes with `pulseaudio-utils` / `libpulse`, which the DEB and RPM packages should declare as a
   dependency.
2. **Volume is per stream, not per application.** A browser opens one stream per tab, and an app may recreate its
   stream between tracks, which gives it a new index. The adapter must resolve the target application on every
   tick and set all of its streams.
3. **Identify applications by name, then binary.** `application.name` is the stable, human-readable key
   (`Spotify`, `Firefox`); `application.process.binary` is less useful (a Windows game under Wine reported
   `wine64-preloader`).
4. **Pulse volumes are cubic.** 50 % in `pactl` is −18.06 dB, not −6 dB: the raw value is `cbrt(linear) · 65536`.
   The adapter must speak raw values or decibels, never percentages, or the steps will be uneven.
5. **The default capture device is a trap with Bluetooth headphones.** javax.sound only sees ALSA's `default`
   device (plus raw `hw:` devices that PipeWire already owns), and `default` follows the default source, which with
   Bluetooth headphones connected is the headset microphone. Capturing it switches the headphones from A2DP to the
   HFP profile and ruins music quality, the same trap as Android finding 4 in [spike #2](android-background-audio.md).
   The built-in microphone can be forced with `PIPEWIRE_NODE`, but only at process start. `parec --device=<source>`
   streaming float samples over a pipe reaches any source, keeps samples in memory, and was validated without a
   profile switch. Pick the source whose `device.bus` is not `bluetooth`, preferring `device.form_factor=internal`.
6. **The output level depends on two volumes.** What reaches the ear is the device (sink) volume times the stream
   volume. With Bluetooth the sink volume is the headphones' absolute volume. The output model must read both.
7. **Cost is negligible:** about 6 ms per `pactl` call at one call per second, and 0.5 % of one core for capture
   plus A-weighting.

## Windows design (to verify)

The prototype calls COM by vtable index through JNA, with no native code to ship:

```
CoCreateInstance(MMDeviceEnumerator)
  → IMMDeviceEnumerator::GetDefaultAudioEndpoint(eRender, eMultimedia)
  → IMMDevice::Activate(IAudioSessionManager2)
  → IAudioSessionManager2::GetSessionEnumerator → IAudioSessionEnumerator::GetCount / GetSession
  → IAudioSessionControl2::GetProcessId / GetState     (process image name via QueryFullProcessImageName)
  → QueryInterface(ISimpleAudioVolume)::GetMasterVolume / SetMasterVolume
```

Questions the verification run must answer:

- Does the code list Spotify's and Chrome's sessions and change their volume? (One app may own several sessions; the
  prototype sets all sessions of the process.)
- Is `ISimpleAudioVolume`'s 0.0 – 1.0 level linear amplitude, as the prototype assumes? Check by playing a tone and
  measuring the loopback level at 1.0, 0.5 and 0.25 (expect −6 and −12 dB).
- COM threading: the calls run from one dedicated thread initialised as multithreaded apartment.
- Microphone: javax.sound lists DirectSound devices by name, so the built-in microphone can be chosen and a
  Bluetooth headset microphone avoided.

## Decisions and consequences

| #   | Decision                                                                                                                          | Affects  |
| --- | --------------------------------------------------------------------------------------------------------------------------------- | -------- |
| P1  | Linux: `pactl` JSON subprocess for streams and volumes; `parec` subprocess for the microphone, never the Bluetooth headset source | #17      |
| P2  | Windows: WASAPI sessions through JNA COM; javax.sound for the microphone, choosing a non-headset device                           | #17      |
| P3  | Target one application chosen by the user, matched by name every tick; set all of its streams                                     | #17      |
| P4  | Desktop volume steps are fixed decibel steps converted to each platform's scale, so the controller's step logic stays unchanged   | #17      |
| P5  | The desktop output estimate uses device volume × stream volume                                                                    | #11, #17 |

## Not covered (follow-ups)

- **The Windows run** (above). Until then the issue stays open.
- PulseAudio older than 16 (no JSON output).
- Flatpak or Snap packaged apps, whose streams may report a sandbox binary instead of the real one.
- Exclusive-mode WASAPI streams, which bypass the session mixer.
