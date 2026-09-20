# Phase 6 — AI Tutor Integration — Acceptance Criteria

**Status:** Draft, authored at Phase 6 kickoff (2026-09-20), before any Phase 6 code change. Derived from
already-locked authoritative sources, not invented from scratch — see "Authority sources" below. Reconciles
one stale table in `MASTER_IMPLEMENTATION_PLAN.md` (noted in § H).

## Authority sources (read in full before writing this checklist)

1. `architecture/AI_TUTOR_ARCHITECTURE.md` — the locked end-to-end flow, prompt construction, context
   boundaries, rate limiting, quick actions, language behavior.
2. `architecture/adr/ADR-009-ai-provider-abstraction.md` — provider abstraction decision + **Anthropic
   Claude API approved as the concrete provider** (product-owner sign-off, 2026-09-04).
3. `architecture/DATABASE_MODEL.md §§ 13-14` — `aiConversations`/`aiMessages` schema (already implemented).
4. `architecture/API_CONTRACT.md` — `GET /api/v1/ai-tutor/conversation`, `POST
   /api/v1/ai-tutor/conversation/messages` (already implemented, locked, not to change in Phase 6).
5. `architecture/AUTH_SECURITY.md §§ 7, 12` — rate-limit and logging-redaction rules already applied.
6. `architecture/DEPLOYMENT.md § 4` — `AI_PROVIDER_API_KEY` config convention, including the explicit
   "a personal dev key, or a stub/mock provider for offline development" fallback allowance.
7. `product/PRODUCT_SPEC.md § 11` — AI Tutor product scope, MVP capabilities, explicit out-of-scope list.
8. `design-system/COMPONENTS.md` (AITutorBubble/AITutorQuickAction) and `ux/UX_STATES.md § 10`
   (loading/thinking/error/empty treatment) — already implemented on Web/Android per Phase 2/4.
9. `execution/DECISIONS_LOG.md` D4, D30, D50-range (Web Task 9), D92 (Android Task 17), D96 — what was
   built against the stub provider and what was explicitly deferred to Phase 6.
10. `execution/PHASE_HANDOFF.md` (Phase 1/2/3/4 entries) — "Phase 6 only swaps the `AiProvider` Koin
    binding — no route/schema/contract change is expected."

**Do not re-derive scope from memory or re-litigate any of the above — this checklist assumes them as given.**

---

## A. Backend AI Tutor Service

| # | Criterion | Status |
|---|---|---|
| A1 | Real `AiProvider` implementation (`AnthropicAiProvider`) calling the Anthropic Messages API, bound via Koin in place of `StubAiProvider` when a key is configured | NOT STARTED |
| A2 | Request/response model unchanged: `AiCompletionRequest`/`AiToken` (provider-agnostic, per ADR-009) — no route/schema change | N/A (already satisfied by design; verify no drift) |
| A3 | Streaming: Anthropic's SSE stream is consumed and re-emitted as the existing `Flow<AiToken>`, preserving the existing chunked `text/plain` wire contract to clients (§ D4/D30) | NOT STARTED |
| A4 | Authenticated, rate-limited, CSRF-checked endpoint (already implemented) continues to pass with the real provider bound | PARTIAL — implemented against stub; needs re-verification with real provider |
| A5 | Input validation (length cap, paired courseId/lessonContextId) unchanged and still enforced ahead of any provider call | DONE (pre-existing, `AiTutorService.validatedContent`) |
| A6 | Timeout handling: a bounded per-request timeout to the provider, mapped to a client-visible retryable error, never a hang | NOT STARTED |
| A7 | Provider/network error handling: connection failure, non-2xx, malformed stream, and mid-stream failure all map to a defined error path that does NOT persist a partial/garbled assistant message (`AI_TUTOR_ARCHITECTURE.md § 1` step 10) | NOT STARTED |
| A8 | Safe credential configuration: `AI_PROVIDER_API_KEY`/`AI_PROVIDER_MODEL` read from `AppConfig` (already implemented); never logged; `redactedSummary()` continues to expose only `aiProviderConfigured: Boolean` | PARTIAL — config already wired; verify the new provider code path never logs the key |
| A9 | No secrets exposed to Web/Android/KMP clients — no route ever echoes the key or raw provider request/response metadata | DONE by construction (no client-facing route touches provider internals); re-verify after A1 lands |
| A10 | Logging: request-level log records `userId`/`requestId`/`lessonContextId`/token counts/latency, never full message content at INFO (`AUTH_SECURITY.md § 12`) | NOT STARTED (no such logging exists yet even for the stub — a real gap to close, not a regression) |
| A11 | Graceful no-key fallback: when `AI_PROVIDER_API_KEY` is unset, the app must still start and serve AI Tutor via a safe fallback (stub) rather than crash or silently 500 (`DEPLOYMENT.md § 4`'s explicit allowance) | NOT STARTED (decide + implement the selection rule) |

## B. Tutoring Flow

| # | Criterion | Status |
|---|---|---|
| B1 | User submits a question → real AI response streams back through the existing endpoint | PARTIAL — flow fully real end-to-end against the stub; needs the real provider swapped in |
| B2 | Loading ("Thinking") state | DONE (Web + Android, `UX_STATES.md § 10`, D50-range/D92) |
| B3 | Error/retry state, preserving the user's own message for retry | DONE (Web + Android; Android's retry-without-discarding-partial-text fix, D92 finding 5) |
| B4 | Empty/first-open state (lightweight welcome + quick actions, not a full `EmptyState`) | DONE (Web + Android) |
| B5 | Ongoing conversation / persisted history across sessions | DONE (backend persistence + client conversation fetch, all pre-existing) |
| B6 | Five quick actions wired: Explain this lesson, Summarize, Give me an example, Quiz me, What should I learn next? | DONE (Android confirmed D92; verify Web parity explicitly during integration verification) |

## C. Course Context

| # | Criterion | Status |
|---|---|---|
| C1 | General/global tutoring mode (no lesson context) | DONE |
| C2 | Lesson-context mode: `courseId`+`lessonContextId` resolved server-side to title/description, enrollment-gated | DONE (backend; Android wires it from Course Player's "Ask AI Tutor" link — confirmed full-tab-switch only, no docked panel, D92) |
| C3 | "What should I learn next?" scoped to the student's own enrollments only, never a platform-wide catalog scan | DESIGNED (`AI_TUTOR_ARCHITECTURE.md § 7`) — **NOT YET IMPLEMENTED**: `AiTutorService`'s current system prompt is a fixed persona string with no live enrolled-course-list injection (explicit, disclosed Phase 1 deferral, D30 finding 2) — real scope for this phase |
| C4 | No unnecessary application data sent to the AI provider (only resolved lesson title/description + trimmed history + the message) | DESIGN DONE; verify the real `AnthropicAiProvider` request builder doesn't smuggle anything extra |
| C5 | Known, accepted, out-of-scope gap: no docked/contextual AI Tutor panel inside Course Player on Web or Android (full-tab/full-screen chat only) | ACCEPTED GAP, carried forward — not a Phase 6 blocker unless the user says otherwise |

## D. Prompt Architecture

| # | Criterion | Status |
|---|---|---|
| D1 | System/instruction prompt owned and constructed entirely server-side | DONE (`AiTutorService.SYSTEM_PROMPT`) — content itself may be revisited for C3's enrolled-course-list addition |
| D2 | Product behavior encoded: read/explain-only, never claims to modify enrollment/progress/quiz/account state | DONE |
| D3 | Language handling: model responds in the language of the student's message; UI locale may be hinted for ambiguous short messages (`AI_TUTOR_ARCHITECTURE.md § 8`) | NOT STARTED — no locale hint currently passed into the prompt; decide at implementation time whether this is in-scope for MVP or an explicit deferral |
| D4 | Context boundaries: lesson content injected as clearly-delimited reference data, not as instructions (basic prompt-injection mitigation) | NOT STARTED (applies once the real provider request is built — the stub never had to do this) |
| D5 | Prompt ownership centralized in one place (`AiTutorService`); no client owns or can override the system prompt | DONE |

## E. KMP

| # | Criterion | Status |
|---|---|---|
| E1 | Shared models/repository/use cases/error mapping own AI Tutor client logic | DONE (`mobile/shared/.../aitutor/*`, Phase 3 Task 14) |
| E2 | No duplicated business logic independently in Android | DONE (Android consumes `MentoraSdk.aiTutor` exclusively, confirmed D92) |
| E3 | No contract change expected from the backend provider swap; if the real provider's error surface needs a new client-visible error case, it must be added to the shared `AiStreamResult` taxonomy, not worked around per-platform | TO VERIFY during integration — flag any real gap found, do not silently patch around KMP |

## F. Android

| # | Criterion | Status |
|---|---|---|
| F1 | AI Tutor integrated using Design System v1.3.2, existing nav, KMP shared layer, real local backend | DONE (Phase 4 Task 17, D92) |
| F2 | English/Arabic, LTR/RTL | DONE (D92 confirms Arabic + RTL verified) |
| F3 | Light/Dark | DONE (inherited from Task 8 component kit + Task 9 theme, not re-verified per-task historically — re-confirm during Phase 6 manual pass) |
| F4 | Genuine re-verification against the REAL provider (not just re-trusting the stub-era pass) | NOT STARTED — required manual verification step, § 11 of the kickoff brief |

## G. Website

| # | Criterion | Status |
|---|---|---|
| G1 | AI Tutor integrated using existing website architecture, Design System v1.3.2, real backend | DONE (Phase 2 Task 9, D50-range) |
| G2 | English/Arabic, RTL | DONE |
| G3 | Light/Dark where supported | DONE (inherited theming) |
| G4 | Genuine re-verification against the REAL provider | NOT STARTED |

## H. Security / Configuration

| # | Criterion | Status |
|---|---|---|
| H1 | AI credentials remain backend-only, never committed | PARTIAL — `.env.example` documents the key with an empty value (correct); confirm the real `.env` (gitignored) stays untracked throughout Phase 6 |
| H2 | No API keys shipped in Android/Web bundles | DONE by construction (no client code ever references a provider key) |
| H3 | Input length/range validation | DONE (`MAX_CONTENT_LENGTH = 4000`, pre-existing) |
| H4 | Basic abuse/cost controls | DONE (per-minute + daily rate limits, pre-existing, tested) |
| H5 | **Delegation-policy reconciliation:** `MASTER_IMPLEMENTATION_PLAN.md`'s Phase-1-era allocation table names this exact boundary ("AI Tutor enrollment-gate + key-handling: Codex implements, Claude reviews") as a Codex-implements item. Actual project practice shifted away from Codex-as-implementer from Phase 4 onward (tiered Opus review + implementer-level authorship, Codex reserved for sparing independent second opinions) per the user's current global routing policy. **Resolution: implementation follows current practice (Sonnet-level implementer + mandatory Opus review); one `codex-reviewer` independent second opinion is run specifically on the provider-boundary + key-handling code**, satisfying both the old table's spirit and the current "preserve Codex usage, use only where it materially reduces risk" instruction. Recorded, not silently overridden. | RESOLVED (policy decision, see `DECISIONS_LOG.md`) |

## I. Demo / Local Environment

| # | Criterion | Status |
|---|---|---|
| I1 | No production hosting requirements introduced | N/A by design — nothing in this phase touches deployment target |
| I2 | External AI provider dependency clearly documented | PARTIAL — `.env.example`/`DEPLOYMENT.md` already document it; add a short section to `backend/README.md` once the provider ships |
| I3 | If a real credential is unavailable: distinguish "implementation complete" vs. "structurally complete" vs. "runtime verification unavailable" — never fabricate a successful provider call | **ACTIVE CONSTRAINT for this phase** — `AI_PROVIDER_API_KEY` is currently unset in this environment; the final acceptance audit must state plainly which parts were runtime-verified vs. structurally-verified-only |

## J. Quality

| # | Criterion | Status |
|---|---|---|
| J1 | Tests: unit tests for `AnthropicAiProvider`'s request/response/error mapping against a mocked HTTP layer (no real network call in the default test suite); the existing `AiTutorIntegrationTest` suite (6/6, confirmed green on this session's baseline run) continues to pass unmodified in intent (may gain new cases, must not regress existing ones) | NOT STARTED |
| J2 | Error handling | tracked under A6/A7 |
| J3 | Documentation: `CURRENT_STATUS.md`, `DECISIONS_LOG.md`, `PHASE_HANDOFF.md`, `backend/.env.example` (already has the keys; confirm comments stay accurate once real), `backend/README.md` | NOT STARTED |
| J4 | Clean Git state at every checkpoint | tracked procedurally, not a one-time item |
| J5 | Existing Phase 1-5 behavior preserved — no regression outside the `aitutor` module and its direct config/DI wiring | tracked procedurally; verify via full existing backend suite before final acceptance, not just the `aitutor` slice |

---

## Baseline confirmed at kickoff (2026-09-20)

- `main` @ `e88bea9`, clean, in sync with `origin/main`.
- `AiTutorIntegrationTest`: **6/6 passing** (fresh run, this session, against `StubAiProvider`) — this is
  the regression baseline every later change in this module is compared against.
- `AI_PROVIDER_API_KEY` in `backend/.env`: **unset** — real-provider runtime verification is blocked until
  the user supplies a key; everything else in this checklist proceeds regardless, per § I3.

## Reconciliation note vs. `MASTER_IMPLEMENTATION_PLAN.md`

Its Phase 6 line ("M13 completion... the five quick actions, enrollment-gate + key-handling review") is
**preserved, not replaced** — this checklist is a strict elaboration of it, informed by the fact (not known
when that line was written) that the Web/Android/KMP client sides were already fully built against the stub
during Phases 2-4, so "wire AI Tutor chat UI on all three clients" is largely already done, not new work.
The one genuine reconciliation is § H5 above (Codex/Claude allocation for the enrollment-gate/key-handling
boundary specifically).
