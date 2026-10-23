# 0004. Supabase as backend

- **Status:** Accepted
- **Date:** 2026-10-23
- **Deciders:** Jean Carlo Londoño, Alejandro Garcés

## Context

Lambda is local-first: metering, the volume loop and dose tracking work offline on the device. A backend is needed
for accounts, opt-in sync of aggregated statistics across devices, and later a web dashboard. The original project
suffered from authorization bugs (endpoints writing to the wrong user) and hand-rolled security.

## Decision

Use **Supabase**: managed PostgreSQL, Auth, Row Level Security (RLS) and Edge Functions, accessed through the
official multiplatform client `supabase-kt`.

- Every table carries `user_id` with RLS policies `auth.uid() = user_id`, so isolation between users is enforced
  by the database, not by application code.
- Schema changes are versioned SQL migrations in the repository (`supabase/migrations/`).
- Only aggregated data is synced ([ADR-0007](0007-privacy-on-device-audio.md)).

## Alternatives considered

| Option                                   | Pros                                                                                           | Cons                                                             |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| **Supabase** (chosen)                    | Auth, RLS and Postgres out of the box; official Kotlin client; low lock-in (standard Postgres) | Hosted service and free-tier limits; RLS policies must be tested |
| Firebase                                 | Mature mobile SDKs                                                                             | NoSQL fits relational stats poorly; stronger lock-in             |
| Own API (Ktor/Django/FastAPI) + Postgres | Full control; learning value                                                                   | Auth, hosting, security and ops become our job                   |

## Consequences

- No backend code to host in the early phases.
- RLS policies need automated tests before sync ships.
- Keys live in `local.properties` / CI secrets; only the public anon key is ever embedded in apps.
