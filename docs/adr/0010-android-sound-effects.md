# 0010. Sound effects on Android: DynamicsProcessing on other apps' sessions

- **Status:** Proposed
- **Date:** 2026-11-02
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

Users want an equaliser and effects (bass boost, reverb, surround, extra volume) on the music they already play in
Spotify, YouTube Music and other apps (#40). Android gives no API to process another app's audio stream directly.
What it does allow is attaching `android.media.audiofx` effects to an **audio session** by its id, which works on
another app's session as long as the attaching app holds `MODIFY_AUDIO_SETTINGS`. The hard part is learning the
session ids:

- Players that support equaliser apps broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` with their session. Many
  do (Spotify, VLC), many do not (YouTube Music).
- `AudioPlaybackConfiguration` lists every player, but its session id is a system API.
- The audio service's `dumpsys` output lists every player with its session. Reading it needs the `DUMP` permission,
  which no store app can get by installing but which the user can grant once over ADB
  (`adb shell pm grant <package> android.permission.DUMP`), as equaliser apps such as Wavelet do.
- Session 0, the output mix, takes effects for every app at once. It is deprecated for effects and only honoured on
  some phones.

Checked on the API 36 emulator with a test player, measuring the output mix with `Visualizer`:

| Effect on the player's session            | Requested         | Measured                                         |
| ----------------------------------------- | ----------------- | ------------------------------------------------ |
| `DynamicsProcessing` pre-EQ, 1 kHz band   | +10 dB            | +10.0 dB                                         |
| `DynamicsProcessing` pre-EQ, low bands    | +8 dB at 60 Hz    | +8.0 dB                                          |
| `LoudnessEnhancer`                        | +6 dB             | +6.0 dB                                          |
| `PresetReverb` / `EnvironmentalReverb`    | large hall / 4 s  | a decaying tail after the tone stops; none dry   |
| `BassBoost`, `Virtualizer` on the speaker | strength 1000     | no change (the NXP bundle only acts on headsets) |
| `DynamicsProcessing` on session 0         | +10 dB input gain | attached to the mixer, not measurable from it    |

The app's own run on the emulator, with a player that announced nothing: with `DUMP` granted the player was found
and the Vocal preset raised 1 kHz by exactly 4 dB; turning effects off brought it back and released the effects;
another app taking the session with a higher priority was reported, and effects came back when it let go; after a
reboot, effects were back without opening the app.

## Decision

1. **One effect chain per session** of every music app (`SoundEffectsEngine` in `core:audio`, platform-free and
   unit-tested): `DynamicsProcessing` (10-band pre-EQ, input gain for extra volume, a limiter at −1 dBFS so boosts
   do not clip), `PresetReverb`, and `Virtualizer` where the phone has one. Bass boost is a low shelf added to the
   EQ, not `BassBoost`, so it works on every output.
2. **Sessions come from both sources:** announced sessions always; with `DUMP` granted, the audio service's dump,
   read again on every playback change and every 10 s. When the dump lists sessions it is the whole truth, so a
   session whose player ended without its close broadcast is dropped. Only player metadata is read, never audio.
3. **Global mode** (session 0) is a user choice for phones where neither source finds their player. It replaces the
   per-app chains rather than adding to them, so no app is processed twice.
4. A **special-use foreground service** keeps the process, and so the effects, alive while effects are on. It
   restarts after a reboot or an update when they were on, and when the system kills it.
5. Priority 1: above a system-wide default, below any equaliser app that asks for more. Losing control is shown
   to the user, and the settings are applied again when control returns.
6. **Effects are not counted in the dose.** By the product owner's decision for #40, boosts are allowed and the app
   warns, whenever they add 3 dB or more, that the level at the ear rises and the dose does not include it.

## Alternatives considered

| Option                                         | Pros                                            | Cons                                                                                                                                                 |
| ---------------------------------------------- | ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Session effects + dump** (chosen)            | Works with any player; no root; no audio copied | Full coverage needs a one-time ADB step; another equaliser app can take control                                                                      |
| Announced sessions only                        | No special permission                           | Misses YouTube Music and other apps that do not announce                                                                                             |
| Session 0 only                                 | One chain for everything                        | Deprecated; ignored by many phones and by offloaded playback                                                                                         |
| Capture with `AudioPlaybackCapture` and replay | Real DSP of our own, slowed/nightcore possible  | Screen-capture prompt every start, hundreds of ms of latency, the original must be muted (needs ADB too), copies other apps' audio, against ADR-0007 |
| Count effects in the dose                      | Dose stays truthful                             | Rejected by the product owner for #40; the output model would need the curve's effect on real music                                                  |

## Consequences

- `MODIFY_AUDIO_SETTINGS`, `FOREGROUND_SERVICE_SPECIAL_USE` and `RECEIVE_BOOT_COMPLETED` are added. `DUMP` is
  declared so it can be granted, but never granted by installing. Granting it lets an app read the diagnostic
  output of every system service; this app reads only the audio service's, and the docs and the screen say so.
- A Play Store release must justify the special-use service type, which the manifest describes.
- The dose underestimates exposure while boosts are on. This departs from the spirit of
  [ADR-0006](0006-adaptive-volume-policy.md) (safety first) and needs the deciders' sign-off before this ADR is
  accepted; counting the boost in the output estimate would be the follow-up if they disagree.
- `Virtualizer` is deprecated since Android 16 in favour of `Spatializer`, which apps cannot apply to other apps;
  surround disappears on phones that drop it.
- Not covered by the emulator: audible results on a real phone with Spotify and YouTube Music, global mode on real
  hardware, and offloaded playback, which bypasses software effects on some phones.
- Slowed and nightcore stay out: changing speed cannot be done live on another app's stream.
- Desktop keeps `UnsupportedSoundEffects` until a native component (PipeWire filter chain, Windows APO) is built.
