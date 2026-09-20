# Phase 6 — AI Tutor Integration: Implementation Plan

**Status:** Authoritative task plan for Phase 6, derived by the `architect` subagent on 2026-09-20
from `execution/PHASE_6_SYSTEM_DESIGN.md` (the architecture), `execution/PHASE_6_ACCEPTANCE_CRITERIA.md`
(the acceptance bar, categories A-J), `architecture/AI_TUTOR_ARCHITECTURE.md`, ADR-009, and the real
source of `backend/src/main/kotlin/com/mentora/backend/aitutor/**`. Logged as
`DECISIONS_LOG.md` **D140**. Read this file before resuming any Phase 6 task — do not re-derive
acceptance criteria from memory.

Per-task status is tracked in `execution/CURRENT_STATUS.md`'s Phase 6 table; this file is the
acceptance detail that table points to. This is an execution planning document, not a locked doc;
amend it with a `DECISIONS_LOG.md` entry.

---

## 0. Repo state at plan time

- `main` @ `e88bea9`, clean, in sync with `origin/main` (per `PHASE_6_ACCEPTANCE_CRITERIA.md`'s
  kickoff baseline).
- `AiTutorIntegrationTest`: **6/6 passing** against `StubAiProvider`. **This is the regression
  baseline for every task below.**
- Backend `aitutor` module complete except the real provider: routes, persistence, enrollment gate,
  rate limiting, validation all shipped and tested (Phase 1 Task 18, D30).
- Web (Phase 2 Task 9), KMP shared (Phase 3 Task 14), Android (Phase 4 Task 17) AI Tutor surfaces:
  **built, shipped, live-verified against the stub.** Phase 6 treats them as **verify-only**.
- `backend/.env`'s `AI_PROVIDER_API_KEY`: **empty.** Live provider verification is blocked (I3).
- iOS: frozen/deferred. **No task in this plan touches `mobile/`.**
- Next decision id: **D140**.

## 1. Sequencing and execution rules

1. **Dependency order is hard:** provider-boundary foundation (T1) → real Anthropic provider (T2) →
   prompt + enrolled-course context (T3) → observability + failure-path tests (T4) → **review
   checkpoint (T5)** → docs/config (T6) → cross-platform verification (T7) → **[key gate]** → live
   runtime verification (T8) → acceptance audit + handoff (T9).
2. **Standing regression gate at every single task:**
   `./gradlew :backend:test` fully green, and `AiTutorIntegrationTest`'s **original 6 cases must
   pass unmodified in intent and assertion** (J1/J5). A task is not done if it changed one of them
   to make itself pass.
3. **One commit per task**, plus a `CURRENT_STATUS.md` continuity commit — the Phase 1-5 convention.
   Never one giant Phase-6 commit.
4. **Host tags:** **W** = fully completable and verifiable on this Windows host with no API key.
   **W-auth / K-verify** = authored and structurally verified here, only *proved* with a real key.
   **K** = requires the user to supply `AI_PROVIDER_API_KEY`. **M** = requires a human running the
   clients manually.
5. **The key boundary, stated once:**
   > **T1-T7 and T9's structural half execute now, in full, with no API key.** T8 is the single
   > genuinely blocked task. Nothing in T1-T7 is "waiting on the key"; nothing in T8 may be
   > simulated, inferred, or reported as passing on the strength of a mocked test (**I3** —
   > never fabricate a successful provider call).
6. **Secrets discipline, every task:** `backend/.env` stays gitignored and untracked; no key value
   ever appears in a commit, a log, a test fixture, a doc, or a terminal transcript pasted into a
   commit message (**H1**). Tests use an obviously-fake key literal such as
   `"test-key-not-a-real-credential"`.
7. Tasks are sized to be independently committable and reviewable — 9 units, matching this
   project's established granularity (Phase 4's 20 for a much larger phase; Phase 6 is small).

## 2. Task summary (dependency-ordered)

| # | Task | Host | Covers | Depends on |
|---|---|---|---|---|
| T1 | Provider-boundary foundation: interface reshape, route/service adaptation, 503 taxonomy, config, Koin selection + startup log | **W** | A2, A11, A6(partial), H1, J5 | — |
| T2 | `AnthropicAiProvider`: Ktor client, request builder, SSE parser, error mapping, timeouts + `MockEngine` unit suite | **W** | A1, A3, A6, A7, A8, C4, J1 | T1 |
| T3 | Prompt construction + enrolled-course injection + language rule | **W** | C3, C4, D1-D5 | T1 |
| T4 | Observability + failure-path integration tests + test seam | **W** | A7, A10, J1, J5 | T2, T3 |
| T5 | **Review checkpoint** — Opus review of the provider boundary + key handling, then one scoped `codex-reviewer` second opinion | **W** | H5, A8, A9, H2 | T4 |
| T6 | Documentation + config surface: `.env.example`, `backend/README.md`, `INTEGRATION_CONTRACT.md § 4/§ 8`, `DECISIONS_LOG.md` D140, `CURRENT_STATUS.md` | **W** | I2, J3, J4 | T5 |
| T7 | Cross-platform verification **without** a key: Android + Web against stub mode and against a forced provider failure | **M** (W backend) | B2, B3, B4, B6, E3, F2, F3, G2, G3 | T6 |
| — | **──── GATE: the user supplies a real `AI_PROVIDER_API_KEY`. Below this line nothing is executable, and nothing below may be reported as passing until it is. ────** | — | — | — |
| T8 | Live runtime verification against the real Anthropic API | **K** + **M** | A1, A3, A4, B1, C3, D3, F4, G4 | T7 + key |
| T9 | Final acceptance audit (A-J) + Phase 6 handoff, with an explicit runtime-verified vs. structurally-verified split | **W** (+ **K** half) | I3, J3, J5 | T8 (or T7 if the key never arrives) |

---

## 3. Task detail

### T1 — Provider-boundary foundation  *(Host: W)*

**Scope.** Everything the real provider needs to exist *except* the Anthropic code, so the risky
signature change is isolated from the risky network code and each is provable on its own.

- `provider/AiProvider.kt`: `suspend fun complete(request, onStream: suspend (Flow<AiToken>) -> Unit): AiUsage`;
  add `maxResponseTokens` to `AiCompletionRequest`; add `AiUsage`; remove `lessonContext` from
  `AiCompletionRequest`; move `LessonContext` to `service/` (Design § 3.1).
- `provider/StubAiProvider.kt`: signature only. `PLACEHOLDER` text and emission pattern **must not
  change** (the integration suite asserts it byte-for-byte).
- `service/AiTutorService.kt`: `prepareMessage` → `suspend fun streamMessage(principal, request,
  requestId, respond: suspend (Flow<AiToken>) -> Unit)`; the single `append(role="assistant")` call
  site moves inside the callback's success path (Design § 1.4 steps 11-14).
- `routes/AiTutorRoutes.kt`: call shape only — `respondTextWriter` moves inside the callback.
  Auth/CSRF/rate-limit wrapping untouched.
- `common/ApiException.kt`: `+ class ServiceUnavailable(code = "AI_TUTOR_UNAVAILABLE")` (503).
- `config/AppConfig.kt`: `+ aiProviderMaxResponseTokens` (default 1024, range 1..8192),
  `+ aiProviderTimeoutSeconds` (default 120, range 5..600), `+ fun aiProviderMode()`.
  `redactedSummary()` unchanged.
- `aitutor/AiTutorModule.kt`: `val aiTutorModule` → `fun aiTutorModule(aiProviderOverride: AiProvider? = null)`,
  with the mode-based selection (Design § 19.2). Still binds `StubAiProvider` in this task (no
  Anthropic class exists yet).
- `Application.kt`: the provider-mode startup log line; pass `null` override.
- `AiTutorIntegrationTest.kt`: **only** the `AppConfig(...)` constructor call gains the two new
  named arguments if required. **No assertion changes.**

**Test strategy.** No new test. The value of this task is that the **existing 6/6 keeps passing
through a structural change** — that is the proof the refactor is behavior-preserving.

**Completion gate.** `:backend:test` fully green; `AiTutorIntegrationTest` 6/6 with **zero**
assertion edits; backend starts and logs `AI Tutor provider mode: STUB — …` with no key set, and
`AI Tutor provider mode: ANTHROPIC (model=…)` with a dummy key set (A11 proven both ways); grep
confirms no `aiProviderApiKey` value reaches any log statement.

---

### T2 — `AnthropicAiProvider` + `MockEngine` unit suite  *(Host: W)*  **← highest-risk task**

**Scope.**
- `backend/build.gradle.kts`: `+ ktor-client-core`, `+ ktor-client-cio` (implementation, version
  `$ktorVersion` = 3.0.1), `+ ktor-client-mock` (testImplementation).
- `provider/AnthropicWire.kt`: `@Serializable` request DTO (exactly `model`, `max_tokens`, `system`,
  `messages`, `stream` — Design § 3.3) and the SSE payload DTOs (`content_block_delta`,
  `message_start`, `message_delta`, error envelope), all with `ignoreUnknownKeys = true`.
- `provider/AnthropicSse.kt`: line-based SSE reader over `ByteReadChannel.readUTF8Line()` →
  `AnthropicEvent` sealed type (Design § 3.4). Unknown/`ping` events ignored; a malformed
  `content_block_delta` payload is a stream failure, never silent.
- `provider/AnthropicAiProvider.kt`: the two-phase `complete` (Design § 3.4) — connect, status check,
  read-to-first-token, then `onStream`; usage capture; the full § 11 error mapping; `baseUrl`
  constructor parameter defaulting to `https://api.anthropic.com`; non-`data class`, key held
  privately, no `toString()` override.
- HTTP client factory with `HttpTimeout` (10s connect / 30s socket / configurable request) and
  `expectSuccess = false`; `ApplicationStopping` close subscription mirroring `plugins/Database.kt`.
- Bind it in `aiTutorModule` when `aiProviderMode() == "anthropic"`.

**Files.** `backend/build.gradle.kts`, `aitutor/provider/{AnthropicAiProvider,AnthropicWire,AnthropicSse}.kt`,
`aitutor/AiTutorModule.kt`, `Application.kt` (lifecycle), new
`backend/src/test/kotlin/com/mentora/backend/aitutor/AnthropicAiProviderTest.kt`.

**Test strategy.** The full `MockEngine` matrix in Design § 20.1 — **15 cases**, no network. The
non-negotiable assertions: (i) the exact five-key request body (**C4**); (ii) the key is in the
header and nowhere else (**A8**); (iii) **`onStream` is never invoked** on every pre-stream failure
case (this is the mechanical proof of the whole § 1.3 design and therefore of **A6/A7**);
(iv) mid-stream failure emits the tokens that did arrive, then throws.

**Completion gate.** New suite green; `:backend:test` fully green; `AiTutorIntegrationTest` still
6/6 (it runs in stub mode — the Anthropic class must be dead code there, which is itself the A11
proof); backend starts in both modes.

---

### T3 — Prompt construction + enrolled-course injection + language rule  *(Host: W)*

**Scope.**
- `service/AiPromptBuilder.kt` (new): the five-part system prompt (Design § 4.1), sanitized
  `<lesson_context>` (D4, Design § 4.2), `<enrolled_courses>` (C3, Design § 4.3), the language
  fallback chain (D3, Design § 15), and `normalizeHistory(...)` (Design § 4.4). `LessonContext`
  and `CourseBrief`/`EnrolledCourse` live here.
- `courses/repository/CourseRepository.kt`: `+ suspend fun findByIds(ids): List<CourseDocument>`
  (one `Filters.in` query).
- `courses/service/CourseService.kt`: `+ suspend fun enrolledCourseBriefs(ids): List<CourseBrief>` —
  **title and level only** (C4).
- `service/AiTutorService.kt`: fetch enrollments (`enrollment.list(principal, PageRequest(null, 20))`)
  → `enrolledCourseBriefs(...)` → `AiPromptBuilder.build(...)`; delete the old `SYSTEM_PROMPT`
  constant; pass `maxResponseTokens` from config.

**Test strategy.** `AiPromptBuilderTest` (new, pure — Design § 20.2), plus `AiTutorServiceTest`
(new, `mockk`) asserting: the enrolled-course read happens on **every** message (global *and*
lesson-context mode); the id set passed to `enrolledCourseBriefs` comes only from the principal's
own enrollments; a provider that throws before `onStream` results in **zero** `append(role="assistant")`
calls while the user message **was** appended (A7, service-level).

**Completion gate.** Both new suites green; `:backend:test` green; `AiTutorIntegrationTest` 6/6
(the stub ignores the prompt, so the existing assertions are untouched — if any of them breaks,
something leaked that shouldn't have).

---

### T4 — Observability + failure-path integration tests  *(Host: W)*

**Scope.**
- The `aiTutor.message` log line in `AiTutorService` (Design § 21): INFO on success, WARN/ERROR per
  the § 11 bucket, `requestId` passed **explicitly** from `call.requestId()`, `firstTokenMs` and
  `totalMs` measured, **never** any message/prompt/course/key content. **A10.**
- Test seam: `internal fun Application.module(appConfig, aiProviderOverride: AiProvider? = null)`
  threaded into `aiTutorModule(...)`; production `module()` passes `null`.
- Three new `AiTutorIntegrationTest` cases (Design § 20.3): pre-stream failure → 503 + JSON envelope
  + user message persisted + **no** assistant message; mid-stream failure → partial bytes + **no**
  assistant message; enrolled-course context → captured `systemPrompt` contains the student's two
  course titles and **not** a third course they aren't enrolled in.

**Test strategy.** As above. Also a focused check that the log line for a failing request contains
the outcome and error type and **does not** contain the message content (assert on a captured log
event, or verify by construction + explicit reviewer instruction in T5 if log capture proves
fiddly in this suite — say which was done, don't quietly skip it).

**Completion gate.** `AiTutorIntegrationTest` **9/9** (original 6 unmodified + 3 new);
`:backend:test` fully green; a manual run with a dummy key shows a correctly-shaped ERROR line for
`authentication_error` with the `check AI_PROVIDER_API_KEY` hint and **no key value** anywhere in
the output.

---

### T5 — Review checkpoint (mandatory, before the provider is marked done)  *(Host: W)*

**Scope.** Two independent reviews, reported separately, per the policy already resolved in
`PHASE_6_ACCEPTANCE_CRITERIA.md § H5`:

1. **Primary Opus review** — scope: the whole Phase 6 diff, with particular weight on
   `AnthropicAiProvider`/`AnthropicSse`/`AnthropicWire`, the `AiProvider` interface reshape, the
   route/service control-flow change, and the § 11 error mapping.
2. **Independent `codex-reviewer` second opinion** — scope **narrowed explicitly** to the
   provider boundary and key handling: `provider/*.kt`, `AiTutorModule.kt`'s selection rule,
   `AppConfig`'s key handling/`redactedSummary`, the startup log line, and every place the key
   could leak (logs, error envelopes, `toString`, test fixtures, request URL/body).
   Reported **separately and clearly labelled**, never merged into the Opus review.

**Explicit questions both reviews must answer** (so the checkpoint isn't a rubber stamp):
- Can `onStream` ever be invoked on a path that later fails to produce a complete stream *and*
  still persists an assistant message? (**A7**)
- Is there any path where the response writer opens before the provider's status is known? (**§ 1.3**)
- Can `AI_PROVIDER_API_KEY` reach any log, any HTTP response, any exception message, or any
  committed file? (**A8/A9/H1/H2**)
- Does the provider send anything beyond the five documented body fields? (**C4**)
- Does any failure path leave the Ktor `HttpClient` connection or response channel unreleased?

**Completion gate.** Both reviews complete; every blocking finding fixed and re-verified with the
full suite green; findings and resolutions recorded in `DECISIONS_LOG.md`. **T6 does not start
before this gate closes.**

---

### T6 — Documentation + config surface  *(Host: W)*

**Scope.**
- `backend/.env.example`: replace the stale Phase-1 comment on `AI_PROVIDER_API_KEY` with the real
  Phase 6 behavior (unset ⇒ stub mode with placeholder replies; set ⇒ real, billable Anthropic
  calls); document the two new optional keys. **Value stays empty.** (**H1/I2**)
- `backend/README.md`: a short "AI Tutor provider" section — the two modes, how to switch, the model
  id caveat (Design § 24), and that no client ever holds a key. (**I2**)
- `execution/INTEGRATION_CONTRACT.md`: § 4 gains `AI_TUTOR_UNAVAILABLE` (503) as an as-built
  taxonomy addition, exactly as `FORBIDDEN_CSRF` was recorded; § 8 is amended to state that the
  request/response contract is unchanged and that this new code can now appear in the pre-stream
  error envelope, plus that the global-mode system prompt now carries the student's enrolled courses.
- `execution/DECISIONS_LOG.md` **D140**: the § 1.3 wrinkle and why option (c) won; the 503-vs-429
  and 500-vs-503 mapping choice; the prompt-only language decision and what was deferred; the
  deliberate non-extension of KMP `ApiErrorCode`; the review-policy execution record.
- `execution/CURRENT_STATUS.md`: Phase 6 table, per-task status.

**Completion gate.** Docs consistent with the code (spot-checked, not assumed); working tree clean
and committed (**J4**); no key value anywhere in the diff.

---

### T7 — Cross-platform verification **without** a key  *(Host: M, backend W)*

**Scope.** Everything client-side that is provable before a key exists. Run the backend locally in
**stub mode**, then re-run with the T4 override wired to a deliberately-failing provider (or a
dummy key, which produces a real `authentication_error` → 500) to exercise the error path.

- **Android** (`AiTutorScreen`): B2 Thinking; B4 first-open welcome + quick actions; **B6 all five
  quick actions send, including "What should I learn next?"**; B3 error + Retry on a forced
  failure, with the user's message preserved; F2 Arabic + RTL; F3 light + dark.
- **Web** (`/[locale]/app/ai-tutor`): same list, **with B6 quick-action parity explicitly
  re-checked** (the acceptance criteria flag it as unverified); G2 Arabic/RTL; G3 light/dark.
- **E3 check:** confirm on both platforms that a **503 `AI_TUTOR_UNAVAILABLE`** renders the generic
  "Something went wrong — try again" + Retry treatment (`UX_STATES.md § 10`) and nothing worse.
  If either platform mishandles it, **stop and escalate** — that would be the one real KMP gap this
  design predicts is absent, and it must be fixed in the shared taxonomy, never per-platform.

**Test strategy.** Manual, recorded pass/fail per item with the build and device/browser noted.
No new automated client test (no client code changes).

**Completion gate.** Every item above recorded as pass, or recorded as a defect with an owner.
`mobile/` and `web/` diffs are **empty** — if this task produced client code changes, the "verify-only"
premise was wrong and that fact goes to the user before proceeding.

---

### T8 — Live runtime verification against the real Anthropic API  *(Host: K + M)*  **BLOCKED**

**Blocked on:** the user supplying a real `AI_PROVIDER_API_KEY` in `backend/.env` (and confirming
the exact `AI_PROVIDER_MODEL` id — Design § 24).

**Scope.**
- Backend starts in `ANTHROPIC` mode; the startup log names the mode and model.
- **A1/A3/B1:** a free-typed question returns a real, streaming answer token-by-token.
- **A4:** re-verify the gate stack with the real provider bound — unauthenticated 401, instructor/
  admin 403, unenrolled + lesson context 403, oversized content 400, per-minute 429, daily 429.
- **C2:** lesson-context mode from Android's Course Player "Ask AI Tutor" → the answer demonstrably
  references the real lesson.
- **C3:** "What should I learn next?" recommends **only** courses the test student is actually
  enrolled in — verified against a student with 2-3 known enrollments and a catalog containing
  courses they are *not* enrolled in. **This is the criterion this phase exists to close; do not
  accept a plausible-sounding answer that names a non-enrolled course.**
- **D2:** ask it to "mark this lesson complete" / "enrol me in X" — it must decline and explain it
  can't change state.
- **D3:** ask in Arabic → Arabic answer; ask a one-word ambiguous follow-up → sensible language.
- **A6/A7:** force a failure (temporarily invalid key → 500 with the config hint; kill network
  mid-answer → partial text preserved, no assistant message persisted — confirm via
  `GET /ai-tutor/conversation`).
- **A10:** confirm the log line carries token counts and latency and **no message content**.
- **F4/G4:** the Android and Web passes re-run against real responses (long answers, Arabic,
  scroll/wrap behavior).

**Completion gate.** All of the above observed and recorded with real evidence. **Nothing here may
be inferred from T2's mocked tests.** If the key never arrives, this task is reported as
`BLOCKED — runtime verification unavailable`, explicitly and by name, per **I3**.

---

### T9 — Final acceptance audit + Phase 6 handoff  *(Host: W, plus the K-dependent half)*

**Scope.**
- Walk **every** criterion in `PHASE_6_ACCEPTANCE_CRITERIA.md` A1-J5 and mark it
  `DONE` / `DONE (structurally verified only — no key)` / `BLOCKED` / `ACCEPTED GAP`, each with the
  concrete evidence (test name, log line, manual step). **I3's three-way distinction —
  "implementation complete" vs. "structurally complete" vs. "runtime verification unavailable" —
  is the required vocabulary; do not collapse it.**
- Full backend suite re-run (**J5**: no regression outside `aitutor` and its DI/config wiring).
- `execution/PHASE_HANDOFF.md` Phase 6 entry: what changed, the § 1.3 decision and why, the new 503
  code, what a Phase 7+ reader must know, and the § 22 known-limits list carried forward verbatim.
- `execution/CURRENT_STATUS.md` final Phase 6 state.
- Confirm `backend/.env` is still untracked and no key is in the history (**H1**).

**Completion gate.** Audit table complete with no unexplained cell; working tree clean; committed
and pushed.

---

## 4. Risks and open questions

| # | Risk | Likelihood | Mitigation / owner |
|---|---|---|---|
| R1 | **The `AI_PROVIDER_MODEL` default is not a valid Anthropic model id**, so every real call 404s. | Medium | Design § 11 row 9 maps it to a 500 with an explicit `check AI_PROVIDER_MODEL` hint, and the startup log prints the model. **User confirms the id at T8.** |
| R2 | The `AiProvider` signature change (T1) breaks something subtle in the route's streaming lifecycle that the stub can't reveal. | Medium | T1 is deliberately separated from T2 so the refactor is proven by the existing 6/6 *before* any network code exists. T5's review explicitly asks about writer-open ordering and channel release. |
| R3 | **Ktor 3.0.1 CIO's exact streaming/read-line behavior** differs from expectation (e.g. `readUTF8Line` handling of `\r\n`, or `requestTimeoutMillis` semantics across a streaming body read). | Medium | T2's `MockEngine` suite exercises the parser directly against canned bytes including `\r\n` framing. Fallback if CIO misbehaves on streaming: switch the engine to `ktor-client-java` — a one-line change, no code impact (Design § 3.2). |
| R4 | Anthropic's SSE event set or error-type strings drift from what is designed against. | Low | The parser ignores unknown event types by design, and the § 11 mapping falls back by HTTP status when `error.type` is unrecognized — so drift degrades to "generic 503", never to a crash or a silent wrong answer. |
| R5 | A 503 renders badly on Web or Android despite § 0's source-level verification. | Low | T7 tests it explicitly against a forced failure. If it fails, escalate — do **not** patch per-platform (E3). |
| R6 | Real answers expose a latent UI problem (long/Arabic content, scroll, wrap) the fixed stub placeholder never could. | Medium | T7 partially (stub is short), T8 fully. Treated as a verification finding, not a Phase 6 design defect. |
| R7 | The key never arrives and Phase 6 cannot be closed. | Real, present | T1-T7 + T9's structural half complete regardless. Phase 6 closes as "implementation complete, runtime verification unavailable" (**I3**) — an honest state this project already has precedent for (Phase 5's Mac gate). |
| R8 | Real-money exposure from an unattended key. | Low | 20/min + 200/day per user (existing, tested), `max_tokens=1024`, 120s ceiling, no server-side retry. Raised to the user as Design § 24 question 2. |

## 5. What this plan deliberately does **not** do

- No iOS task. iOS is frozen; **no file under `mobile/` is touched by any task above.**
- No docked/contextual AI Tutor panel in Course Player (**C5**, accepted gap).
- No KMP `ApiErrorCode` extension (Design §§ 12, 23).
- No rebuild or refactor of the Web/Android/KMP AI Tutor surfaces — they are verify-only, and if a
  task appears to need client code changes, that is a signal to stop and re-check the premise with
  the user, not to start editing.
- No multi-provider failover, no admin dashboard, no cost-analytics pipeline, no moderation call,
  no token persistence — all explicitly out of scope for a portfolio MVP.
