# 0003. Kotlin Multiplatform + Compose Multiplatform

- **Status:** Accepted
- **Date:** 2026-10-23
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

Targets are Android (primary) and desktop on Windows and Linux ([ADR-0002](0002-platforms-android-first.md)),
with a possible web companion later. The adaptive-volume loop must run inside an Android foreground service, i.e.
in native code that keeps working while the UI is not visible. A two-person team must maintain everything.

## Decision

Use **Kotlin Multiplatform** for all code and **Compose Multiplatform** for the UI:

- Domain logic is written once in `commonMain` and runs natively on Android (including the foreground service)
  and on the desktop JVM.
- One UI codebase for Android and desktop; Compose for Web (Wasm) remains an option.
- Platform code (`AudioRecord`, `AudioManager`, WASAPI, PipeWire) sits behind `expect`/`actual` or interfaces.

## Alternatives considered

| Option                                   | Pros                                                                                                        | Cons                                                                                                                                     |
| ---------------------------------------- | ----------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| **KMP + Compose Multiplatform** (chosen) | Native Android; one language; domain shared with the background service without duplication; stable desktop | Desktop app ships a JVM (larger installer); OS audio APIs on desktop need JNA/JNI                                                        |
| React Native (Expo) + Tauri              | Large ecosystem; TypeScript                                                                                 | iOS was its main advantage and is out of scope; the background loop would need Kotlin anyway, duplicating the algorithm in 2–3 languages |
| Tauri 2 everywhere (Rust + web UI)       | Small desktop binaries; Rust core compiles to Wasm                                                          | Android UI in a WebView; mobile support less mature; three languages (Rust, TypeScript, Kotlin)                                          |
| Flutter                                  | One UI for all targets                                                                                      | Background services still need native code; canvas-rendered web with weaker accessibility                                                |
| Fully native per platform                | Maximum control                                                                                             | Several codebases for a team of two                                                                                                      |

## Consequences

- The team must learn Kotlin, Compose and Gradle; this is also the language Android itself requires.
- Desktop OS integration (per-app volume) needs native bindings; validated in a Phase 0 spike.
- Builds use convention plugins in `build-logic/` to keep module configuration consistent.
