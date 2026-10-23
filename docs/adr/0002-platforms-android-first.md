# 0002. Android first, desktop second, no iOS

- **Status:** Accepted
- **Date:** 2026-10-23
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

The product's core promise is to adjust the volume of whatever the user is listening to (Spotify, YouTube Music,
podcasts…) according to ambient noise. Whether a platform allows that decides whether the product can exist there:

| Platform | Set volume of other apps                                                  | Mic in background                                                    | Notes                                                             |
| -------- | ------------------------------------------------------------------------- | -------------------------------------------------------------------- | ----------------------------------------------------------------- |
| Android  | Yes — `AudioManager.setStreamVolume(STREAM_MUSIC)`, no special permission | Yes, with a `microphone` foreground service and visible notification | Google Play requires declaring the foreground-service use         |
| Windows  | Yes — per application via WASAPI (`ISimpleAudioVolume`)                   | Yes                                                                  |                                                                   |
| Linux    | Yes — per stream via PipeWire/PulseAudio                                  | Yes                                                                  |                                                                   |
| iOS      | **No** public API; `outputVolume` is read-only                            | Limited                                                              | Also requires a paid Apple Developer account                      |
| Web      | **No** — a page controls only its own audio                               | Only while the tab is active                                         | This is why the original WaveHeaven could not deliver its promise |

The team has two Android phones and Windows and Linux machines, no Mac or iOS device, and no budget for an Apple
Developer account.

## Decision

1. **Android is the primary platform and the MVP.**
2. **Desktop (Windows, Linux) is the second platform**, as a native app (not a website), because only a native
   app can control other applications' volume.
3. **iOS is out of scope.** We will revisit only if both the platform constraint and the account cost change.
4. A **web** dashboard or browser extension may come later as a companion, never as the core experience.

## Consequences

- We can honestly promise automatic volume adaptation on every platform we ship.
- The stack must target Android and the desktop JVM well (see [ADR-0003](0003-kotlin-multiplatform.md)).
- Marketing and docs must state the supported platforms clearly.
