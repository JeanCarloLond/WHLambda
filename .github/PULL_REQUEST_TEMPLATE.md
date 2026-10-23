<!--
PR title = squash commit message. Use the convention, e.g.:  ✨ feat(core): add NIOSH dose calculator
-->

## What & why

<!-- One or two sentences. Link the issue it resolves. -->

Closes #

## How

<!-- Key decisions, trade-offs, anything a reviewer should look at first. -->

## Evidence

<!-- Tests added, screenshots/recordings for UI changes, device(s) used for audio changes. -->

## Checklist

- [ ] The PR title follows `<gitmoji> <type>(<scope>): <subject>`
- [ ] Tests cover the new behaviour (domain logic: unit tests are mandatory)
- [ ] `./gradlew check` passes locally
- [ ] User-facing strings are in `composeResources` with `en` and `es` translations
- [ ] Docs/ADRs updated if behaviour, architecture or a convention changed
- [ ] No audio is recorded or leaves the device; only aggregated levels are stored
