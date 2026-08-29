<div align="center">

# 🧩 Puzzle Agenda

**A self-hosted personal day & free-time planner.**
Fit your activities into the free slots of your day — like puzzle pieces.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![CI](https://github.com/alebuc/puzzle-agenda/actions/workflows/ci.yml/badge.svg)](https://github.com/alebuc/puzzle-agenda/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/alebuc/puzzle-agenda?include_prereleases)](https://github.com/alebuc/puzzle-agenda/releases)
[![Java](https://img.shields.io/badge/Java-25-orange)](backend/pom.xml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?logo=springboot&logoColor=white)](backend/pom.xml)
[![Vue 3](https://img.shields.io/badge/Vue-3-42b883?logo=vuedotjs&logoColor=white)](frontend/package.json)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-336791?logo=postgresql&logoColor=white)](#)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

[Features](#-features) •
[Quick Start](#-quick-start) •
[Architecture](#-architecture) •
[Development](#-development) •
[Roadmap](#-roadmap) •
[Contributing](#-contributing)

</div>

---

> [!NOTE]
> Puzzle Agenda is under active development and has not reached a stable `1.0` release yet. Expect breaking changes between minor versions.

## 📸 Screenshots

<!-- TODO: replace with real screenshots once the day timeline UI stabilizes -->
<div align="center">
  <em>Screenshots coming soon — day timeline, activity backlog, routine template.</em>
</div>

## ✨ Features

- **📋 Activity backlog** — capture things you want to do with an estimated duration, priority, and optional category (sport, errands, leisure, chores…). Unplanned activities wait in the backlog until you schedule them.
- **📅 Day timeline** — view any day as a chronological timeline of time blocks, with visible gaps and distinct styles for routine, constrained, and free-time blocks.
- **🔁 Routine template** — define your everyday routine (sleep, meals, hygiene) once; each new day is pre-filled with it. Pre-filled blocks are ordinary blocks: edit, move, or delete them per day without touching the template.
- **⛔ Overlap-proof by design** — two blocks can never overlap on the same day. Enforced both in the domain layer *and* at the database level (PostgreSQL range types + exclusion constraints).
- **🌙 Midnight-spanning sleep** — sleep is modeled as one continuous interval across two days, not two disconnected blocks.
- **🗓️ Two-week horizon** — plan from today up to 13 days ahead; a bounded horizon keeps planning realistic.
- **🏠 Self-hosted & privacy-first** — your data stays on your server. No account with a third party, no Google dependency, no telemetry.

### Design goals

- **F-Droid-compatible Android app** (Capacitor) — no proprietary push services; local notifications only, with optional [UnifiedPush](https://unifiedpush.org/)/ntfy support planned as a latency accelerator, never a correctness requirement.
- **Spec-driven development** — every feature goes through a full [Spec Kit](https://github.com/github/spec-kit) cycle (constitution → spec → plan → tasks → implement) before any code is written.

## 🚀 Quick Start

### Docker Compose (recommended)

```yaml
# docker-compose.yml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: puzzle_agenda
      POSTGRES_USER: puzzle_agenda
      POSTGRES_PASSWORD: change-me
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U puzzle_agenda"]
      interval: 5s
      retries: 10

  app:
    image: ghcr.io/alebuc/puzzle-agenda:latest # TODO: publish image
    depends_on:
      db:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/puzzle_agenda
      SPRING_DATASOURCE_USERNAME: puzzle_agenda
      SPRING_DATASOURCE_PASSWORD: change-me
    ports:
      - "8080:8080"

volumes:
  db-data:
```

```bash
docker compose up -d
```

Then open <http://localhost:8080>. Database schema is managed automatically by Flyway migrations on startup.

### Kubernetes

Helm chart / manifests: *planned*. In the meantime the container above runs fine as a standard Deployment + PostgreSQL StatefulSet (or an operator like CloudNativePG).

## 🏗 Architecture

Monorepo with a hexagonal (ports & adapters) backend and a Vue frontend:

```
puzzle-agenda/
├── backend/                 # Java 25 · Spring Boot 4 · Maven multi-module
│   ├── domain/              # Entities, value objects, domain services, ports
│   │                        #   → ZERO framework dependencies (no Spring/JPA)
│   ├── application/         # Use cases — depends only on domain
│   ├── infrastructure/      # REST controllers, persistence adapters,
│   │                        #   Flyway migrations — implements domain ports
│   └── bootstrap/           # Spring Boot entry point & wiring
├── frontend/                # Vue 3 (Composition API) · Vite
└── specs/                   # Spec Kit artifacts (spec / plan / tasks per feature)
```

Dependency direction is enforced **at compile time** by Maven module boundaries:

```
bootstrap → infrastructure → application → domain
```

Key invariants:

| Invariant | Enforcement |
|---|---|
| No overlapping time blocks | Domain rules **+** PostgreSQL `tsrange` `EXCLUDE` constraint |
| 5-minute time granularity | Database-level check |
| Reproducible schema | Flyway migrations only — manual edits prohibited |
| Framework-free domain | Maven module with no Spring/JPA/Jackson dependency |

The project's full set of non-negotiable principles lives in the [constitution](.specify/memory/constitution.md).

## 🛠 Development

### Prerequisites

- JDK 25, Maven
- Node 20+, npm
- Docker (local PostgreSQL + Testcontainers)

### Run locally

```bash
# 1. Database
docker run -d --name puzzle-agenda-db \
  -e POSTGRES_DB=puzzle_agenda -e POSTGRES_USER=puzzle_agenda -e POSTGRES_PASSWORD=puzzle_agenda \
  -p 5432:5432 postgres:16

# 2. Backend (from backend/)
mvn install -DskipTests
mvn -pl bootstrap spring-boot:run       # → http://localhost:8080

# 3. Frontend (from frontend/, separate shell)
npm install
npm run dev                             # → proxies /api/* to :8080
```

> Re-run `mvn install -DskipTests` after changing `domain`, `application`, or `infrastructure` — `spring-boot:run` on `bootstrap` alone resolves siblings from `~/.m2`, not from source.

### Tests

```bash
# Backend — domain & application: plain JUnit 5 + AssertJ + Mockito (no Spring context)
#           infrastructure: Spring Boot Test + Testcontainers (real PostgreSQL)
cd backend && mvn verify

# Frontend — Vitest + Vue Test Utils
cd frontend && npm test
```

### Workflow

- **PR-only** — direct pushes to `main` are blocked by a GitHub ruleset *and* a `.githooks/pre-push` hook. Enable local hooks once: `git config core.hooksPath .githooks`
- **Spec first** — no implementation without a completed spec/plan/tasks cycle under `specs/`.
- **Versioning** — semantic versioning with release candidates (`0.x.0-rc.N`) between releases, enforced by CI.

## 🗺 Roadmap

- [x] Daily planning core — backlog, time blocks, routine template, day timeline
- [ ] Free-slot suggestion for pending activities
- [ ] Weekly view
- [ ] Docker image & Helm chart publication
- [ ] Android app (Capacitor) with local notifications
- [ ] Optional UnifiedPush/ntfy sync acceleration (`PUSH_ENABLED=false` by default)

See [open issues](https://github.com/alebuc/puzzle-agenda/issues) for details and discussion.

## 🤝 Contributing

Contributions are welcome! Please:

1. Read the [constitution](.specify/memory/constitution.md) — it supersedes all other docs where conflicts exist.
2. Open an issue to discuss non-trivial changes before starting.
3. Follow the Spec Kit cycle for new features.
4. Submit a PR — all checks must pass and branches must be up to date before merging.

## 📄 License

Distributed under the [MIT License](LICENSE).

---

<div align="center">
  <sub>Built as both a useful tool and a learning vehicle for spec-driven development.</sub>
</div>