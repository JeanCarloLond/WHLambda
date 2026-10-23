# 0007. Audio is processed on-device only

- **Status:** Accepted
- **Date:** 2026-10-23
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

The app listens to the microphone, possibly in the background, and stores listening habits and hearing-check
results. Hearing data is health data — sensitive data under Colombia's Ley 1581 de 2012 and special-category data
under the EU GDPR. The original app requested the microphone without explaining what happened to the audio.

## Decision

- Microphone samples are reduced to sound levels **in memory, on the device**, and discarded immediately. Audio is
  never written to disk, never recorded, never sent anywhere.
- The only persisted measurements are **aggregates** (e.g. per-minute Leq, estimated output level, dose).
- Before the microphone permission prompt, the app explains in plain language what is measured and what is not,
  and asks for explicit consent to process health data. Consent is versioned and revocable.
- Metering is visibly indicated (notification on Android, tray icon on desktop) and can be stopped in one action.
- Health data is excluded from Android cloud backup and device-to-device transfer.
- Cloud sync is opt-in and limited to aggregates; users can export and delete their data.

## Consequences

- Features that would need raw audio (e.g. content classification) are out of scope unless they also run
  entirely on-device.
- A privacy policy compliant with Ley 1581 (and GDPR if relevant) is required before any public release.
