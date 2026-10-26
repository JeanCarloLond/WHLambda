# Privacy

What WaveHeaven Lambda processes, where it lives and how users stay in control. The decisions behind it are in
[ADR-0007](adr/0007-privacy-on-device-audio.md). This is an engineering reference, not the public privacy policy
that Ley 1581 de 2012 requires before a public release.

## What is processed

| Data                                                                                    | When                                                      | Where it goes                                                                       | Kept                     |
| --------------------------------------------------------------------------------------- | --------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------ |
| Microphone samples                                                                      | Adaptive volume on, consent given, music playing (WH-003) | Reduced to one level per second in memory, then discarded (≤ 100 ms buffer, WH-104) | Never stored             |
| Ambient level per second                                                                | Same                                                      | Volume controller, in memory                                                        | Never stored             |
| Per-minute aggregate: start, listening time, estimated output Leq ± margin, ambient Leq | Same                                                      | App database on the device                                                          | 30 days (WH-044)         |
| Listening profiles, weekly standard, consent version and date                           | When the user sets them                                   | App database on the device                                                          | Until deleted or changed |

Never collected: audio recordings, speech, the titles or apps being played, location, contacts or identifiers.
Nothing leaves the device: the MVP does not even declare the `INTERNET` permission (WH-102).

The Android database lives in app-private storage, excluded from cloud backup and device-to-device transfer
(`data_extraction_rules.xml`, `backup_rules.xml`). On desktop it is in `%LOCALAPPDATA%\WaveHeaven Lambda` or
`~/.local/share/waveheaven-lambda`.

## Consent

Sound exposure says something about hearing health, which Ley 1581 treats as sensitive data, so processing it needs
explicit, informed and revocable consent (articles 5 and 6).

- **Before any prompt**, onboarding explains what is measured and what is never recorded (WH-070).
- **Explicit:** an unticked checkbox and an "Accept" button that stays disabled until the box is ticked. "Not now"
  is always available and the app keeps working as a dose explorer without it (WH-074).
- **Versioned:** the app stores the version of the text and the date (WH-071). Metering runs only with consent to the
  current version (`Consent.allowsMetering`). When the text changes in substance, `Consent.CURRENT_VERSION` is
  bumped and users are asked again (WH-072).
- **Revocable:** Settings › Consent › Withdraw stops metering and offers to delete the data in the same step (WH-073).
- The microphone permission is requested only after consent, and only from onboarding or an explicit user action.

### Consent text history

Change this table, the strings in `feature/onboarding` and `Consent.CURRENT_VERSION` together.

| Version | Date       | Change        |
| ------- | ---------- | ------------- |
| 1       | 2026-10-26 | First version |

Version 1, English:

> How much sound your ears receive says something about your hearing health, so Colombian law (Ley 1581 de 2012)
> treats it as sensitive health data. To measure it, WaveHeaven needs your explicit consent to process the loudness
> of your surroundings and of your music, only on this device and only to estimate your listening dose. Consent is
> optional: without it the app still works as a dose explorer. You can withdraw it at any time in Settings.
>
> ☐ I agree to WaveHeaven processing this data as described.

Version 1, Spanish:

> Cuánto sonido reciben tus oídos dice algo sobre tu salud auditiva, por eso la ley colombiana (Ley 1581 de 2012) lo
> trata como un dato sensible de salud. Para medirlo, WaveHeaven necesita tu consentimiento expreso para procesar el
> nivel de tu entorno y de tu música, solo en este dispositivo y solo para estimar tu dosis de escucha. Darlo es
> opcional: sin él la app sigue funcionando como explorador de dosis. Puedes retirarlo cuando quieras en Ajustes.
>
> ☐ Acepto que WaveHeaven procese estos datos como se describe.

## The user's rights

Ley 1581 (article 8) gives data subjects the right to know, update, rectify and delete their data and to revoke
consent. Since all data stays on the device, the app itself is how users exercise them:

| Right               | How                                                                                        |
| ------------------- | ------------------------------------------------------------------------------------------ |
| Know what is stored | Onboarding and Settings › Your listening data describe it; the dashboard shows it          |
| Update, rectify     | Profiles and the weekly standard are editable; aggregates are measurements, not statements |
| Delete              | Settings › Delete my listening data (WH-043); uninstalling removes everything              |
| Revoke consent      | Settings › Consent › Withdraw (WH-073)                                                     |

## Permissions

| Platform | Permission     | Why                         | Asked                                                                                     |
| -------- | -------------- | --------------------------- | ----------------------------------------------------------------------------------------- |
| Android  | `RECORD_AUDIO` | Ambient metering (ADR-0007) | In onboarding, after the explanation and consent, or when the user starts adaptive volume |
