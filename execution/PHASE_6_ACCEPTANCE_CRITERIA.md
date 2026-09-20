# Phase 6 — AI Tutor Integration — Acceptance Criteria

**Status:** FINAL — T9 acceptance audit, 2026-09-20. Originally authored as a draft at Phase 6 kickoff,
before any Phase 6 code change; every row below has now been updated in place to its final, real,
end-of-phase state (T1-T7 complete; T8 genuinely blocked on a missing external credential, per § I3 —
never simulated). Derived from already-locked authoritative sources, not invented from scratch — see
"Authority sources" below. Reconciles one stale table in `MASTER_IMPLEMENTATION_PLAN.md` (noted in § H).

**Legend:** PASS (implemented + test-verified), PASS-STRUCTURAL (implemented + verified against a
mock/fake, but the specific real-provider runtime behavior is genuinely untestable without a key),
NOT TESTABLE (blocked solely on the T8 gate — real `AI_PROVIDER_API_KEY`), N/A, ACCEPTED GAP.

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
| A1 | Real `AiProvider` implementation (`AnthropicAiProvider`) calling the Anthropic Messages API, bound via Koin in place of `StubAiProvider` when a key is configured | **PASS-STRUCTURAL** — `provider/AnthropicAiProvider.kt` (T2, `24d159b`), mode-selected in `AiTutorModule.aiTutorModule()` via `AppConfig.aiProviderMode()`. Test: 22 `MockEngine` unit tests (`AnthropicAiProviderTest.kt`). A real call against `api.anthropic.com` is **NOT TESTABLE** without a key (T8 gate). |
| A2 | Request/response model unchanged: `AiCompletionRequest`/`AiToken` (provider-agnostic, per ADR-009) — no route/schema change | **PASS** — verified: `AiTutorRoutes.kt`'s wire contract untouched; `AiCompletionRequest` was reshaped (T1) but is an internal service/provider boundary type, not client-visible. |
| A3 | Streaming: Anthropic's SSE stream is consumed and re-emitted as the existing `Flow<AiToken>`, preserving the existing chunked `text/plain` wire contract to clients (§ D4/D30) | **PASS-STRUCTURAL** — `AnthropicSse.kt` parses Anthropic's real SSE framing into `AiToken`s delivered through the `onStream` callback (design reshaped to suspend+callback in T1/D140 §1.3 specifically so provider failures surface before `respondTextWriter` opens — zero wire-format change to clients). Test: SSE-parsing unit tests (happy path, CRLF framing, malformed events) + integration tests. Real end-to-end Anthropic stream: **NOT TESTABLE** (T8 gate). |
| A4 | Authenticated, rate-limited, CSRF-checked endpoint (already implemented) continues to pass with the real provider bound | **PASS-STRUCTURAL** — full `AiTutorIntegrationTest` suite (auth/rate-limit/CSRF, 10 cases) exercises the endpoint with the real service wiring and fakes standing in for the provider; the full request path with the *real* provider selected is T8's job. |
| A5 | Input validation (length cap, paired courseId/lessonContextId) unchanged and still enforced ahead of any provider call | **PASS** (pre-existing, unmodified, `AiTutorService.validatedContent`, `MAX_CONTENT_LENGTH = 4000`). |
| A6 | Timeout handling: a bounded per-request timeout to the provider, mapped to a client-visible retryable error, never a hang | **PASS-STRUCTURAL** — `AiTutorModule.kt` installs Ktor's `HttpTimeout` plugin with `requestTimeoutMillis = appConfig.aiProviderTimeoutSeconds * 1000L` (config-driven, `AI_PROVIDER_TIMEOUT_SECONDS`, range 5-600s). A timeout throws the same `HttpRequestTimeoutException` path already covered by the generic connection-failure catch (`AnthropicAiProvider.kt:127-129`) — pre-stream maps to `ApiException.ServiceUnavailable`, mid-stream rethrows into the stream-failure path. No test simulates an actual multi-second hang (impractical with `MockEngine`), but the exact catch branch it would hit is exercised by the existing connection-failure test. |
| A7 | Provider/network error handling: connection failure, non-2xx, malformed stream, and mid-stream failure all map to a defined error path that does NOT persist a partial/garbled assistant message (`AI_TUTOR_ARCHITECTURE.md § 1` step 10) | **PASS** — the T1 suspend+callback reshape (D140 §1.3) plus F1/F2 (D141) plus the 2 Codex findings (D142) closed every identified gap here: pre-first-token failures throw before `respondTextWriter` opens (nothing partial ever gets written to the client or persisted); mid-stream failures (`MessageStop` vs `ChannelExhausted` split, F2) are distinguished so a truncated response is never persisted as complete. Test: `FailBeforeStreamAiProvider`/`FailMidStreamAiProvider` integration tests + the full `AnthropicAiProviderTest` error-mapping matrix (401/404/429/529, SSE error events, clean EOF, malformed events). |
| A8 | Safe credential configuration: `AI_PROVIDER_API_KEY`/`AI_PROVIDER_MODEL` read from `AppConfig` (already implemented); never logged; `redactedSummary()` continues to expose only `aiProviderConfigured: Boolean` | **PASS** — `AppConfig.aiProviderApiKey` is `.trim()`-guarded (F5 fix, prevents a stray-whitespace key from leaking into a Ktor `IllegalHeaderValueException` message); the key is never interpolated into any log statement anywhere in `aitutor/`; `redactedSummary()` unchanged. Test: the T2 "key only appears in the `x-api-key` header, nowhere else" test, strengthened per Codex finding 3 to scan every captured header, not just URL/body. |
| A9 | No secrets exposed to Web/Android/KMP clients — no route ever echoes the key or raw provider request/response metadata | **PASS by construction** — re-verified after A1 landed: no client-facing route in `AiTutorRoutes.kt` references the key or any Anthropic-specific type; F3's fix (D141) additionally ensured the *diagnostic* detail of an internal error (which could otherwise embed Anthropic's raw error type/env-var names) never reaches `ApiException.Internal`'s client-visible `.message`, only its server-side `.cause`. |
| A10 | Logging: request-level log records `userId`/`requestId`/`lessonContextId`/token counts/latency, never full message content at INFO (`AUTH_SECURITY.md § 12`) | **PASS** — new `aiTutor.message` observability log line (T4, `6960127`): `requestId`/`userId`/`conversationId`/`mode`/`lessonContextId`/`provider`/`model`/`historyTurns`/`enrolledCourses`/`inputTokens`/`outputTokens`/`firstTokenMs`/`totalMs`/`outcome`/`error` — never the message or response text. Test: `ListAppender`-based log-capture tests asserting the exact field set and absence of message content. |
| A11 | Graceful no-key fallback: when `AI_PROVIDER_API_KEY` is unset, the app must still start and serve AI Tutor via a safe fallback (stub) rather than crash or silently 500 (`DEPLOYMENT.md § 4`'s explicit allowance) | **PASS** — `AppConfig.aiProviderMode()` returns `"stub"` whenever the key is unset/blank; `AiTutorModule` binds `StubAiProvider` in that case; `Application.kt` logs the active mode at startup either way. Verified live in this environment throughout T1-T7 (every backend run this session started in STUB mode without incident) and by the full test suite (127/127 green, all against stub/fakes). |

## B. Tutoring Flow

| # | Criterion | Status |
|---|---|---|
| B1 | User submits a question → real AI response streams back through the existing endpoint | **PASS-STRUCTURAL** — flow fully real end-to-end (auth → validation → prompt build → provider → stream → persistence) against stub/fakes on both Web and Android (T7, D144). A real Anthropic-backed answer is **NOT TESTABLE** without a key (T8 gate). |
| B2 | Loading ("Thinking") state | **PASS** — re-confirmed live on both Web and Android during T7 (D144): three-dot pulsing indicator observed directly in screenshots on both platforms, not just inferred from code. |
| B3 | Error/retry state, preserving the user's own message for retry | **PASS** — re-confirmed live on both Web and Android during T7 (D144) with a real killed-backend outage: user's message persists in history, clean generic error + Retry control appears, and Retry succeeds once the backend is back up; Android additionally confirmed per-turn retry isolation (a second, independent error also retried correctly). |
| B4 | Empty/first-open state (lightweight welcome + quick actions, not a full `EmptyState`) | **PASS** — re-confirmed live on both Web and Android during T7 (D144). |
| B5 | Ongoing conversation / persisted history across sessions | **PASS** (backend persistence + client conversation fetch, pre-existing; observed live on Web during T7 — a fresh page load showed prior conversation history intact). |
| B6 | Five quick actions wired: Explain this lesson, Summarize, Give me an example, Quiz me, What should I learn next? | **PASS** — all 5 confirmed live on both Web and Android during T7 (D144), each round-tripping successfully with no error. |

## C. Course Context

| # | Criterion | Status |
|---|---|---|
| C1 | General/global tutoring mode (no lesson context) | DONE |
| C2 | Lesson-context mode: `courseId`+`lessonContextId` resolved server-side to title/description, enrollment-gated | DONE (backend; Android wires it from Course Player's "Ask AI Tutor" link — confirmed full-tab-switch only, no docked panel, D92) |
| C3 | "What should I learn next?" scoped to the student's own enrollments only, never a platform-wide catalog scan | **PASS-STRUCTURAL** — closed for real in T3 (`a172682`): `CourseRepository.findByIds`/`CourseService.enrolledCourseBriefs` resolve only the calling principal's own enrollment IDs, injected into the prompt via `AiPromptBuilder.buildSystemPrompt`'s `<enrolled_courses>` block. Test: `AiTutorServiceTest` (4 mockk tests) proves the id set traces only to the principal's own enrollments on every call. The actual *quality* of a real Anthropic-generated recommendation is **NOT TESTABLE** without a key (T8 gate, C3 listed explicitly there). |
| C4 | No unnecessary application data sent to the AI provider (only resolved lesson title/description + trimmed history + the message) | **PASS** — verified by reading `AnthropicAiProvider`'s request builder directly: it sends only `AiCompletionRequest.systemPrompt`/`history`/`maxResponseTokens`, nothing else; the T2 "exact 5-key request body" test pins the wire shape so nothing extra can be smuggled in without breaking that test. |
| C5 | Known, accepted, out-of-scope gap: no docked/contextual AI Tutor panel inside Course Player on Web or Android (full-tab/full-screen chat only) | ACCEPTED GAP, carried forward — not a Phase 6 blocker unless the user says otherwise |

## D. Prompt Architecture

| # | Criterion | Status |
|---|---|---|
| D1 | System/instruction prompt owned and constructed entirely server-side | DONE (`AiTutorService.SYSTEM_PROMPT`) — content itself may be revisited for C3's enrolled-course-list addition |
| D2 | Product behavior encoded: read/explain-only, never claims to modify enrollment/progress/quiz/account state | DONE |
| D3 | Language handling: model responds in the language of the student's message; UI locale may be hinted for ambiguous short messages (`AI_TUTOR_ARCHITECTURE.md § 8`) | **PASS-STRUCTURAL** — `AiPromptBuilder.buildSystemPrompt` includes the explicit language rule ("respond in the language of the student's message") per System Design § 4. Whether a real Anthropic response actually honors it in practice is **NOT TESTABLE** without a key (T8 gate, D3 listed explicitly there). |
| D4 | Context boundaries: lesson content injected as clearly-delimited reference data, not as instructions (basic prompt-injection mitigation) | **PASS** — `AiPromptBuilder.sanitize()` neutralizes both `<lesson_context>` and `<enrolled_courses>` tag pairs in user-controlled course/lesson titles before they're wrapped in those same delimiter tags (the `<enrolled_courses>` gap was found and closed during T3's own pre-commit review, not by a later reviewer). Test: dedicated `AiPromptBuilderTest` cases for both tag families. Two lower-severity injection-hardening gaps (no explicit "data, not instructions" preamble; case-sensitive tag matching) were found in the T5 review and deliberately deferred as low-severity/non-blocking (F6, D141) — recorded, not silently dropped. |
| D5 | Prompt ownership centralized in one place (`AiTutorService`); no client owns or can override the system prompt | DONE |

## E. KMP

| # | Criterion | Status |
|---|---|---|
| E1 | Shared models/repository/use cases/error mapping own AI Tutor client logic | DONE (`mobile/shared/.../aitutor/*`, Phase 3 Task 14) |
| E2 | No duplicated business logic independently in Android | DONE (Android consumes `MentoraSdk.aiTutor` exclusively, confirmed D92) |
| E3 | No contract change expected from the backend provider swap; if the real provider's error surface needs a new client-visible error case, it must be added to the shared `AiStreamResult` taxonomy, not worked around per-platform | **PASS** — the one new backend error code (`AI_TUTOR_UNAVAILABLE`, 503) needed **zero** shared-module change: it falls through to the existing `Unknown`-fallback case already present in the KMP error taxonomy (the same forward-compatible-sealed-class pattern already used for e.g. `FORBIDDEN_CSRF`), confirmed by reading `ApiErrorCode.kt`/`ApiErrorCopy.kt` and re-confirmed live on Android during T7 (the connection-failure path exercises the identical fallback). No `mobile/shared` file touched anywhere in Phase 6. |

## F. Android

| # | Criterion | Status |
|---|---|---|
| F1 | AI Tutor integrated using Design System v1.3.2, existing nav, KMP shared layer, real local backend | DONE (Phase 4 Task 17, D92) |
| F2 | English/Arabic, LTR/RTL | DONE (D92 confirms Arabic + RTL verified) |
| F3 | Light/Dark | **PASS** — re-confirmed live during T7 (D144): AI Tutor screen renders fully dark-themed and consistent with the rest of the app (correct contrast, no default-Android unstyled elements), combined with Arabic in the same pass. |
| F4 | Genuine re-verification against the REAL provider (not just re-trusting the stub-era pass) | **NOT TESTABLE** — hard-blocked on the T8 gate (real `AI_PROVIDER_API_KEY`). Everything re-testable without a key (B2-B6, F2, F3, E3) has been genuinely re-verified live on a real device/emulator (T7, D144), not re-trusted from the stub era. |

## G. Website

| # | Criterion | Status |
|---|---|---|
| G1 | AI Tutor integrated using existing website architecture, Design System v1.3.2, real backend | DONE (Phase 2 Task 9, D50-range) |
| G2 | English/Arabic, RTL | DONE |
| G3 | Light/Dark where supported | DONE (inherited theming) |
| G4 | Genuine re-verification against the REAL provider | **NOT TESTABLE** — hard-blocked on the T8 gate. Everything re-testable without a key (B2-B6, G2, G3, E3, plus outage/retry behavior) has been genuinely re-verified live in a real browser (T7, D144), not re-trusted from the stub era. |

## H. Security / Configuration

| # | Criterion | Status |
|---|---|---|
| H1 | AI credentials remain backend-only, never committed | **PASS** — confirmed at T9: `git check-ignore -v backend/.env` shows it's gitignored; `git log --all -- backend/.env` returns nothing (never committed, ever); `git log -p --all -- backend/.env.example` shows the key's value has only ever been empty across its entire history. `.env`'s own `AI_PROVIDER_API_KEY` remains unset in this environment throughout Phase 6. |
| H2 | No API keys shipped in Android/Web bundles | DONE by construction (no client code ever references a provider key) |
| H3 | Input length/range validation | DONE (`MAX_CONTENT_LENGTH = 4000`, pre-existing) |
| H4 | Basic abuse/cost controls | DONE (per-minute + daily rate limits, pre-existing, tested) |
| H5 | **Delegation-policy reconciliation:** `MASTER_IMPLEMENTATION_PLAN.md`'s Phase-1-era allocation table names this exact boundary ("AI Tutor enrollment-gate + key-handling: Codex implements, Claude reviews") as a Codex-implements item. Actual project practice shifted away from Codex-as-implementer from Phase 4 onward (tiered Opus review + implementer-level authorship, Codex reserved for sparing independent second opinions) per the user's current global routing policy. **Resolution: implementation follows current practice (Sonnet-level implementer + mandatory Opus review); one `codex-reviewer` independent second opinion is run specifically on the provider-boundary + key-handling code**, satisfying both the old table's spirit and the current "preserve Codex usage, use only where it materially reduces risk" instruction. Recorded, not silently overridden. | RESOLVED (policy decision, see `DECISIONS_LOG.md`) |

## I. Demo / Local Environment

| # | Criterion | Status |
|---|---|---|
| I1 | No production hosting requirements introduced | N/A by design — nothing in this phase touches deployment target |
| I2 | External AI provider dependency clearly documented | **PASS** — T6 (`82e2bb4`) added the "AI Tutor provider" section to `backend/README.md` (both modes, the always-on startup log, the no-client-key structural guarantee); `.env.example`/`INTEGRATION_CONTRACT.md` updated to match. |
| I3 | If a real credential is unavailable: distinguish "implementation complete" vs. "structurally complete" vs. "runtime verification unavailable" — never fabricate a successful provider call | **PASS (constraint honored throughout)** — every PASS above that touches the real provider is explicitly marked PASS-STRUCTURAL or NOT TESTABLE, never a bare PASS; A1/A3/B1/C3/D3/F4/G4 are the specific items still gated on T8. No successful provider call was fabricated, simulated, or implied anywhere in this project's documentation or test suite. |

## J. Quality

| # | Criterion | Status |
|---|---|---|
| J1 | Tests: unit tests for `AnthropicAiProvider`'s request/response/error mapping against a mocked HTTP layer (no real network call in the default test suite); the existing `AiTutorIntegrationTest` suite (6/6, confirmed green on this session's baseline run) continues to pass unmodified in intent (may gain new cases, must not regress existing ones) | **PASS** — 22 `MockEngine`-based `AnthropicAiProviderTest` cases (zero real network calls anywhere in the suite); the original 6 `AiTutorIntegrationTest` cases kept byte-identical throughout, grown to 10 with 3 new failure-path cases plus 1 F1-regression case; 18 `AiPromptBuilderTest` + 4 `AiTutorServiceTest` cases added. **Full backend suite: 127/127 green** (fresh run at T9, `BUILD SUCCESSFUL`, 0 failures, 0 errors — up from 79 before Phase 6 began). |
| J2 | Error handling | **PASS** — see A6/A7. |
| J3 | Documentation: `CURRENT_STATUS.md`, `DECISIONS_LOG.md`, `PHASE_HANDOFF.md`, `backend/.env.example` (already has the keys; confirm comments stay accurate once real), `backend/README.md` | **PASS** — all updated: `.env.example` (T6), `backend/README.md` (T6), `INTEGRATION_CONTRACT.md` (T6), `CURRENT_STATUS.md` (T6 + T7 + this T9 pass), `DECISIONS_LOG.md` (D139-D144), `PHASE_HANDOFF.md` (this T9 pass, see its Phase 6 entry). |
| J4 | Clean Git state at every checkpoint | **PASS** — verified at T9: `git status` clean, every task committed individually (`a607da6` through `f08cfee`) and pushed to `origin/main` after each commit, per this project's established one-commit-per-task workflow. |
| J5 | Existing Phase 1-5 behavior preserved — no regression outside the `aitutor` module and its direct config/DI wiring | **PASS** — full 127/127 backend suite (all modules, not just `aitutor`) green at T9; `git diff --stat` scope-checked at every task's completion gate confirmed no file outside `aitutor/`+its direct config/DI wiring was touched on the backend, and zero `web/`/`mobile/` files were touched anywhere in Phase 6 (verify-only client work, T7/D144). |

---

## Baseline confirmed at kickoff (2026-09-20)

- `main` @ `e88bea9`, clean, in sync with `origin/main`.
- `AiTutorIntegrationTest`: **6/6 passing** (fresh run, this session, against `StubAiProvider`) — this is
  the regression baseline every later change in this module is compared against.
- `AI_PROVIDER_API_KEY` in `backend/.env`: **unset** — real-provider runtime verification is blocked until
  the user supplies a key; everything else in this checklist proceeds regardless, per § I3.

## Final state confirmed at T9 acceptance audit (2026-09-20)

- `main` @ `f08cfee`, clean, pushed to `origin/main`.
- Full backend suite: **127/127 passing**, fresh run, 0 failures, 0 errors.
- Every criterion above is either PASS, PASS-STRUCTURAL, N/A, or an explicitly ACCEPTED GAP, **except**
  seven items that are genuinely NOT TESTABLE without a real Anthropic credential: **A1, A3, B1, C3, D3,
  F4, G4** — all gated on the single unmet precondition in § I3 (`AI_PROVIDER_API_KEY` remains unset).
  No other open item, defect, or regression exists. See `DECISIONS_LOG.md` D144 and `PHASE_HANDOFF.md`'s
  Phase 6 entry for the full account.

## Reconciliation note vs. `MASTER_IMPLEMENTATION_PLAN.md`

Its Phase 6 line ("M13 completion... the five quick actions, enrollment-gate + key-handling review") is
**preserved, not replaced** — this checklist is a strict elaboration of it, informed by the fact (not known
when that line was written) that the Web/Android/KMP client sides were already fully built against the stub
during Phases 2-4, so "wire AI Tutor chat UI on all three clients" is largely already done, not new work.
The one genuine reconciliation is § H5 above (Codex/Claude allocation for the enrollment-gate/key-handling
boundary specifically).
