# Equaliser and effects

The equaliser and sound effects the app applies to other apps' music on Android (#40, WH-090 – WH-097). Why it
works this way, and what was measured, is in [ADR-0010](../adr/0010-android-sound-effects.md). Code:
[`core:domain` › `effects`](../../core/domain/src/commonMain/kotlin/com/waveheaven/lambda/core/domain/effects/) (settings
and how they combine), [`core:audio`](../../core/audio/src/commonMain/kotlin/com/waveheaven/lambda/core/audio/SoundEffectsEngine.kt)
(one chain per session), [`feature:soundeffects`](../../feature/soundeffects/) (screen),
[`apps:android` › `effects`](../../apps/android/src/main/kotlin/com/waveheaven/lambda/effects/) (service).

## What the user sets

| Setting      | Range                                                  | Applied as                                                     |
| ------------ | ------------------------------------------------------ | -------------------------------------------------------------- |
| Equaliser    | 10 bands, 31 Hz – 16 kHz, −12 to +12 dB                | `DynamicsProcessing` pre-EQ, one band per octave               |
| Style        | 11 presets, or the user's own curve                    | The preset's gains; moving a band switches to the user's curve |
| Bass boost   | 0 – 12 dB                                              | A first-order low shelf at 120 Hz added to the curve           |
| Extra volume | 0 – 10 dB                                              | `DynamicsProcessing` input gain, then a limiter at −1 dBFS     |
| Reverb       | Off, small/medium/large room, medium/large hall, plate | `PresetReverb` (the standard I3DL2 rooms)                      |
| Surround     | 0 – 100 %, headphones only                             | `Virtualizer`, shown only where the phone has one              |
| Where        | Each music app, or all the phone's audio               | Per-session chains, or one chain on the output mix (session 0) |

Presets, in dB from 31 Hz to 16 kHz:

| Preset     |  31 |  62 | 125 | 250 | 500 |  1k |  2k |  4k |  8k | 16k |
| ---------- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: |
| Flat       |   0 |   0 |   0 |   0 |   0 |   0 |   0 |   0 |   0 |   0 |
| Bass       |   6 |   5 |   4 |   2 |   0 |   0 |   0 |   0 |   0 |   0 |
| Treble     |   0 |   0 |   0 |   0 |   0 |   1 |   2 |   4 |   5 |   6 |
| Vocal      |  −2 |  −2 |  −1 |   0 |   2 |   4 |   4 |   2 |   0 |  −1 |
| Rock       |   5 |   4 |   3 |   1 |  −1 |  −1 |   1 |   3 |   4 |   5 |
| Pop        |  −1 |   0 |   2 |   3 |   4 |   3 |   1 |   0 |  −1 |  −1 |
| Electronic |   5 |   4 |   1 |   0 |  −2 |   1 |   0 |   1 |   4 |   5 |
| Hip hop    |   6 |   5 |   2 |   3 |  −1 |  −1 |   1 |   0 |   2 |   3 |
| Jazz       |   3 |   2 |   1 |   2 |  −2 |  −2 |   0 |   1 |   2 |   3 |
| Classical  |   4 |   3 |   2 |   1 |  −1 |  −1 |   0 |   2 |   3 |   4 |
| Acoustic   |   4 |   3 |   2 |   1 |   1 |   1 |   2 |   2 |   3 |   2 |

## How the settings combine

`SoundEffectsSettings.resolve(profileEq)` turns the settings into what is applied:

1. **Curve.** The active listening profile's own EQ curve (from the hearing check) when it has one (WH-092),
   sampled at the ten band centres; otherwise the preset's, or the user's curve.
2. **Bass boost.** Each band gets `bassBoostDb × 1 / (1 + (f / 120 Hz)²)`: almost all of it at 31 Hz, half at
   120 Hz, under a fifth at 250 Hz, nothing audible in the treble.
3. **Clamp.** Curve plus boost stays within ±18 dB per band.
4. **Gain.** Extra volume goes before the limiter, which keeps every boost from clipping.

## Boosts and the dose

Effects are **not** counted in the dose, by the product owner's decision for #40. Instead, whenever the largest
boost (the highest band gain, if positive, plus extra volume) reaches **3 dB**, the screen warns that the level at
the ear rises and that the dose does not include it (WH-094). Below 3 dB the change is within the margin of the
output-level estimate ([hearing-safety.md](hearing-safety.md)). ADR-0010 records this as a departure from the
safety-first policy that the deciders still have to sign off.

## Reaching other apps

| Source                   | Covers                                               | Needs                                                                                                          |
| ------------------------ | ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Announced sessions       | Players that announce their session (Spotify, VLC)   | Nothing                                                                                                        |
| The audio service's dump | Every media player (media, game and untagged usages) | `DUMP`, granted once over ADB; checked on Android 16, older dumps without sessions fall back to announced ones |
| Global mode              | All the phone's audio at once                        | A phone that honours effects on the output mix                                                                 |

With the dump available it is the whole truth: an announced session missing from it has ended and is dropped. The
screen shows the ADB command until the permission is granted, and what it allows: seeing which apps play, never
what they play.

If another equaliser app takes a session with a higher priority, the screen names the app that lost its effects;
they come back as soon as the other app lets go (WH-095).

## Lifetime

Effects only last while the app's process holds them. A special-use foreground service holds them while effects are
on, with a notification naming the apps that have effects and a "Turn off" action. The service starts from the
screen, comes back after a reboot or an app update when effects were on (WH-096), and is restarted by the system if
killed. Turning effects off releases every effect, leaving other apps' sound as it was.

## Limits

- Not verified on a real phone yet: what Spotify and YouTube Music sound like, global mode, and playback that the
  phone offloads to a hardware decoder, which skips software effects on some phones.
- Surround uses `Virtualizer`, deprecated since Android 16; phones that drop it hide the control.
- Slowed and nightcore are out: changing speed cannot be done live on another app's stream.
- Desktop has no effects yet (`UnsupportedSoundEffects`); it would need a PipeWire filter chain or a Windows APO.
