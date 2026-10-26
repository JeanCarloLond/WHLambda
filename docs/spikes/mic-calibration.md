# Spike: microphone calibration, dBFS to dB(A) SPL

- **Issue:** #3
- **Date:** 2026-10-26
- **Prototype:** branch [`spike/3-mic-calibration`](https://github.com/JeanCarloLond/WHLambda/tree/spike/3-mic-calibration) (A-weighting cost benchmark, throw-away, not merged)
- **Verdict:** ⏳ **Partly answered.** The calibration strategy, the A-weighting choice and the error budget are settled.
  The measurements against a reference meter on our two phones are still pending: they need the phones and a
  reference meter in the same room, and the [protocol](#measurement-protocol-pending) below is ready for that session.

## Question

How far off is an uncalibrated phone microphone from a reference sound-level meter, and what calibration flow is
good enough for the volume policy?

## What the accuracy is for

The ambient level drives **comfort only**. In [`VolumeController`](../domain/volume-controller.md) the target is
`ambient Leq + target margin`, while the safety ceiling comes from the remaining dose and the _output_ level model
(#11). The microphone never enters the ceiling. So a microphone that reads `e` dB too high makes the controller aim
`e` dB louder than intended, and the ceiling still caps it.

That sets the error budget: the controller ignores target changes smaller than its 3 dB hysteresis, so an offset
error of **±3 dB** in the range where adaptation matters (about 60 to 85 dB(A): offices, streets, buses, gyms) is
invisible in practice. Errors outside that range only move the volume to one end of the profile's range sooner.

## What Android promises

The Android Compatibility Definition Document (CDD) gives the only cross-device reference. Its audio capture
requirements are mostly _SHOULD_, so they are targets, not guarantees:

| Source                         | CDD section | Sensitivity                                          | Frequency response          | Linearity                               | Processing                  |
| ------------------------------ | ----------- | ---------------------------------------------------- | --------------------------- | --------------------------------------- | --------------------------- |
| `VOICE_RECOGNITION`            | 5.4.2       | 90 dB SPL at 1 kHz → RMS 2500 of 32768 (−22.35 dBFS) | ±3 dB from 100 Hz to 4 kHz  | 30 dB range, −18 to +12 dB re 90 dB SPL | AGC and noise reduction off |
| `UNPROCESSED` (when supported) | 5.11        | 94 dB SPL at 1 kHz → RMS 520 of 32768 (−36 dBFS)     | ±10 dB from 100 Hz to 7 kHz | wider, specified separately             | none                        |

Consequences:

- The **nominal offsets** are `90 + 22.35 = 112.35 dB` for `VOICE_RECOGNITION` (already the default in
  `MicrophoneCalibration`) and `94 + 36 = 130 dB` for `UNPROCESSED`. The offset belongs to the _source_, not only
  to the device.
- The CDD only speaks about linearity from **72 to 102 dB SPL**. Below that the self-noise of the microphone and
  any residual processing are unspecified, so quiet rooms (≈ 40 to 50 dB(A)) may read high. The policy tolerates
  this: in a quiet room the target already falls to the bottom of the profile's range.
- `UNPROCESSED` was not available on the spike #2 phone (`PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED == false`),
  so `VOICE_RECOGNITION` stays the default and `UNPROCESSED` is used only when the property says it is supported.

Published measurements agree that the spread between Android devices is the main problem: Kardous and Shaw
(_J. Acoust. Soc. Am._ 135, EL186, 2014) found several iOS sound-level apps within ±2 dB(A) of a type 1 meter,
but left Android apps out of the detailed comparison because hardware and calibration were too inconsistent
across devices. A per-device (or per-model) offset is therefore needed for a tight estimate, while the nominal
CDD offset is the honest default.

## A-weighting: IIR filter or FFT bins

Both options were implemented and compared on 60 s of 48 kHz audio
([benchmark](https://github.com/JeanCarloLond/WHLambda/blob/spike/3-mic-calibration/core/audio/src/desktopTest/kotlin/com/waveheaven/lambda/core/audio/spike/AWeightingCostBenchmark.kt)):

- **IIR:** the production `LevelMeter`, three cascaded biquads applied sample by sample.
- **FFT:** Welch power spectrum (4096-point Hann frames, 50 % overlap, precomputed twiddles) with each bin weighted
  by the IEC 61672 formula.

Desktop JVM, Intel Core i7-1165G7, third run after warm-up:

| Signal      | IIR (ns/sample) | IIR CPU per second of audio | FFT (ns/sample) | FFT CPU per second of audio | Max difference |
| ----------- | --------------- | --------------------------- | --------------- | --------------------------- | -------------- |
| Pink noise  | 15.2            | 0.73 ms                     | 71.3            | 3.42 ms                     | 0.19 dB        |
| 1 kHz tone  | 16.4            | 0.79 ms                     | 73.3            | 3.52 ms                     | 0.00 dB        |
| 100 Hz tone | 17.0            | 0.82 ms                     | 72.1            | 3.46 ms                     | 0.08 dB        |
| 6 kHz tone  | 19.7            | 0.94 ms                     | 77.7            | 3.73 ms                     | 0.55 dB        |

The two agree within 0.55 dB (the IIR's bilinear transform drifts slightly near 6 to 8 kHz, still inside IEC Class 1
limits, see `AWeightingFilterTest`). The IIR is about **4.5 times cheaper**, streams without buffering a whole
frame, and is already tested. Even assuming a phone core ten times slower than this laptop, the IIR costs under
1 % of one core. The spike #4 prototype measured capture plus A-weighting end to end at **0.5 % of one core** on the
same laptop.

**Decision: keep the IIR filter.** The FFT would only pay off if we needed octave bands, which we do not.

## Recommended calibration strategy

Three layers, from always-available to most accurate:

1. **Nominal offset per audio source (default).** `VOICE_RECOGNITION` → 112.35 dB, `UNPROCESSED` → 130 dB. Expected
   error: several dB, unknown sign. Acceptable because the ambient level only drives comfort (see above).
2. **Measured offset per phone model.** A small table in `core:audio` keyed by `Build.MANUFACTURER`, `Build.MODEL`
   and the audio source, filled from our own measurements (protocol below) and later from users who calibrate.
   Used when the device matches; falls back to layer 1.
3. **Optional user calibration** (WH-004), in settings:
   1. The app explains that this only fine-tunes comfort, never the safety limit.
   2. The user places the phone next to a reference: a sound-level meter, or a calibrated meter app on another
      phone (for example NIOSH SLM on an iPhone, which is the best-documented free option).
   3. A steady sound plays or runs nearby (a fan, a running tap, pink noise from a speaker), ideally between 60 and
      80 dB(A).
   4. The app shows its own reading as a 10 s Leq with the current offset; the user types the reference reading.
   5. The new offset is `current offset + (reference − ours)`, accepted only within ±15 dB of the nominal offset
      (anything larger points to a wrong setup, not a bad microphone), stored locally per audio source, and
      reversible with "Reset to default".

The desktop has no CDD equivalent: microphones and input gain vary by orders of magnitude between PCs (the spike
#4 laptop read a quiet room at about −25 dB(A)FS with the input at 100 %). On desktop the user calibration is the
only meaningful path; until a user calibrates, the app should label the ambient level as approximate.

## Measurement protocol (pending)

To fill in the table below, in one session:

- **Equipment:** both phones, a reference meter set to A-weighting, slow time weighting (a type 2 meter, or a
  calibrated app on an iPhone), a speaker playing pink noise.
- **Setup:** phones and reference side by side, microphones facing the speaker, about 1 m away, in a room without
  strong reflections; phone cases removed.
- **Levels:** adjust the speaker to read about 50, 65 and 80 dB(A) on the reference. At each level record a 30 s
  Leq on the reference and on each phone (the ambient level shown by the app's notification once #10 lands, with
  the nominal offset), three repetitions.
- **Per phone and source:** offset error `reference − ours` at each level, the slope of ours against the reference
  (linearity, ideal 1.0), and the spread between repetitions.

| Phone                | Source              | ≈ 50 dB(A) | ≈ 65 dB(A) | ≈ 80 dB(A) | Slope | Recommended offset |
| -------------------- | ------------------- | ---------- | ---------- | ---------- | ----- | ------------------ |
| Redmi Note 14        | `VOICE_RECOGNITION` | pending    | pending    | pending    |       |                    |
| Second phone (model) | `VOICE_RECOGNITION` | pending    | pending    | pending    |       |                    |

The spike is done when this table is filled: the offsets go into the per-model table (layer 2) and the verdict
says whether the nominal offset alone stays within the ±3 dB budget.

## Decisions and consequences

| #   | Decision                                                                                                                                                          | Affects  |
| --- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- |
| C1  | The ambient level is a comfort input; the error budget is ±3 dB between 60 and 85 dB(A). Safety never depends on the microphone                                   | #8, #10  |
| C2  | Keep the IIR A-weighting filter; no FFT                                                                                                                           | #9       |
| C3  | The calibration offset belongs to the audio source: 112.35 dB for `VOICE_RECOGNITION`, 130 dB for `UNPROCESSED`; prefer `UNPROCESSED` when the device supports it | #9, #10  |
| C4  | Offsets come from, in order: the user's calibration, a measured per-model table, the nominal CDD value                                                            | #10, #15 |
| C5  | On desktop, label the ambient level approximate until the user calibrates                                                                                         | #17      |
