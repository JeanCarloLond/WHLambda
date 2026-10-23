# 0006. Adaptive volume: target SNR capped by dose

- **Status:** Accepted
- **Date:** 2026-10-23
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

The original requirements said "raise the volume when noise rises", while the code lowered it. Neither was a
deliberate policy. Raising volume with noise keeps music audible but is exactly the behaviour that damages hearing;
only lowering it makes the app useless on a bus.

## Decision

The volume controller pursues two goals in strict priority order:

1. **Safety (hard constraint):** never target an output level whose sustained use would exceed the remaining
   exposure budget of the active standard (WHO by default) for the current window
   ([hearing-safety.md](../domain/hearing-safety.md)).
2. **Comfort (soft goal):** within that ceiling, keep the output a target **signal-to-noise ratio** above the
   ambient Leq (default ≈ +10 dB, configurable per listening profile).

To feel natural rather than "pumping", changes use a 1–3 s Leq window, a dead band (ignore changes below ~3 dB),
hysteresis, and a maximum rate of change. When the ceiling prevents reaching the target SNR, the app says so and
suggests noise-cancelling or a quieter spot instead of silently failing.

## Consequences

- The policy is pure logic and lives in `core:domain`, fully unit-tested independent of platforms.
- Estimating output level needs the device volume and a headphone sensitivity profile (default if unknown); the
  margin of error is shown, not hidden.
- Manual user adjustments are respected; learning from them (Phase 4) may move the SNR target, never the ceiling.
