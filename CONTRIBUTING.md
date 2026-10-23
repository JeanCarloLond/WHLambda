# Contributing to WaveHeaven Lambda

Thanks for helping! This guide explains how we work so that every change is easy to review, test and ship.

## Ground rules

1. **Every change starts from an issue** (feature, bug, spike or task) and lands through a pull request.
2. **`main` is always releasable.** CI must be green before merging; nobody pushes to `main` directly.
3. **Domain logic is test-first.** Anything in `core:domain` ships with unit tests.
4. **Privacy by design.** Audio never leaves the device and is never recorded; only aggregated levels are stored.
5. **Decisions are written down.** Architectural choices get an [ADR](docs/adr/).

## Workflow

```
issue ──► branch ──► commits ──► pull request ──► review + CI ──► squash merge ──► branch deleted
```

1. Pick or open an issue and assign yourself.
2. Create a branch from an up-to-date `main`:
   `<type>/<issue-number>-<short-description>` → `feat/12-noise-meter`, `fix/31-dose-rounding`.
3. Commit in small, logical steps (see below). Hooks check format and messages for you.
4. Open a PR early (draft is fine). Fill in the template and link the issue with `Closes #12`.
5. At least one approval from the other maintainer, all CI checks green, conversations resolved.
6. **Squash and merge.** The PR title becomes the commit on `main`, so it must follow the convention too.

## Commit messages

We combine [Gitmoji](https://gitmoji.dev) (quick visual scan) with
[Conventional Commits](https://www.conventionalcommits.org) (machine-readable history, changelogs, semver):

```
<gitmoji> <type>(<scope>)<!>: <subject>

[optional body: what and why, wrapped at 100 columns]

[optional footer: Closes #12, BREAKING CHANGE: …]
```

- **Subject** in imperative mood, lower case, no trailing period: `add`, not `Added` or `Adds`.
- **`!`** after the scope (and a `BREAKING CHANGE:` footer) marks a breaking change.
- Both the `commit-msg` hook and CI validate this with [commitlint](commitlint.config.js).

### Types and their emoji

| Emoji | Type       | Use for                                                 |
| ----- | ---------- | ------------------------------------------------------- |
| ✨    | `feat`     | A new user-facing feature                               |
| 🐛    | `fix`      | A bug fix                                               |
| 🚑️    | `fix`      | A critical hotfix                                       |
| 🔒️    | `fix`      | A security or privacy fix                               |
| ♻️    | `refactor` | Code change that neither fixes a bug nor adds a feature |
| ⚡️    | `perf`     | Performance improvement                                 |
| 💄    | `style`    | UI and style-only changes                               |
| ✅    | `test`     | Adding or updating tests                                |
| 📝    | `docs`     | Documentation only                                      |
| 👷    | `ci`       | CI configuration                                        |
| 🔧    | `chore`    | Configuration, tooling                                  |
| ⬆️    | `chore`    | Dependency upgrades                                     |
| 🏗️    | `build`    | Build system, Gradle, convention plugins                |
| 🌐    | `feat`     | Internationalisation and translations                   |
| ♿️    | `feat`     | Accessibility                                           |
| 🔥    | `chore`    | Removing code or files                                  |
| ⏪️    | `revert`   | Reverting a previous commit                             |
| 🎉    | `chore`    | Beginning of a project or major milestone               |
| 💥    | any + `!`  | Breaking change                                         |

### Scopes

`core` · `domain` · `audio` · `data` · `ui` · `designsystem` · `android` · `desktop` · `web` · `build` · `ci` ·
`deps` · `docs` · `release` — the scope is optional; leave it out when a change spans the whole repo.

### Examples

```
✨ feat(core): add WHO and NIOSH exposure-dose model
🐛 fix(audio): clamp target volume to the remaining dose
♻️ refactor(ui): extract SafeTimeCard composable
✅ test(domain): cover Leq for mixed-duration segments
📝 docs: add ADR-0004 on Supabase
💥 feat(data)!: store exposure per minute instead of per session
```

## Code style and quality gates

| Gate              | Tool                              | Run locally                                 |
| ----------------- | --------------------------------- | ------------------------------------------- |
| Formatting        | ktlint via Spotless, Prettier     | `./gradlew spotlessApply`, `npm run format` |
| Static analysis   | detekt (+ Compose rules)          | `./gradlew detekt`                          |
| Compiler          | Kotlin, warnings are errors       | `./gradlew assemble`                        |
| Unit and UI tests | kotlin.test, Compose UI test      | `./gradlew desktopTest`                     |
| Coverage          | Kover, `core:domain` ≥ 80 % lines | `./gradlew koverVerify`                     |
| Android           | Android Lint, warnings are errors | `./gradlew :apps:android:lint`              |
| Everything above  |                                   | `./gradlew check`                           |

Conventions worth knowing:

- **Where code goes** is described in [docs/architecture.md](docs/architecture.md). When in doubt, push logic down
  into `core:domain` and keep composables thin.
- `core:domain` uses Kotlin **explicit API mode**: every public declaration needs a visibility modifier and should
  have KDoc.
- **No hard-coded user-facing text.** Add strings to `shared/src/commonMain/composeResources/values/strings.xml`
  and the Spanish translation in `values-es/`.
- **Tests** use backticked sentence names: ``fun `NIOSH halves the allowed time every 3 dB`()``.
- **Units in names** when a number has one: `levelDba`, `durationMinutes`, `exchangeRateDb`.

## Pull request reviews

Reviewers check, in order: correctness and tests → hearing-safety and privacy impact → architecture boundaries →
readability. Prefer suggestions over demands, and explain the _why_. Authors respond to every comment
(fix it, or explain why not).

## Architecture decisions

Significant or hard-to-reverse choices get an ADR in `docs/adr/`, numbered sequentially. Copy
[the template](docs/adr/0000-template.md), open it in the same PR as the change, and set its status to _Accepted_
when merged. Superseded ADRs are kept and marked as such.

## Reporting bugs and requesting features

Use the [issue templates](https://github.com/JeanCarloLond/WHLambda/issues/new/choose). Security issues go through
[private reporting](SECURITY.md), never public issues.
