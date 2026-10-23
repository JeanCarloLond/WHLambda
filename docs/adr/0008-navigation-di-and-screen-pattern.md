# 0008. Navigation 3, Koin and a unidirectional screen pattern

- **Status:** Accepted
- **Date:** 2026-10-23
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

Phase 1 adds several screens (dashboard, profiles, onboarding) and non-UI components such as the Android
foreground service. Before they multiply we need one way to navigate, to provide dependencies and to structure a
screen, working identically on Android and desktop. The original WaveHeaven had business logic scattered in
templates; Lambda must keep screens thin and testable.

## Decision

1. **Navigation: Navigation 3** (`org.jetbrains.androidx.navigation3`, multiplatform). The back stack is a plain
   list of serializable `NavKey`s owned by the app shell; each entry gets its own saved state and ViewModel store
   (`lifecycle-viewmodel-navigation3`).
2. **Dependency injection: Koin.** Started once per process (`Application.onCreate` / desktop `main`) through
   `initKoin`, so services can use it as well as screens. Each feature exposes a Koin module; ViewModels are
   obtained with `koinViewModel()`.
3. **Screen pattern: unidirectional data flow.** Immutable `UiState`, `sealed interface` actions, a ViewModel that
   owns a single `StateFlow` and changes it only on actions, a stateful `Route` and a stateless `Screen`. Details
   and checklist in [architecture.md](../architecture.md#screen-pattern-unidirectional-data-flow).
4. **Feature modules.** One Gradle module per user-facing capability (`:feature:*`, convention plugin
   `whlambda.kmp.feature`). Features depend on `core:*` only; the shell wires navigation between them.

## Alternatives considered

| Option                       | Pros                                                                                                                                                                | Cons                                                                                        |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| **Navigation 3** (chosen)    | Back stack is ordinary state: easy to reason about and test; adaptive layouts; Google's direction; stable multiplatform release with ViewModel and Koin integration | Newer, fewer examples; on non-Android targets every key must be registered for state saving |
| Navigation Compose 2.x       | Mature, many examples                                                                                                                                               | Graph DSL and back stack hidden inside the library; superseded by Navigation 3              |
| Voyager / Decompose          | Popular in KMP                                                                                                                                                      | Third-party navigation model; Decompose has a steeper learning curve                        |
| **Koin** (chosen)            | Simple DSL, first-class KMP and Compose/ViewModel support, no code generation                                                                                       | Errors at runtime rather than compile time (mitigated by tests that start the real graph)   |
| Manual constructor injection | No library                                                                                                                                                          | Boilerplate grows with every feature; ViewModel factories by hand                           |
| Metro / kotlin-inject        | Compile-time safety                                                                                                                                                 | Code generation and a steeper learning curve for a two-person team                          |

## Consequences

- Adding a screen is mechanical (see the checklist); reviewers can check it against one reference
  (`feature:exposure`).
- `AppTest` starts the real Koin graph and navigates, catching missing bindings or keys.
- Desktop UI tests need `kotlinx-coroutines-swing` for `Dispatchers.Main`; the feature convention plugin adds it.
- Each new navigation key must also be registered in `NavigationConfig` for back-stack saving outside Android.
