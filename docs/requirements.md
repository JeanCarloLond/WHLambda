# Requirements

The single source of truth for what WaveHeaven Lambda promises. It was written after the Phase 0 spikes, so it
only promises what the platforms allow ([spike #2](spikes/android-background-audio.md),
[spike #3](spikes/mic-calibration.md), [spike #4](spikes/desktop-app-volume.md)).

## How to read this document

- **IDs are stable.** `WH-0xx` are functional MVP requirements, `WH-1xx` non-functional requirements and `WH-2xx`
  requirements of later phases. An ID is never reused or renumbered; a dropped requirement stays in the table,
  struck through, with the reason.
- **Acceptance criteria use [EARS](https://alistairmavin.com/ears/)** (Easy Approach to Requirements Syntax):

  | Pattern      | Template                                        |
  | ------------ | ----------------------------------------------- |
  | Ubiquitous   | The app shall …                                 |
  | Event-driven | **When** _trigger_, the app shall …             |
  | State-driven | **While** _state_, the app shall …              |
  | Unwanted     | **If** _condition_, **then** the app shall …    |
  | Optional     | **Where** _feature is present_, the app shall … |

- **Priority** follows MoSCoW: _Must_ ships in the MVP, _Should_ ships in the MVP unless it blocks the release,
  _Could_ ships if there is time.
- **Done** means verified as stated in the _Verified by_ column: an automated test, a demo on a real device, or an
  inspection of code or configuration. Each GitHub issue links to the IDs it implements.
- "The app" means the Android app unless a requirement names another platform. "Dose ceiling", "active standard"
  and "window" are defined in [hearing-safety.md](domain/hearing-safety.md) and
  [volume-controller.md](domain/volume-controller.md).

## Personas

| Persona                      | Context                                                                                                                                           | What they need from us                                                         |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| **Valentina**, 20, commuter  | Student. 1 h 30 min a day on buses and the metro with Bluetooth earbuds and Spotify. Turns the volume up to beat the engine and forgets it there. | Music that stays audible on the bus and comes back down when she gets off.     |
| **Andrés**, 27, gym and runs | Trains five times a week with loud gym music on top of his own. Wired sports headphones.                                                          | To know how much of his weekly budget a session costs, without being nagged.   |
| **Laura**, 34, office worker | Listens on her PC most of the day at low level, and on her phone on the way home. Cares about privacy.                                            | Trust that nothing is recorded; one weekly dose across both devices (Phase 3). |

## Functional requirements (MVP, Phase 1)

### Ambient metering

| ID     | Requirement                                                                                                                                   | Priority | Issue   | Verified by                                 |
| ------ | --------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------- | ------------------------------------------- |
| WH-001 | **While** adaptive volume is running and media is playing, the app shall measure the A-weighted ambient level on the device once per second.  | Must     | #9, #10 | Unit tests (`LevelMeter`), device demo      |
| WH-002 | The app shall reduce microphone samples to levels in memory and shall never write, record or transmit audio.                                  | Must     | #9      | Inspection (ADR-0007 checklist), unit tests |
| WH-003 | **While** no media is playing, the app shall release the microphone, and **when** playback starts again it shall resume metering.             | Must     | #10     | Device demo                                 |
| WH-004 | **Where** the user has calibrated the microphone against a reference meter, the app shall apply the user's offset instead of the default one. | Could    | #3      | Unit test                                   |

### Adaptive volume

| ID     | Requirement                                                                                                                                                                              | Priority | Issue   | Verified by                     |
| ------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------- | ------------------------------- |
| WH-010 | **When** the 3 s ambient Leq plus the profile's target margin is at least 3 dB away from the estimated output level, the app shall move the media volume one step towards that target.   | Must     | #8, #10 | Unit tests (`VolumeController`) |
| WH-011 | The app shall never raise the media volume above the dose ceiling: the highest level sustainable for 1 h with the remaining budget of the active standard, and never less than 70 dB(A). | Must     | #8      | Unit tests                      |
| WH-012 | **If** the volume is above the dose ceiling and the user has not changed it in the last 10 minutes, **then** the app shall lower it to the ceiling at the next tick.                     | Must     | #8, #10 | Unit tests, device demo         |
| WH-013 | **When** the user changes the volume by any means, the app shall stop automatic adjustments on that output for 10 minutes.                                                               | Must     | #8, #10 | Unit tests, device demo         |
| WH-014 | **If** the dose ceiling prevents reaching the target margin, **then** the app shall say so and suggest noise-cancelling or a quieter place.                                              | Must     | #8, #10 | Unit test, UI test              |
| WH-015 | The app shall keep volume state separately for each output device (speaker, wired, each Bluetooth device).                                                                               | Must     | #8      | Unit test                       |
| WH-016 | The app shall never unmute the media volume.                                                                                                                                             | Must     | #8      | Unit test                       |

### Background operation

| ID     | Requirement                                                                                                                               | Priority | Issue | Verified by            |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------- | -------- | ----- | ---------------------- |
| WH-020 | **When** the user starts adaptive volume from a visible screen, the app shall run it in a `microphone` foreground service.                | Must     | #10   | Device demo            |
| WH-021 | **While** the service runs, the app shall show a persistent notification with the ambient level, today's dose and Pause and Stop actions. | Must     | #10   | Device demo, unit test |
| WH-022 | **When** the user taps Stop, the app shall release the microphone and end the service within 1 s.                                         | Must     | #10   | Device demo            |
| WH-023 | **While** paused, the app shall neither meter nor change the volume, and **when** resumed it shall continue with the same profile.        | Must     | #10   | Unit test, device demo |
| WH-024 | The app shall never start the service without a user action, including after a reboot or an app update.                                   | Must     | #10   | Inspection (manifest)  |

### Output level and exposure

| ID     | Requirement                                                                                                                                                             | Priority | Issue | Verified by               |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ----- | ------------------------- |
| WH-030 | The app shall estimate the A-weighted level at the ear from the volume index, the output device's volume curve and a headphone profile.                                 | Must     | #11   | Unit tests                |
| WH-031 | **If** the headphone model is unknown, **then** the app shall assume a conservative profile that overestimates rather than underestimates the level.                    | Must     | #11   | Unit test                 |
| WH-032 | Every estimated output level the app shows shall come with its margin of error.                                                                                         | Must     | #11   | UI test                   |
| WH-040 | **While** media is playing and the service runs, the app shall store one aggregate per minute: start time, ambient Leq, estimated output Leq and listening time.        | Must     | #12   | Unit tests (in-memory DB) |
| WH-041 | The app shall compute today's NIOSH dose (since local midnight) and the active WHO standard's dose over the last 7 days from the stored aggregates, after restarts too. | Must     | #12   | Unit tests                |
| WH-042 | The app shall exclude exposure and hearing data from cloud backup and device-to-device transfer.                                                                        | Must     | #12   | Inspection (backup rules) |
| WH-043 | **When** the user deletes their data from settings, the app shall erase every stored aggregate.                                                                         | Must     | #12   | Unit test, UI test        |
| WH-044 | The app shall delete aggregates older than 30 days.                                                                                                                     | Should   | #12   | Unit test                 |

### Dashboard

| ID     | Requirement                                                                                              | Priority | Issue | Verified by        |
| ------ | -------------------------------------------------------------------------------------------------------- | -------- | ----- | ------------------ |
| WH-050 | The app shall show today's NIOSH dose and the 7-day WHO dose as percentages.                             | Must     | #13   | UI test            |
| WH-051 | The app shall show, for each, the listening time left at the most recent output level.                   | Must     | #13   | Unit test, UI test |
| WH-052 | The app shall show the daily dose of the last 7 days as a chart, with the same values available as text. | Should   | #13   | UI test            |

### Listening profiles

| ID     | Requirement                                                                                                                                     | Priority | Issue | Verified by        |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ----- | ------------------ |
| WH-060 | The app shall offer four built-in profiles (commute, gym, office, home), each with a target margin over noise and a minimum and maximum volume. | Must     | #14   | Unit test          |
| WH-061 | The app shall let the user create, edit and delete their own profiles; built-in profiles cannot be deleted.                                     | Must     | #14   | Unit test, UI test |
| WH-062 | **When** the user selects a profile, the volume loop shall use it from the next tick.                                                           | Must     | #14   | Unit test          |
| WH-063 | The dose ceiling shall apply whatever the profile; a profile's maximum volume can only lower it.                                                | Must     | #14   | Unit test          |
| WH-064 | **Where** a profile has an EQ curve, the app shall store it with the profile as frequency (Hz) and gain (dB) points.                            | Could    | #14   | Unit test          |

### Onboarding, consent and settings

| ID     | Requirement                                                                                                                                                    | Priority | Issue | Verified by        |
| ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ----- | ------------------ |
| WH-070 | **When** the app is opened for the first time, it shall explain in plain language what it measures and what it never records before asking for the microphone. | Must     | #15   | UI test            |
| WH-071 | The app shall ask for explicit consent to process health data (Ley 1581 de 2012), store the consent version and date, and not meter without it.                | Must     | #15   | Unit test, UI test |
| WH-072 | **When** the consent text changes version, the app shall ask for consent again before metering.                                                                | Must     | #15   | Unit test          |
| WH-073 | **When** the user revokes consent in settings, the app shall stop metering and offer to delete their data.                                                     | Must     | #15   | UI test            |
| WH-074 | **If** the microphone permission is denied, **then** the app shall keep working as a dose explorer with manual input and explain how to grant it.              | Must     | #15   | UI test            |
| WH-075 | The app shall let the user choose the weekly standard, WHO adult (default) or WHO sensitive, during onboarding and in settings.                                | Must     | #15   | Unit test, UI test |

### Dose alerts

| ID     | Requirement                                                                                                                      | Priority | Issue | Verified by             |
| ------ | -------------------------------------------------------------------------------------------------------------------------------- | -------- | ----- | ----------------------- |
| WH-080 | **When** the dose of a window reaches 50 %, 80 % or 100 %, the app shall notify the user, at most once per threshold per window. | Must     | #16   | Unit tests              |
| WH-081 | Alerts shall be visual and haptic, never sound-only.                                                                             | Must     | #16   | Inspection, device demo |
| WH-082 | Each alert shall say what to do: lower the volume, take a break or use noise-cancelling.                                         | Must     | #16   | Unit test               |
| WH-083 | The app shall let the user turn off each threshold, or all alerts, in settings.                                                  | Should   | #16   | Unit test, UI test      |

## Non-functional requirements

| ID     | Quality         | Requirement                                                                                                                                                                                                        | Verified by                           |
| ------ | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------- |
| WH-100 | Responsiveness  | The volume loop shall lower an over-ceiling volume within 2 s, make the first comfort step within 4 s of a sustained noise change, and space comfort raises at least 3 s apart.                                    | Unit tests, device demo               |
| WH-101 | Battery         | With adaptive volume running and music playing, the app shall use at most 2 % of the battery per hour on the reference device (Redmi Note 14), measured over at least 1 h unplugged with the screen off.           | Device measurement before release     |
| WH-102 | Offline         | Every MVP feature shall work without a network connection. The MVP shall not declare the `INTERNET` permission.                                                                                                    | Inspection (merged manifest)          |
| WH-103 | Accessibility   | The app shall meet WCAG 2.2 AA: text contrast ≥ 4.5:1 and UI contrast ≥ 3:1, a label on every interactive element, touch targets ≥ 48 dp, usable at 200 % font scale, and no information by colour or sound alone. | UI tests, TalkBack demo               |
| WH-104 | Privacy         | Audio buffers shall hold at most 100 ms of samples, live only in memory, and never leave the process. Only aggregates are stored.                                                                                  | Inspection, unit tests                |
| WH-105 | Languages       | Every user-facing text shall be available in English and Spanish, following the system language.                                                                                                                   | UI tests, inspection                  |
| WH-106 | Platform        | The app shall run on Android 10 (API 29) and later, and keep metering with the screen off and in Doze for at least 1 h.                                                                                            | Device demo                           |
| WH-107 | Storage         | 30 days of aggregates shall take less than 5 MB.                                                                                                                                                                   | Unit test (row size × 43 200 minutes) |
| WH-108 | Maintainability | `core:domain` and `core:audio` shall keep line coverage ≥ 80 %; CI shall block merges on tests, static analysis, formatting and lint.                                                                              | CI                                    |

## Later phases

Recorded now so the MVP does not close doors; refined when each phase starts.

| ID     | Requirement                                                                                                                                            | Phase | Issue |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ----- | ----- |
| WH-200 | On Windows and Linux, the desktop app shall adapt the volume of the user's chosen music application with the same policy and dose tracking as Android. | 2     | #17   |
| WH-201 | **While** the desktop app runs, it shall show a tray icon with the metering state and dose, and pause and stop actions.                                | 2     | #17   |
| WH-202 | Pushing a version tag shall publish a signed Android build and Windows and Linux installers with a changelog.                                          | 2     | #6    |
| WH-210 | **Where** the user opts in, the app shall sync per-minute aggregates, never audio, to their account so the weekly dose covers all their devices.       | 3     | #18   |
| WH-211 | The backend shall make it impossible for one user to read or write another user's rows (row-level security, tested).                                   | 3     | #18   |
| WH-212 | **When** the user deletes their account, the backend shall delete all their data.                                                                      | 3     | #18   |
| WH-220 | The app shall offer a hearing check per ear at 6 to 8 frequencies from 250 Hz to 8 kHz, labelled as not a diagnosis.                                   | 3     | #19   |
| WH-221 | **If** the hearing check shows asymmetric or poor results, **then** the app shall advise seeing an audiologist.                                        | 3     | #19   |
| WH-222 | The app shall store hearing-check results only on the device, as health data, and offer them as an EQ compensation curve.                              | 3     | #19   |

## Traceability

| Issue | Requirements                                                                     |
| ----- | -------------------------------------------------------------------------------- |
| #8    | WH-010 – WH-016                                                                  |
| #9    | WH-001, WH-002, WH-104                                                           |
| #10   | WH-001, WH-003, WH-010, WH-012 – WH-014, WH-020 – WH-024, WH-100, WH-101, WH-106 |
| #11   | WH-030 – WH-032                                                                  |
| #12   | WH-040 – WH-044, WH-107                                                          |
| #13   | WH-050 – WH-052, WH-103                                                          |
| #14   | WH-060 – WH-064                                                                  |
| #15   | WH-070 – WH-075                                                                  |
| #16   | WH-080 – WH-083                                                                  |
| #17   | WH-200, WH-201                                                                   |
| #6    | WH-202                                                                           |
| #18   | WH-210 – WH-212                                                                  |
| #19   | WH-220 – WH-222                                                                  |
