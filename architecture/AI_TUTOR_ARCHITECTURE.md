# Mentora — AI Tutor Architecture

Mechanism locked, initial provider approved — the Anthropic Claude API, per [ADR-009](./adr/ADR-009-ai-provider-abstraction.md). This is a design, not an implementation — no AI API is integrated by this document.

---

## 1. End-to-End Flow

```
Client (Web/Android/iOS)
  │  POST /api/v1/ai-tutor/conversation/messages
  │  body: { content: string, lessonContextId?: string }
  │  auth: Bearer/cookie (same as any endpoint)
  ▼
Backend `aitutor` module
  1. Resolve principal (userId) — reject if unauthenticated.
  2. Rate-limit check (§ 6) — reject with 429 RATE_LIMITED_AI_TUTOR if exceeded.
  3. If lessonContextId present: verify the principal is ENROLLED in the course owning that lesson
     (service-layer check against `enrollments` — see § 4). Reject with 403 FORBIDDEN_NOT_ENROLLED
     if not. Fetch the lesson's title/description (never trust client-supplied lesson *content* text).
  4. Fetch/create the user's `aiConversations` document; fetch recent `aiMessages` history (windowed, § 3).
  5. Construct the prompt (§ 3): system instructions + lesson context (if any) + trimmed history + new message.
  6. Persist the user's message to `aiMessages` immediately (so it's never lost even if the provider call fails).
  7. Call `AiProvider.complete(...)` (§ ADR-009) — streamed.
  8. Stream tokens back to the client as they arrive (chunked response / SSE-equivalent over the existing
     connection) so the client can render the "AI Tutor" bubble growing token-by-token.
  9. On stream completion: persist the assistant's full message to `aiMessages`.
  10. On provider failure/timeout: do NOT persist a partial/garbled assistant message — surface a
      client-visible error per `../ux/UX_STATES.md § 10` ("Something went wrong — try again"), leaving
      the conversation otherwise intact (the user's message from step 6 is still there to retry against).
  ▼
Client renders the response as a new AITutorBubble.
```

No AI provider SDK, API key, or network call exists anywhere in `web/`, `mobile/androidApp/`, `mobile/iosApp/`, or `mobile/shared` — every one of these steps happens inside the `aitutor` backend module.

## 2. Global vs. Lesson-Context Modes

One endpoint, one conversation model (§ [`DATABASE_MODEL.md § 13`](./DATABASE_MODEL.md)) serves both, per [`../product/PRODUCT_SPEC.md § 11`](../product/PRODUCT_SPEC.md) ("both... a general assistant... carries that lesson's context automatically"):

- **Global** (opened from Sidebar/bottom nav): `lessonContextId` omitted. The system prompt includes the student's enrolled-course list (fetched server-side) so "What should I learn next?" can reason over real enrollment data — never a platform-wide catalog recommendation, per the product doc's explicit scope limit.
- **Lesson-context** (opened from within Course Player): `lessonContextId` present, resolved server-side to the lesson's title/description as described in § 1 step 3.

Both modes share the same persisted conversation — Mentora doesn't model "one thread per lesson," matching the product doc's single-persisted-history description.

## 3. Prompt Construction

Composed server-side, in this fixed order, every time:
1. **System instructions** — a fixed Mentora tutor persona + explicit behavioral constraints mirroring the product's locked scope: never fabricate access to content outside the student's enrollments, never claim to modify enrollment/progress (AI Tutor is read/explain-only per [`../product/PRODUCT_SPEC.md § 11`](../product/PRODUCT_SPEC.md)), and the five quick actions' exact intended behavior (§ 7).
2. **Lesson context** (if present) — the resolved lesson title/description from step 1.3 above, injected as reference material, clearly delimited from the user's own message so the model can distinguish "content to explain" from "instructions to follow" (a basic, standard prompt-injection mitigation — the lesson content is data the model reads about, not a source of new instructions).
3. **Conversation history** — the N most recent messages (a fixed window, e.g. the last 20 turns or a token-budget cutoff, whichever is smaller) — never the full unbounded history, both for cost control (§ 6) and because `aiMessages` is explicitly unbounded by design ([`DATABASE_MODEL.md § 14`](./DATABASE_MODEL.md)).
4. **The new user message.**

## 4. Context Boundaries & Authorization

- The AI Tutor only ever answers with context the backend itself resolved and verified access to — a student cannot smuggle another course's content into scope by guessing a `lessonContextId`, because step 1.3's enrollment check runs on every request, not once per session.
- No AI Tutor request can read another student's conversation, progress, or enrollment — the conversation lookup in step 1.4 is always scoped to the authenticated principal's own `userId`.
- The AI Tutor cannot **write** anything except its own conversation messages — it has no code path to call `progress`, `enrollment`, or `quiz` mutation endpoints, per [`../product/PRODUCT_SPEC.md § 11`](../product/PRODUCT_SPEC.md)'s explicit "read/explain only" constraint. This is enforced structurally: the `aitutor` module's service layer has no dependency on any other module's *mutating* service methods, only read-only ones (fetching enrolled courses, fetching lesson metadata).

## 5. Logging & Privacy

Conversation content lives in `aiMessages` (§ [`DATABASE_MODEL.md § 14`](./DATABASE_MODEL.md)) under normal database access controls (readable only by the owning user and, for support/debugging, an operator with direct database access — never exposed through any API to another user, including Instructors/Admins, who have no product-defined reason to see a student's AI conversations). **Application logs do not include full message content at INFO level** — a log line for an AI Tutor request records `userId`, `requestId`, `lessonContextId` (if any), token counts, and latency, but not the message text itself, consistent with [`AUTH_SECURITY.md § 12`](./AUTH_SECURITY.md)'s redaction rule. If a provider integration requires temporary request/response logging for debugging during implementation, that logging is scoped to a non-production environment and time-boxed, never left enabled in staging/production by default.

## 6. Rate Limiting, Abuse Controls, Cost Controls

- **Per-user rate limit** (Ktor `RateLimit`, § [`AUTH_SECURITY.md § 7`](./AUTH_SECURITY.md)): a message-per-minute cap and a daily message cap, both tunable via configuration (not hardcoded), so cost exposure from a single account is bounded regardless of provider pricing.
- **Conversation history window** (§ 3) and a **max-response-token** cap passed to the provider bound the cost of any single request.
- **Request size cap** on the incoming user message (a student pasting an enormous block of text is rejected with `400 VALIDATION_ERROR` before it reaches the provider call).
- **Abuse/moderation:** out of scope for MVP beyond the above — if the selected provider (§ ADR-009) offers a moderation endpoint, calling it before/alongside the completion request is a reasonable low-cost addition at implementation time, noted here as recommended, not required, to avoid overbuilding a portfolio MVP's trust-and-safety surface.

## 7. Quick Actions — Explicitly Conversational, Not Persistent

The five fixed quick actions (*Explain this lesson, Summarize, Give me an example, Quiz me, What should I learn next?*) are **UI-triggered pre-filled prompts**, not separate backend endpoints — tapping one sends a specific, fixed message content (localized per [`LOCALIZATION_ARCHITECTURE.md`](./LOCALIZATION_ARCHITECTURE.md)) through the exact same `POST /ai-tutor/conversation/messages` flow as free-typed text, carrying `lessonContextId` when available.

**"Quiz me" is explicitly, architecturally distinct from the real `Quiz` system:** it produces an ephemeral, conversational practice question **inside the chat conversation only** — the response is a normal `aiMessages` entry, never a `Quiz`/`Question`/`QuizAttempt` record ([`DATABASE_MODEL.md §§ 7–8`](./DATABASE_MODEL.md)). The `aitutor` module has no write access to the `quiz` module's collections at all (§ 4), which makes "Quiz me never accidentally creates a real graded attempt" a structural guarantee, not a behavioral promise that could regress.

**"What should I learn next?"** — the system prompt (§ 3, step 1) instructs the model to recommend only from the student's own enrolled courses/Learning Paths (fetched server-side and included in context), never a platform-wide catalog scan — matching [`../product/PRODUCT_SPEC.md § 11`](../product/PRODUCT_SPEC.md)'s explicit scope limit.

## 8. Language Behavior

Per [`../product/PRODUCT_SPEC.md § 11`](../product/PRODUCT_SPEC.md), response-language behavior beyond the interface chrome is explicitly deferred by product planning. This architecture's default (simple, defensible, and changeable without a schema/API change): **the model responds in the language of the student's message**, since this is the most natural default for a conversational assistant and requires no additional signal. The system prompt (§ 3) may additionally hint the active UI locale so a student typing in English inside an Arabic-UI session still gets a sensible default if their message is ambiguous (e.g. a single word). This is an implementation-level tuning decision, not an architectural constraint — it can be changed at AI Tutor implementation time without touching the flow, persistence, or authorization design above it.

## 9. Explicit Decision Required

**Provider selection (which LLM API) is not made in this document** — see [ADR-009](./adr/ADR-009-ai-provider-abstraction.md) and [`ADR_INDEX.md`](./ADR_INDEX.md)'s approval-required list. Everything above is written to be provider-agnostic and implementable regardless of which provider is ultimately chosen.
