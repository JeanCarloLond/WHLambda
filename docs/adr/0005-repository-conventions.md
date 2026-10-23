# 0005. Trunk-based workflow and gitmoji + Conventional Commits

- **Status:** Accepted
- **Date:** 2026-10-23
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

The original project was written in bursts before deadlines, without CI, tests or reviews, and its history was hard
to read. We want a history that documents itself and gates that catch problems before they reach `main`.

## Decision

- **Trunk-based development:** short-lived branches off `main`, merged by pull request with **squash merge**;
  branches are deleted after merge.
- **Commit format:** `<gitmoji> <type>(<scope>): <subject>` — Gitmoji for quick scanning, Conventional Commits for
  machine-readable history (changelog and semantic versioning). PR titles follow the same format because they
  become the squash commit.
- **Enforcement:** a `commit-msg` hook (lefthook + commitlint) locally, and commitlint on commits and PR titles in CI.
- **Quality gates in CI on every PR:** formatting, static analysis, compiler warnings as errors, unit and UI tests,
  coverage threshold on the domain, Android Lint, builds of both apps.
- **Licence:** Apache-2.0.

## Consequences

- Node.js is needed locally for hooks (not for building the apps).
- The gitmoji prefix is not understood by stock Conventional Commits tools; changelog generation must use a
  configurable tool (e.g. git-cliff) when we add releases.
