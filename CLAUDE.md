# CLAUDE.md

Guidance for AI coding assistants working in this repository. Humans: see [CONTRIBUTING.md](CONTRIBUTING.md).

## Project

WaveHeaven Lambda: safe-listening app that adapts media volume to ambient noise, capped by a WHO/NIOSH exposure
dose. Kotlin Multiplatform + Compose Multiplatform. Targets: Android (primary) and desktop (Windows, Linux).
No iOS. Read [docs/architecture.md](docs/architecture.md) before adding modules or moving code.

## Commands

```bash
./gradlew check                    # all gates: tests, detekt, ktlint, Android Lint (run before declaring done)
./gradlew desktopTest              # fast JVM unit + UI tests
./gradlew :core:domain:desktopTest # one module
./gradlew spotlessApply            # fix Kotlin formatting
./gradlew koverVerify              # domain coverage ≥ 80 %
./gradlew :apps:desktop:run        # run desktop app
./gradlew :apps:android:assembleDebug
npm run format                     # Prettier for md/yml/json
```

## Rules

- `core:domain` is pure Kotlin: no Android, Compose, I/O or platform imports. Explicit API mode; KDoc on public API.
  New domain logic ships with unit tests in `commonTest`.
- Compiler warnings are errors; fix the cause, do not suppress without a comment explaining why.
- New screens follow the pattern in `docs/architecture.md` (Contract → ViewModel → Route + stateless Screen → Koin
  module), modelled on `feature:exposure`. Features never depend on each other.
- No hard-coded UI strings: add them to the feature's `composeResources/values/strings.xml` **and**
  `values-es/strings.xml`.
- Versions only in `gradle/libs.versions.toml`; build configuration only in `build-logic/` convention plugins.
- Never record, store or transmit raw audio. Persist aggregates only ([ADR-0007](docs/adr/0007-privacy-on-device-audio.md)).
- Volume logic must respect the dose ceiling ([ADR-0006](docs/adr/0006-adaptive-volume-policy.md)); dose maths is
  documented in [docs/domain/hearing-safety.md](docs/domain/hearing-safety.md).
- Commits and PR titles: `<gitmoji> <type>(<scope>): <subject>`, lower-case imperative subject. Allowed scopes are in
  `commitlint.config.js`. Work on a branch and open a PR; never push to `main`.
- Significant or hard-to-reverse decisions get an ADR in `docs/adr/`.
