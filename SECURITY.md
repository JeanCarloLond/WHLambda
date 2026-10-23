# Security Policy

WaveHeaven Lambda handles data that is sensitive by nature: microphone access, listening habits and hearing
results (health data under Colombia's Ley 1581 de 2012 and the EU GDPR). We take reports seriously.

## Supported versions

The project is pre-1.0. Only the latest commit on `main` and the latest release receive fixes.

## Reporting a vulnerability

**Do not open a public issue.** Use
[GitHub private vulnerability reporting](https://github.com/JeanCarloLond/WHLambda/security/advisories/new).
Include steps to reproduce, the affected version or commit, and the impact you expect.

We aim to acknowledge reports within 3 business days and to agree on a disclosure timeline with you.

## Our commitments

- Audio is processed on-device and is never recorded or uploaded; only aggregated sound levels are stored.
- No secrets in the repository: keys live in `local.properties`, environment variables or CI secrets.
- Health and exposure data are excluded from Android cloud backup and device transfer.
- Dependencies are monitored by Dependabot.
