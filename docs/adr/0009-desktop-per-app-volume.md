# 0009. Per-application volume on desktop: WASAPI through JNA, pactl on Linux

- **Status:** Proposed (Linux validated; becomes Accepted after the Windows verification run of spike #4)
- **Date:** 2026-10-26
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

On desktop the app must change the volume of another application (Spotify, a browser) and meter the microphone,
from a Compose Desktop app on the JVM, on Windows and Linux ([ADR-0002](0002-platforms-android-first.md)). The JVM
has no API for either. [Spike #4](../spikes/desktop-app-volume.md) compared the options: native libraries through
JNA, a native helper, and command-line tools.

Findings that shape the decision: Linux streams are per stream, not per application, and their volume is cubic;
capturing the default source with Bluetooth headphones switches them to the low-quality HFP profile; a `pactl` call
costs about 6 ms.

## Decision

1. **Linux:** talk to PulseAudio or PipeWire (`pipewire-pulse`) through the `pactl` command with JSON output, once
   per tick. Capture the microphone with `parec --device=<source>` over a pipe, choosing a source that is not on the
   Bluetooth bus.
2. **Windows:** call WASAPI's audio-session interfaces (`IAudioSessionManager2`, `IAudioSessionControl2`,
   `ISimpleAudioVolume`) through COM with JNA, from one dedicated thread. Capture with `javax.sound.sampled`,
   choosing a device that is not a headset microphone.
3. Both sit behind the existing domain ports `MediaVolume` (one target application, chosen by the user) and
   `AmbientMeter`, in `core:audio`'s desktop source set.

## Alternatives considered

| Option                          | Pros                                                                        | Cons                                                         |
| ------------------------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------ |
| **pactl subprocess** (chosen)   | No native code; one tool for PulseAudio and PipeWire; easy to debug by hand | A process per call (≈ 6 ms); needs PulseAudio 16+ for JSON   |
| libpulse through JNA            | No subprocess                                                               | Asynchronous main-loop API; much more code and crash surface |
| **JNA COM on Windows** (chosen) | Pure Java dependency; no toolchain or signing                               | Vtable indices written by hand; errors surface at runtime    |
| Native helper executable        | Full Win32/C++ API                                                          | Extra toolchain, build and code signing                      |
| PowerShell + inline C#          | No compilation                                                              | Seconds of start-up per call                                 |

## Consequences

- No native code in the repository; `jna-platform` and `kotlinx-serialization-json` become dependencies of
  `core:audio` on desktop.
- The Linux packages depend on `pactl`/`parec` (`pulseaudio-utils` on Debian/Ubuntu, `pulseaudio-utils` or
  `pipewire-pulseaudio` on Fedora).
- Volume steps on desktop are fixed decibel steps converted to each platform's scale, so `VolumeController` works
  unchanged.
- Windows support is provisional until the verification run; if `ISimpleAudioVolume` turns out not to be linear,
  only the conversion in the Windows adapter changes.
