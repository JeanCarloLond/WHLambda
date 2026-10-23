# Spike: background microphone metering + media volume control on Android

- **Issue:** #2
- **Date:** 2026-10-23
- **Prototype:** branch [`spike/2-android-background-audio`](https://github.com/JeanCarloLond/WHLambda/tree/spike/2-android-background-audio) (throw-away, not merged)
- **Verdict:** ✅ **Feasible.** The Phase 1 architecture (foreground service + `AudioRecord` + `AudioManager`) works on our device in every state we tested, including deep Doze on battery.

## Question

Can a `microphone`-type foreground service read the microphone continuously and change `STREAM_MUSIC` volume
**while Spotify plays**, with the app in the background and the screen off, on a real phone?

## Setup

|             |                                                                                              |
| ----------- | -------------------------------------------------------------------------------------------- |
| Device      | Xiaomi Redmi Note 14 (24117RN76L, board `tanzanite`)                                         |
| OS          | Android 16 (API 36), HyperOS 3.0 (OS3.0.305.0.WOGMIXM), battery saver off                    |
| App         | `targetSdk` 37, foreground service type `microphone`, started from the visible activity      |
| Capture     | `AudioRecord`, 48 kHz mono float, 1 s windows → RMS → dBFS                                   |
| Volume test | `setStreamVolume(STREAM_MUSIC, …)` alternating between two indices, then read back           |
| Music       | Spotify (playlist, then Megadeth 🤘) on the speaker and on REDMI Buds 8 Pro (Bluetooth A2DP) |

## Runs

| Run | Output         | Conditions                                                          | Duration                    | Result                                                             |
| --- | -------------- | ------------------------------------------------------------------- | --------------------------- | ------------------------------------------------------------------ |
| 1   | Speaker        | foreground → background → screen off, plugged in, music not playing | 60 s                        | ✅ mic and volume work with screen off                             |
| 2   | Speaker        | same, Spotify playing                                               | 75 s                        | ✅ mic measures the music; volume changes audible in the mic level |
| A   | Bluetooth A2DP | same, Spotify playing                                               | 62 s (35 s screen off)      | ✅ 12/12 volume changes applied                                    |
| B   | Bluetooth A2DP | **unplugged, locked, in normal use**, Spotify playing               | 13 min (≈ 9 min on battery) | ✅ no gaps, see below                                              |

### Run B in detail (the realistic one)

Timeline from `dumpsys batterystats --history` (reset at 15:43:54):

| Time     | Event                                                             |
| -------- | ----------------------------------------------------------------- |
| 15:43:55 | Service started from the visible app, then app sent to background |
| 15:47:11 | **Unplugged** (`status=discharging plug=none`)                    |
| 15:47:19 | Screen off                                                        |
| 15:49:51 | **Deep Doze** (`device_idle=full`), ~2 min 40 s after unplugging  |
| 15:56:06 | Plugged back in; Doze off; screen on                              |

What the service logged over those 13 minutes:

- **778 samples for 777 s.** No missing seconds, no wall-clock gaps above 2 s; read windows 870–1074 ms (mean 997 ms).
- **Never silenced** (`AudioRecordingConfiguration.isClientSilenced == false` throughout).
- **8 min 48 s of continuous screen-off**, ~6 min of it in deep Doze, and the loop kept running. Audioserver holds an
  `AudioIn` wake lock for our capture, which is what keeps the CPU awake.
- **25/25 volume changes** applied and read back correctly.
- Spotify pausing (the user paused a song) shows up as `isMusicActive=false`.
- HyperOS **did not kill** the service. No autostart or battery whitelist was needed with default settings.

## Findings

1. **The architecture holds.** A `microphone` foreground service started while the app is visible survives
   background, screen-off, battery and deep Doze. A 1 s metering cadence is stable.
2. **The microphone is not silenced by music playback.** Spotify does not capture audio, so there is no concurrent
   capture conflict.
3. **No unprocessed source on this device.** `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` is false; we used
   `VOICE_RECOGNITION` (minimal processing). Calibration is therefore device-specific → spike #3.
4. **Capture stays on the built-in mic with Bluetooth headphones.** Routed device type 15 (`TYPE_BUILTIN_MIC`); A2DP
   playback does not switch the input to the headset, so music quality is not degraded to HFP.
5. **`setStreamVolume` from the service works in every state**, with no special permission.
6. **16 volume steps (0–15).** Measured through the mic on the speaker, one step ≈ **1.9 dB** near the top
   (index 13 → 11: −23.0 → −26.8 dBFS). Coarse enough that the controller must think in steps, not decibels.
7. **Volume is stored per output device.** Speaker and A2DP had different indices (5 vs 12) at the same time.
   The controller must keep state per route and react to route changes.
8. **Bluetooth absolute volume pushes back.** 300 ms after we set index 12, `com.android.bluetooth` set 11 (AVRCP
   round-trip between the 0–15 and 0–127 scales). It happened once in 12 changes.
9. **Manual changes are attributed to the foreground app.** The user's volume-key presses appeared in the audio
   log as `adjustStreamVolume … from com.spotify.music`. The source of a change cannot be trusted. Only "the current
   index differs from what we last set" can.
10. **Battery looks cheap, but the measurement is incomplete.** Android's power model attributes **0.36 mAh** to our
    app over 13 min (≈ 1.7 mAh/h), and the battery stayed at 71 % over the 9 min unplugged run (with Spotify and
    Bluetooth also running). The model does not include microphone hardware or DSP cost, so this is a lower bound.

## Decisions and consequences for Phase 1

| #   | Decision                                                                                                                                                        | Affects      |
| --- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| D1  | Keep the planned design: `microphone` foreground service, started only from a visible UI action, hosting meter → policy → `AudioManager`                        | #9, #10      |
| D2  | The volume policy works in **steps** with a **dead band of at least 1 step**, and waits ~500 ms after setting before trusting a read-back                       | #8           |
| D3  | **Manual override detection** = current index ≠ last index we set (after the settle time), regardless of reported source                                        | #8, #10      |
| D4  | Controller state is **per output route** (speaker, wired, A2DP…); route changes reset the comparison baseline                                                   | #8, #10, #11 |
| D5  | Pause metering while `isMusicActive == false` (resume via `AudioPlaybackCallback`). This saves battery and is the right privacy default: no music, no listening | #10, #15     |
| D6  | Battery cost gets a measured non-functional requirement: run ≥ 1 h unplugged before the MVP release                                                             | #5, #10      |

## Not covered (follow-ups)

- HyperOS **Battery saver / Ultra battery saver** modes and the per-app "Battery restrictions" setting.
- **Runs longer than 1 h**, and behaviour across a reboot (the service must not auto-start without the user).
- **The second phone / other OEMs.** Samsung and others have their own background killers.
- **Wired headphones** (not tested).
- **Google Play foreground-service declaration:** the `microphone` type must be justified in the Play Console.
