# Hearing check

A five-minute check that compares how well the user hears different pitches in each ear (#19, WH-220 – WH-222).
It is **not a diagnosis**: the phone and headphones are not calibrated, so it only finds relative patterns, and the
app says so before and after. Code:
[`core:domain` › `hearing`](../../core/domain/src/commonMain/kotlin/com/waveheaven/lambda/core/domain/hearing/),
[`feature:hearingcheck`](../../feature/hearingcheck/).

## Procedure

1. **Reference level.** The user plays a 1 kHz tone in both ears at −20 dBFS and sets the system volume so it is clear
   and comfortable, never loud. That level is 0 dB for the rest of the check, and **no tone is ever louder**.
2. **Tones.** Right ear first, then left; in each, 1 kHz, 2, 3, 4, 6 and 8 kHz, then 500 and 250 Hz (eight
   frequencies from 250 Hz to 8 kHz). Each tone lasts 1 s with 20 ms fades (no click), after a random 0.8 – 2.5 s
   silence so its timing cannot be guessed. A press of "I hear it" counts while the tone plays and for 1.5 s after;
   presses at other times are ignored. The screen never shows when a tone plays.
3. **Staircase** (modified Hughson-Westlake, as in clinical audiometry):
   - start 30 dB below the reference;
   - heard → 10 dB quieter; missed → 5 dB louder;
   - the threshold is the quietest level heard on **two ascending presentations** (ones that follow a miss);
   - missed even at the reference: _not heard_;
   - after 14 tones a frequency stops and keeps the quietest level heard, so the whole check stays around five minutes.

## Reading the result

Thresholds are in dB below the reference: more negative is better. Only differences mean something.

| Assessment | Rule                                                                                                                                 | What the app says                                                           |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| Asymmetric | The ears differ by 15 dB or more at two or more frequencies (or one ear did not hear a tone the other did)                           | See an audiologist (WH-221)                                                 |
| Uneven     | Two or more frequencies are 25 dB or more worse than the user's best, or a tone was not heard at all (e.g. a noise notch near 4 kHz) | See an audiologist (WH-221)                                                 |
| Even       | Neither                                                                                                                              | Fine, but see an audiologist if you notice ringing, muffled hearing or pain |

## EQ compensation

The result can become the EQ curve of a new listening profile (WH-222). Per frequency, the gain is half the gap
between the average of both ears and the user's best threshold (the _half-gain_ rule of thumb from hearing-aid
fitting), from 0 to at most +12 dB; a tone not heard counts as the reference level. The curve uses the single
[`EqCurve`](../../core/domain/src/commonMain/kotlin/com/waveheaven/lambda/core/domain/profile/ListeningProfile.kt)
model of listening profiles. Applying EQ to other apps' audio is not possible on Android without system effects, so
for now the curve is stored with the profile (WH-064).

## Privacy

Results are health data: stored only on the device, in the app database excluded from backups, and deletable from
the hearing-check screen ([privacy.md](../privacy.md)). They are never synced.

## Limits

- Ambient noise masks quiet tones: the check asks for a quiet room and cannot verify it.
- Bluetooth headphones may apply their own processing; wired headphones are more reliable.
- Very quiet tones can fall below the device's noise floor at low system volumes, which would read as better hearing
  than reality only in absolute terms; the relative comparison still holds.
