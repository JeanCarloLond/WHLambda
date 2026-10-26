# Roadmap

Each phase is a GitHub milestone; each item is an issue. What each phase promises, with stable IDs and
acceptance criteria, is in [requirements.md](requirements.md). A phase is done when its acceptance criteria are
demonstrated (tests or demo), not when the code exists.

## Phase 0 — Foundations ✅ in progress

Goal: an architecture that scales and a repository that enforces good practice from the first commit.

- Kotlin Multiplatform project (Android + desktop), convention plugins, version catalog
- Quality gates: detekt, ktlint, Android Lint, warnings-as-errors, Kover, unit + UI tests
- CI on every PR; commit and PR-title linting; Dependabot; issue and PR templates
- Domain: exposure-dose model (WHO, NIOSH) with tests
- Docs: README, CONTRIBUTING, architecture, ADRs
- **Spikes** (answer feasibility before we commit):
  - Android: foreground service with microphone + `AudioManager` volume while another app plays
  - Microphone calibration: dBFS vs a reference sound-level meter on our two phones
  - Desktop: per-app volume on Windows (WASAPI) and Linux (PipeWire) from the JVM

## Phase 1 — Android MVP

Goal: the original promise, working for real on Android.

- On-device ambient-noise meter (A-weighted Leq, no audio stored)
- Adaptive volume engine: target SNR, hysteresis, rate limiting, dose ceiling
- Background operation via foreground service, with clear notification and controls
- Listening profiles per environment (commute, gym, office, home)
- Local storage of per-minute aggregates; daily/weekly dose dashboard
- Onboarding with informed consent for microphone and health data
- Dose alerts (50 / 80 / 100 %)

## Phase 2 — Desktop

- Per-application volume control (Windows WASAPI, Linux PipeWire)
- Tray app with background metering
- MSI / DEB / RPM installers built in CI

## Phase 3 — Accounts, sync and hearing check

- Supabase auth and sync of aggregated stats across devices (opt-in)
- Hearing check (per ear, 6–8 frequencies, not a diagnosis) feeding a personal EQ curve
- Data export and account deletion

## Phase 4 — Beyond

- Learn the user's preferred margin from manual adjustments (bounded by the dose ceiling)
- Web dashboard (Compose for Web) and/or browser extension for tab audio
- Streaming integrations where their APIs allow it
- Group listening ("party mode") with a shared EQ
