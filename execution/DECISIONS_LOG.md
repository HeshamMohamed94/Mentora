# Mentora — Implementation Decisions Log

Records implementation-time decisions that are consistent with the locked architecture but weren't fully pinned down at the architecture-doc level (e.g. "either approach is fine," or a local-environment fact discovered during implementation). This is **not** a place to record new architecture decisions that contradict locked docs — those require explicit user approval and, if accepted, an update to the relevant `architecture/adr/`.

Format: `D<n> — <date> — <decision>` with **Why** and **Impact**.

---

### D1 — 2026-09-05 — Local MongoDB is a native Windows service, not Docker

**Decision:** Backend connects to the developer's already-installed, already-running native MongoDB Community 8.3 Windows service at `mongodb://localhost:27017`. `infra/docker/docker-compose.yml` is not created/required for Phase 1.

**Why:** `mongosh --eval "db.runCommand({ping:1})"` succeeded against a `Running` `MongoDB` Windows service found at `C:\Program Files\MongoDB\Server\8.3\bin\mongod.exe`. `DEPLOYMENT.md § 1` explicitly states a native local install is an equally valid alternative to docker-compose.

**Impact:** No Docker dependency introduced in Phase 1. `MONGODB_URI` config default is `mongodb://localhost:27017/mentora`. If a docker-compose convenience file is wanted later (e.g. for CI), it can be added without changing application code.

---

### D2 — 2026-09-05 — No system Gradle/Kotlin CLI; vendor the Gradle wrapper

**Decision:** `backend/` will include a committed Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) targeting a Gradle version compatible with JDK 21 (Temurin, confirmed present) and Kotlin's current Ktor-compatible release.

**Why:** `where gradle` / `where kotlin` returned no results on the local machine — only a JDK is globally installed. The wrapper is the standard, reproducible way to build a Kotlin/Ktor project without assuming any global tool install.

**Impact:** All backend build/test commands in this project run via `./gradlew` (bash) or `.\gradlew.bat` (PowerShell), never a bare `gradle`/`kotlin` invocation.

---

### D3 — 2026-09-05 — Codex CLI available; will be used per the roadmap's delegation split

**Decision:** `codex-cli 0.151.0` is installed and available. Codex will be used via the `codex-delegate` skill for scoped, code-heavy Phase 1 module implementation (repositories, DTOs, routine CRUD, tests), per `IMPLEMENTATION_ROADMAP.md`'s Claude/Codex allocation. Claude retains direct authorship/review of: project scaffolding, plugin/config wiring, Auth & RBAC, the demo-checkout no-real-payment boundary, the quiz `isCorrect`-stripping projection, and the AI Tutor interface boundary.

**Why:** Matches the roadmap's explicit "reserve Codex for implementation-heavy, repetitive-code work; Claude for supervision/security-critical review" instruction, now that Codex availability is confirmed rather than assumed.

**Impact:** Expect PHASE_HANDOFF.md to list which Phase 1 modules were Codex-delegated vs. Claude-authored directly.

---

### D4 — 2026-09-05 — Phase 1 builds the `aitutor` module boundary only, no concrete provider call

**Decision:** Per the user's explicit Phase 1 scope instruction, Phase 1 implements the `aitutor` module's persistence (`aiConversations`/`aiMessages`), the `AiProvider` interface (per `AI_TUTOR_ARCHITECTURE.md` §§ 1, 3, ADR-009), the enrollment-gate/lesson-context resolution, and rate-limit wiring — bound to a stub/no-op `AiProvider` implementation (e.g. echoes or returns a fixed placeholder response). The concrete Anthropic Claude API call is deferred to Phase 6, even though the provider (Anthropic) is already architecturally approved (`ADR-009`, 2026-09-04).

**Why:** Directly instructed by the user: "Do NOT implement the concrete Anthropic integration yet unless strictly required for a backend interface boundary... AI Tutor concrete integration belongs to Phase 6," combined with "create backend abstractions/contracts required by future phases where the approved architecture requires it."

**Impact:** Phase 1's `/api/v1/ai-tutor/conversation/messages` endpoint will be functionally real (auth, rate-limit, enrollment gate, persistence, streaming plumbing) but will stream back stub content, not a real LLM completion. Phase 6 replaces only the `AiProvider` implementation binding — no route, schema, or client-contract change expected, consistent with the abstraction's purpose.

---

### D5 — 2026-09-05 — Backend integration tests run against the local native MongoDB instance, not Testcontainers, on this dev machine

**Decision:** `TESTING_STRATEGY.md § 1` specifies Testcontainers (MongoDB module) for backend integration tests. This machine has no Docker installed (`docker` is not on PATH, confirmed during environment audit). Testcontainers requires a container runtime and cannot function without one. Backend integration tests in Phase 1 instead run directly against the already-running local native MongoDB Community 8.3 service (`mongodb://localhost:27017`), using a dedicated `mentora_test` database that is dropped/recreated per test class to keep tests isolated and repeatable.

**Why:** A real, narrow implementation blocker (no container runtime on the local dev machine) — not a disagreement with the locked testing strategy. `DEPLOYMENT.md § 1` already establishes that a native local MongoDB install is an equally valid alternative to a Docker-based one for *running* the stack; this extends the same reasoning to *testing* the stack in the one specific place (Testcontainers) that has a hard Docker dependency. What is tested (real Mongo query/transaction/index behavior, not mocks) is unchanged — only the transport mechanism to reach a real MongoDB instance differs.

**Impact:** Backend test code must not declare a hard Testcontainers runtime requirement that fails outright when Docker is absent. If Docker later becomes available (this machine, or CI), Testcontainers can be reintroduced without changing what the tests assert — flagged here as a reasonable future improvement, not required for Phase 1 to pass its quality gate. This is called out explicitly in the Phase 1 report to the user, since it is a deviation from the locked `TESTING_STRATEGY.md`'s stated tooling (though not from its intent).
