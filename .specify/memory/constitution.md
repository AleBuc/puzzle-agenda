<!--
Sync Impact Report
- Version change: (none, template only) → 1.0.0
- Modified principles: N/A (initial ratification; all five slots filled for the first time)
  - I. Hexagonal Architecture with DDD (NON-NEGOTIABLE) — new
  - II. Data Integrity First — new
  - III. Test-Backed Development — new
  - IV. API Contract Clarity — new
  - V. Simplicity Over Speculation (YAGNI) — new
- Added sections:
  - Technology Constraints
  - Development Workflow
  - Governance (amendment procedure, versioning policy, compliance review)
- Removed sections: none (template placeholders only)
- Templates requiring follow-up: none — dependent templates (plan/spec/tasks/checklist)
  read this file at runtime and were not modified by this command.
- Deferred TODOs: none
-->

# Puzzle Agenda Constitution

Puzzle Agenda is a personal day and free-time planner. Users create activities
(with estimated duration, priority, and category) and place them on time
slots within their day, distinguishing constrained time (work, appointments)
from free time. The app suggests available free slots for pending
activities, offers daily and weekly views, and limits its planning horizon
to two weeks ahead.

## Core Principles

### I. Hexagonal Architecture with DDD (NON-NEGOTIABLE)
The backend MUST follow ports & adapters. The domain model (entities, value
objects, domain services, ports) MUST live in a dedicated Maven module with
ZERO framework dependencies — no Spring, no JPA, no Jackson. Use cases MUST
live in an application module that depends only on the domain module. All
technical adapters (REST, persistence, configuration) MUST live in an
infrastructure module. Dependency direction MUST be enforced at compile
time by Maven module boundaries: infrastructure → application → domain,
never the reverse.
**Rationale**: keeping the domain framework-free makes business rules
(overlap detection, slot suggestion) independently testable and reusable,
and prevents infrastructure churn (e.g., a persistence or web framework
upgrade) from leaking into planning logic.

### II. Data Integrity First
PostgreSQL is the single source of truth. Time-slot overlap prevention MUST
be enforced at the database level (range types + exclusion constraints) in
addition to domain-level rules. Schema changes MUST go through Flyway
migrations; manual schema edits are prohibited.
**Rationale**: a personal scheduler is only useful if it cannot produce
double-booked or corrupted time slots — the database is the last line of
defense even when domain logic has a bug, and Flyway migrations keep every
environment's schema reproducible and auditable.

### III. Test-Backed Development
Every feature MUST ship with tests before merge. Unit tests use JUnit 5
with AssertJ for assertions and Mockito for mocking; `@ParameterizedTest`
MUST be used whenever a rule is exercised against multiple input
combinations (e.g., overlap-detection edge cases, slot-suggestion
scenarios). The domain and application modules MUST be tested with plain
JUnit 5 — no Spring context. Infrastructure MUST be tested with Spring Boot
Test and Testcontainers for PostgreSQL. Frontend tests use Vitest and Vue
Test Utils. Business logic such as slot suggestion and overlap detection
requires unit tests before merge.
**Rationale**: keeping domain/application tests free of a Spring context
keeps the test suite fast and honest to the hexagonal boundary; Testcontainers
ensures infrastructure tests exercise real PostgreSQL behavior (including
the exclusion constraints from Principle II) instead of a mocked substitute.

### IV. API Contract Clarity
The REST API is the contract between backend and frontend. Endpoints MUST
follow consistent REST conventions and return meaningful HTTP status codes.
Breaking changes to the API MUST carry explicit justification recorded in
the change (e.g., plan or PR description).
**Rationale**: frontend and backend evolve as separate Maven/Vite build
units in the same monorepo; a clear, stable contract is what lets them
change independently without silent breakage.

### V. Simplicity Over Speculation (YAGNI)
The frontend uses Vue 3 with the Composition API and Vite. No
state-management library, no additional backend module, and no new
architectural abstraction may be introduced until a concrete, demonstrated
need exists.
**Rationale**: Puzzle Agenda's domain (personal scheduling for one user at a
time, two-week horizon) is bounded; speculative infrastructure (global
state stores, extra modules) adds maintenance cost without a corresponding
present-day requirement.

## Technology Constraints

- Backend: Java 25, Spring Boot 4.x, Maven multi-module layout — `domain`,
  `application`, `infrastructure`, `bootstrap`.
- Database: PostgreSQL 16+, schema managed exclusively through Flyway
  migrations.
- Frontend: Vue 3 (Composition API), Vite.
- Repository layout: a monorepo with `frontend/` and `backend/` folders at
  the root.

## Development Workflow

Each feature MUST go through the full Spec Kit loop in order: `/speckit-specify`
→ `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`.
Generated artifacts (spec, plan, tasks) MUST be reviewed by a human before
`/speckit-implement` is run.

## Governance

This constitution supersedes all other project practices and documentation
where a conflict exists. Amendments require:

1. A documented rationale for the change (what is changing and why).
2. An update to the version number following semantic versioning:
   - MAJOR: backward-incompatible governance or principle removal/redefinition.
   - MINOR: a new principle or section, or materially expanded guidance.
   - PATCH: clarifications, wording, or non-semantic refinements.
3. Propagation review of dependent templates (plan, spec, tasks, checklist)
   for consistency with the amended principles, performed the next time
   those templates are used.

All plans and reviews MUST verify compliance with this constitution.
Complexity that appears to violate Principle V (Simplicity Over
Speculation) MUST be explicitly justified in the plan before implementation
proceeds.

**Version**: 1.0.0 | **Ratified**: 2026-08-14 | **Last Amended**: 2026-08-14
