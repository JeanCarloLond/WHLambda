# Volume controller

How [`VolumeController`](../../core/domain/src/commonMain/kotlin/com/waveheaven/lambda/core/domain/volume/VolumeController.kt)
turns ambient noise and the remaining exposure dose into volume changes. Policy: [ADR-0006](../adr/0006-adaptive-volume-policy.md).
Constraints from real devices: [spike #2](../spikes/android-background-audio.md).

## Inputs and outputs

Once per second the platform layer sends a `VolumeTick`:

| Field           | Meaning                                                |
| --------------- | ------------------------------------------------------ |
| `now`           | Monotonic time (only differences matter)               |
| `route`         | Current output, e.g. speaker or a Bluetooth headset    |
| `ambient`       | Calibrated ambient level of the last second, dB(A)     |
| `currentIndex`  | Volume index the system reports for that route         |
| `isMusicActive` | Whether something is playing                           |
| `doseConsumed`  | Share of the configured standard's budget already used |

It gets back a `VolumeDecision`: `newIndex` (or `null` to leave it), a `reason`, and the ambient Leq, target and
ceiling so the UI can explain what is happening.

An `OutputLevelModel` translates indices into estimated dB(A) at the ear. Until headphone profiles exist (#11),
`LinearOutputLevelModel` assumes a constant number of dB per step.

## Rules, in priority order

| #   | Condition                                        | Decision                                                      | Why                                                             |
| --- | ------------------------------------------------ | ------------------------------------------------------------- | --------------------------------------------------------------- |
| 1   | Nothing is playing                               | `NO_MUSIC`, hold                                              | No exposure, nothing to adapt                                   |
| 2   | Less than `settleTime` since our last change     | `SETTLING`, adopt the index                                   | Bluetooth absolute volume may round our value (spike D2)        |
| 3   | The index differs from what we last set          | `MANUAL_OVERRIDE` for `manualOverrideDuration`                | Someone else changed it; the reported source is unreliable (D3) |
| 4   | Index below `minIndex`                           | `MUTED`, hold                                                 | Never unmute automatically                                      |
| 5   | Index above the dose ceiling                     | `LOWER_TO_DOSE_CEILING`, drop at once                         | Safety beats comfort and rate limits                            |
| 6   | Fewer than `ambientWindowTicks` readings         | `WARMING_UP`, hold                                            | Avoid reacting to a single noisy second                         |
| 7   | Target outside the hysteresis band               | `RAISE_FOR_NOISE` / `LOWER_FOR_QUIET`, one step, rate-limited | Smooth, step-wise changes (D2)                                  |
| 8   | Target above the ceiling, already at the ceiling | `AT_DOSE_CEILING`, hold, `isTargetCappedByDose`               | UI suggests noise-cancelling or a break                         |
| 9   | Otherwise                                        | `WITHIN_DEAD_BAND`, hold                                      | Close enough                                                    |

During a manual override the controller never touches the volume, even above the ceiling. It flags
`isAboveDoseCeiling` so the app can warn instead of fighting the user's buttons.

State (last index set, when, override) is kept **per route** (D4); the ambient window is shared.

## Target and ceiling

- **Ambient Leq:** energy mean of the last `ambientWindowTicks` one-second readings.
- **Target:** `ambient Leq + targetSnrDb`. The target index is the lowest index whose level reaches it.
- **Ceiling:** the highest level that can be sustained for `planningHorizon` with the remaining budget,
  `L = Lc + q·log2(Tc · remaining / horizon)` ([hearing-safety.md](hearing-safety.md)), never below
  `minimumCeiling`. The ceiling index is the highest index at or below it.

## Default parameters

| Parameter                | Default   | Rationale                                                  |
| ------------------------ | --------- | ---------------------------------------------------------- |
| `standard`               | WHO adult | Leisure-listening reference (ADR-0006)                     |
| `targetSnrDb`            | 10 dB     | Comfortable margin over noise; profiles will tune it (#14) |
| `hysteresisDb`           | 3 dB      | ≈ 1.5 steps at 2 dB/step; avoids "pumping"                 |
| `ambientWindowTicks`     | 3         | 3 s Leq smooths speech and passing cars                    |
| `settleTime`             | 600 ms    | Bluetooth pushed back after 300 ms in spike #2             |
| `raiseInterval`          | 3 s       | Raising is slow and cautious                               |
| `lowerInterval`          | 1 s       | Lowering is quick                                          |
| `manualOverrideDuration` | 10 min    | Respect the user's choice for a while                      |
| `planningHorizon`        | 1 h       | The ceiling assumes the user keeps listening this long     |
| `minimumCeiling`         | 70 dB(A)  | Music never becomes inaudible; dose impact is negligible   |
| `minIndex`               | 1         | The controller never mutes                                 |

## Worked example

Speaker model: 16 steps, 2 dB/step, index 15 = 100 dB(A); WHO budget untouched.

1. Ambient 85 dB(A) → target 95 dB(A); ceiling ≈ 96 dB(A) (index 13).
2. Starting at index 5 (80 dB(A)), the controller raises one step every 3 s: 6, 7, … 12.
3. At index 12 (94 dB(A)) the target is 1 dB away, inside the 3 dB band, so it holds.
4. With 90 % of the weekly budget used, the ceiling drops to ≈ 86 dB(A) (index 8): the volume goes straight to
   8 and stays there with `AT_DOSE_CEILING` while the noise remains high.
