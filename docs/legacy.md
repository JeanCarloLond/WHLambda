# Lessons from the original WaveHeaven

[WaveHeaven](https://github.com/AGR-23/WaveHeaven) (2025) was a Django web app plus a Chrome extension, built in
one semester. Lambda keeps its idea and its domain, not its code. This page records what we learned so we do not
repeat it.

## What was worth keeping

- **The problem:** people turn the volume up in noisy places and silently damage their hearing.
- **Exposure awareness:** tips and listening statistics were the best-received part.
- **Profiles per environment** (commute, gym, office) as one-tap presets.
- **A hearing check**, which can personalise the experience if done carefully.
- **The brand:** blue (trust, calm) and purple (music, wellbeing).

## What went wrong, and how Lambda answers it

| Problem in the original                                                | Root cause                                                                                                 | Lambda's answer                                                                                                  |
| ---------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| The "automatic volume" never changed what the user actually heard      | A web page cannot control other apps' audio; the workaround played a processed copy on top of the original | Native platforms that can set media volume ([ADR-0002](adr/0002-platforms-android-first.md))                     |
| "85 dB" thresholds compared against raw 0–255 analyser bytes           | No sound-level model; browser noise suppression on                                                         | Physical model in `core:domain`, calibration step, raw mic input ([hearing-safety.md](domain/hearing-safety.md)) |
| Volume went _down_ as noise went up, while requirements said _up_      | Policy never decided explicitly                                                                            | Written policy: target SNR capped by remaining dose ([ADR-0006](adr/0006-adaptive-volume-policy.md))             |
| An ML "risk model" that always predicted the same class                | Trained on a handful of synthetic rows                                                                     | Deterministic, standards-based rules; learning only from real user behaviour, later                              |
| Three incompatible equalizer models                                    | No shared domain model                                                                                     | One profile/EQ model in `core:domain`                                                                            |
| The microphone feature could not run in production                     | Deployed over plain HTTP                                                                                   | Native apps; HTTPS-only backend                                                                                  |
| Security gaps: CSRF protection disabled, stored XSS, secrets in source | No review gate, no tooling                                                                                 | CI gates, private-by-default data, secrets never in git, reviews required                                        |
| No tests; code written in bursts before deadlines                      | No CI, no definition of done                                                                               | Tests and CI from commit one; small PRs; DoD in every issue                                                      |
| Requirements with conflicting IDs, "done" by self-assessment           | Several documents, no evidence rule                                                                        | Issues with acceptance criteria; done = demonstrated                                                             |
| Feasibility discovered too late                                        | No technical spikes                                                                                        | Spikes before requirements (Phase 0)                                                                             |
