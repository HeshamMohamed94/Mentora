# ADR-009: AI Tutor Provider Abstraction

**Status:** Locked (mechanism) / **Provider: Approved — Anthropic Claude API** (explicit product-owner sign-off recorded 2026-09-04)
**Date:** 2026-09-04

## Decision

The backend's `aiTutor` module defines a small, provider-agnostic Kotlin interface (conceptually `interface AiProvider { suspend fun complete(request: AiCompletionRequest): Flow<AiToken> }`) and every AI Tutor interaction — client message, quick action, lesson-context injection — flows through the backend and this interface. **No AI provider API key, request, or response ever touches a client directly.**

**Concrete provider (approved 2026-09-04): the Anthropic Claude API** is the initial `AiProvider` implementation. This selection is a configuration/implementation-time choice bound via dependency injection (Koin), not a change to the interface, routes, persistence, or client code — see § "Explicit Decision Required" below for the record of this approval, and the Migration Path for how a future provider/model change is expected to work.

## Context

The brief requires the AI Tutor architecture to be fully designed "WITHOUT implementing it," and separately requires that "no AI provider API keys" ever reach a client. It also notes product planning deliberately deferred provider/model integration itself ([`../product/PRODUCT_SPEC.md § 11`](../../product/PRODUCT_SPEC.md): "Model/provider integration itself... no AI API is integrated during product planning").

## Options Considered

### Option A — Backend-side provider abstraction, provider TBD (chosen)

The client (Web/Android/iOS) calls Mentora's own backend (`POST /api/v1/ai-tutor/conversations/{id}/messages`) exactly like any other authenticated endpoint. The backend resolves lesson context (if any), verifies the student is enrolled in the relevant course, constructs the prompt, and calls the `AiProvider` interface — which is the *only* place a real provider SDK/API key is referenced, and it lives entirely in backend configuration/secrets (never bundled into a mobile app binary or a Web JS bundle, both of which would trivially leak a key). This is the only option consistent with the brief's non-negotiable requirement.

### Option B — Client calls the AI provider directly, with a backend-issued short-lived scoped token

**Why not:** several AI providers do support short-lived scoped tokens for exactly this pattern, but it still requires embedding provider-specific request-construction logic (prompt format, model selection, streaming protocol) into three separate clients instead of one backend module, tripling the surface that needs to change if the provider or prompt strategy ever changes, and making server-side context injection (verifying enrollment before revealing lesson content to the model) awkward to enforce consistently across three implementations. Rejected — the backend-mediated design is strictly better on both security and maintainability axes, at low added latency cost (one extra hop the backend already needs to make for auth anyway).

### Option C — No abstraction interface; call a specific provider's SDK directly from the `aiTutor` module

**Why not fully:** would work today but locks the module to one provider's request/response shape throughout the codebase, making a future provider switch (or a provider outage requiring failover) a larger refactor than necessary. The interface costs little to define now and directly serves the brief's ask for a "provider abstraction," so it's adopted even though only one implementation exists initially.

## What The Interface Owns vs. What It Doesn't

The `AiProvider` interface's job is narrow and mechanical: given a constructed prompt/context and conversation history, stream back tokens. It does **not** own: authorization (verified before the interface is called), conversation persistence (the `aiTutor` module persists `Message`s regardless of which provider answered), or prompt construction (built by the `aiTutor` module's own logic, using the injected lesson context — see [`AI_TUTOR_ARCHITECTURE.md`](../AI_TUTOR_ARCHITECTURE.md)). This keeps the provider-specific code minimal and swappable.

## Explicit Decision Required (resolved — approved 2026-09-04)

**Which LLM API Mentora integrates is now decided: the Anthropic Claude API**, selected by explicit product-owner approval — it carries direct cost, account-setup, and terms-of-service implications that were correctly left to that decision rather than an architectural judgment call. The interface above was designed so this choice could be made at AI Tutor implementation time (Milestone M13 in [`IMPLEMENTATION_ROADMAP.md`](../IMPLEMENTATION_ROADMAP.md)) without affecting anything built before it, and that property holds unchanged now that the choice is made.

Two conditions were attached to this approval and both are already satisfied by the design above, not new constraints on it:
- The Claude API key is backend-only configuration (§ "What The Interface Owns vs. What It Doesn't" and `DEPLOYMENT.md § 4`'s `AI_PROVIDER_API_KEY` variable) — it is never bundled into the Web JS bundle, the Android app, or the iOS app, and no client-facing route ever echoes it.
- The architecture does not hardcode one permanent Claude model identifier: `AiCompletionRequest`/the `AiProvider` implementation reads the specific Claude model name from backend configuration (the same environment-variable-driven config loader as every other environment-specific value, per `DEPLOYMENT.md § 4`), not a literal baked into application code — so a future model upgrade (e.g. a newer Claude model) is a configuration change, not a code change.

## Consequences

- `AiConversation` and `Message` are persisted in MongoDB regardless of provider (see [`DATABASE_MODEL.md`](../DATABASE_MODEL.md)) — conversation history is a Mentora-owned asset, not something that lives only in a provider's own conversation-state feature.
- Rate limiting, token/cost budgeting, and abuse controls (see [`AI_TUTOR_ARCHITECTURE.md § 6`](../AI_TUTOR_ARCHITECTURE.md)) are implemented once, in the `aiTutor` module, independent of provider.
- The "Quiz me" quick action is explicitly conversational/ephemeral — it never creates a `Quiz`/`QuizAttempt` record, regardless of provider, per [`../product/PRODUCT_SPEC.md § 11`](../../product/PRODUCT_SPEC.md).

## Migration Path

Switching providers, or adding a second provider for cost/quality fallback, means implementing a second `AiProvider` and changing which implementation is bound via dependency injection (Koin) — no change to routes, persistence, authorization, or client code.
