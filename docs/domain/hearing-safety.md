# Hearing safety: standards and the dose model

This note explains the maths implemented in
[`core:domain` › `exposure`](../../core/domain/src/commonMain/kotlin/com/waveheaven/lambda/core/domain/exposure/).
It is the reference to check the code against.

## Sound level

- A **sound pressure level** in dB SPL is `L = 10·log10(p² / p₀²)` with `p₀ = 20 µPa`.
- We work with **A-weighted** levels, dB(A), which approximate how loud the ear perceives a sound.
- What matters for damage is **energy over time**. The quantity that adds up is the mean-square pressure
  `p² = p₀² · 10^(L/10)` (Pa²), integrated over time: the **sound exposure** `E = Σ p²·t` (Pa²·h).

> A microphone gives **dBFS** (relative to the device's digital full scale), not dB SPL. Converting needs a
> per-device calibration offset, and the level that matters for the dose is the one **at the ear** (what the
> headphones play), not the ambient noise. Ambient noise drives the _volume policy_; the _dose_ tracks output.

## Equivalent continuous level (Leq)

The steady level that would carry the same energy as a varying one over the same time:

```
Leq = 10·log10( Σ tᵢ·10^(Lᵢ/10) / Σ tᵢ )
```

Loud moments dominate: one hour at 80 dB(A) plus one at 90 dB(A) gives Leq ≈ 87.4 dB(A), not 85.

## Dose standards

A standard is a **criterion level** `Lc` that may be sustained for a **reference period** `Tc`, and an
**exchange rate** `q`: every `q` dB above the criterion halves the allowed time.

```
T(L) = Tc / 2^((L − Lc) / q)          allowed time at level L
D    = Σ tᵢ / T(Lᵢ)                    dose; 1.0 = 100 % of the budget
```

| Standard (code) | Lc       | Tc   | q                      | Window | Source                                                                              |
| --------------- | -------- | ---- | ---------------------- | ------ | ----------------------------------------------------------------------------------- |
| `WHO_ADULT`     | 80 dB(A) | 40 h | 3.01 dB (equal energy) | week   | WHO-ITU _Safe listening devices and systems_, ITU-T H.870 (mode 1, ≈1.6 Pa²·h/week) |
| `WHO_SENSITIVE` | 75 dB(A) | 40 h | 3.01 dB                | week   | ITU-T H.870 mode 2, for children and sensitive users (≈0.51 Pa²·h/week)             |
| `NIOSH`         | 85 dB(A) | 8 h  | 3 dB                   | day    | NIOSH Recommended Exposure Limit (1998)                                             |

Notes:

- WHO/ITU defines the budget as energy (Pa²·h), which is exactly an exchange rate of `10·log10(2) ≈ 3.01 dB`.
  NIOSH uses a round 3 dB, so the two differ very slightly far from the criterion (at 100 dB(A): 15 min vs 15.2 min).
- ITU rounds the budgets to 1.6 and 0.51 Pa²·h; we derive them from `Lc` and `Tc` (1.600 and 0.506), well within
  measurement uncertainty.
- **OSHA's** 90 dB(A) / 5 dB rule is a legal workplace minimum, less protective than NIOSH; we do not use it.
- **WHO is the default** for leisure listening; NIOSH is offered because its daily window is intuitive.

### Quick reference (NIOSH)

| Level           | 85 dB(A) | 88  | 91  | 94  | 97     | 100    | 103     |
| --------------- | -------- | --- | --- | --- | ------ | ------ | ------- |
| Allowed per day | 8 h      | 4 h | 2 h | 1 h | 30 min | 15 min | 7.5 min |

## Output level at the ear

The dose must track what the headphones play, not the street (WH-030). No phone can measure that, so the app
estimates it from three things
([`CurveOutputLevelModel`](../../core/domain/src/commonMain/kotlin/com/waveheaven/lambda/core/domain/volume/CurveOutputLevelModel.kt),
[`HeadphoneProfile`](../../core/domain/src/commonMain/kotlin/com/waveheaven/lambda/core/domain/volume/HeadphoneProfile.kt)):

```
L_ear(index) = L_max(profile) + G(index)        G(max) = 0 dB, G(mute) = −96 dB
```

- **`L_max`**, the profile's maximum output: the A-weighted level at the ear at full volume with typical music. We use
  the level of EN 50332-1's programme-simulation noise, about 10 dB below a full-scale sine, because music is not
  played at full scale.
- **`G(index)`**, the attenuation at each volume index. On Android it comes from
  `AudioManager.getStreamVolumeDb(STREAM_MUSIC, index, device)`, which follows the device's own curve for the current
  output. Curves are far from linear: on the reference curve below, the top steps are 2 to 4 dB apart and the bottom
  ones more than 5 dB.
- **The margin of error**, shown next to every estimate (WH-032): the profile's margin plus the curve's.

### Profiles

| Profile                      | `L_max`                        | Margin | Why                                                                                                                                                 |
| ---------------------------- | ------------------------------ | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Unknown headphones (default) | 105 dB(A)                      | ± 8 dB | Above the 100 dB(A) cap EN 50332 sets in Europe, because devices elsewhere often exceed it. Overestimating only makes the ceiling stricter (WH-031) |
| Phone speaker                | 85 dB(A)                       | ± 6 dB | Phone speakers rarely exceed 80 dB(A) at 30 cm; holding the phone closer adds a few decibels                                                        |
| From a datasheet (wired)     | `S + 10·log10(1000·V²/Z) − 10` | ± 4 dB | `S` in dB SPL/mW, `Z` in Ω, `V` the source's full-scale RMS voltage (≈ 1 V for phones and laptops)                                                  |
| From a datasheet (per volt)  | `S_V + 20·log10(V) − 10`       | ± 4 dB | For datasheets that give sensitivity in dB SPL/V                                                                                                    |

Example: 100 dB SPL/mW, 32 Ω headphones on a 1 V source receive 31.25 mW at full scale, so
`L_max = 100 + 14.95 − 10 ≈ 105 dB(A)`.

### When the platform curve cannot be used

With **Bluetooth absolute volume** the phone does not attenuate at all (the headphones do), so Android reports a
flat curve. Then the app uses Android's reference media curve (AOSP `DEFAULT_MEDIA_VOLUME_CURVE`: −58 dB at 1 % of
the range, −40 dB at 20 %, −17 dB at 60 %, 0 dB at 100 %, linear in decibels in between) and adds **± 3 dB** to the
margin. A curve is considered unusable when it does not increase strictly with the index or spans less than 20 dB.

With unknown headphones on Bluetooth the estimate is therefore `105 dB(A) + G(index) ± 11 dB`. That is wide, and the
UI says so; letting users pick their headphones or enter a datasheet will narrow it.

## How the product uses this

1. **Dose ceiling:** the adaptive-volume policy never targets a level whose sustained use would exceed the
   remaining budget for the window ([ADR-0006](../adr/0006-adaptive-volume-policy.md), [volume-controller.md](volume-controller.md)).
2. **Feedback:** the app shows dose consumed (% of day/week) and time left at the current level.
3. **Alerts:** at 50 %, 80 % and 100 % of the weekly budget, visually and haptically, never only by sound
   ([`DoseAlertPolicy`](../../core/domain/src/commonMain/kotlin/com/waveheaven/lambda/core/domain/alert/DoseAlerts.kt)):
   - once per threshold per window; the weekly window rolls, so a threshold re-arms only after the dose has fallen
     5 points below it, as old listening leaves the 7 days;
   - when the dose jumps past several thresholds at once, only the highest alerts;
   - each alert says what to do: at 50 %, lower the volume; at 80 % and 100 %, take a break, or use noise-cancelling
     when the surroundings are above 75 dB(A), since the noise is what pushes the volume up;
   - each threshold can be switched off, or all muted, in Settings (WH-083).

## Disclaimer

These are population-level guidelines, not a diagnosis. WaveHeaven Lambda is not a medical device.
