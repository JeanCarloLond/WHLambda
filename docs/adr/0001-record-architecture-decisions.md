# 0001. Record architecture decisions

- **Status:** Accepted
- **Date:** 2026-10-23
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

The original WaveHeaven made key decisions implicitly (the web as platform, an ML model, the volume policy) and
several turned out infeasible or contradictory. Nobody could later say why a choice was made.

## Decision

We record every significant, hard-to-reverse decision as an Architecture Decision Record in `docs/adr/`, following
[the template](0000-template.md), reviewed in the same pull request as the change it motivates.

## Consequences

- New contributors (and future us) can learn the _why_ behind the codebase.
- Decisions are revisited deliberately, by superseding an ADR, rather than drifting.
- Small overhead per significant decision.
