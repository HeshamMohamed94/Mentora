# Mentora — Deployment

**The Mentora MVP is a local portfolio/demo system, per [ADR-012](./adr/ADR-012-local-demo-scope.md) — LOCKED.** It is not deployed to production, or to any cloud host, during the current implementation phase. Every environment described below as "current MVP" runs entirely on the developer's own machine (plus a mobile emulator/simulator). Cloud hosting is described later in this document only as **optional future production evolution** — clearly labeled, not implemented, not scheduled, not an MVP dependency.

---

## CURRENT MVP ARCHITECTURE — Local Only

### 1. Local Development

The backend is **started locally by the developer** — there is no "deploy" step for the MVP, only "run":

```
MongoDB Community Edition   — installed natively, or run via infra/docker/docker-compose.yml
                               (mongo official image, seeded via infra/docker/mongo-init on first run)
backend                     — Ktor app, run directly (./gradlew run / an IDE run configuration),
                               or via docker-compose for convenience; connects to local MongoDB and
                               local media storage — no container hosting requirement (ADR-012 § 1)
web                         — Next.js dev server (npm run dev), connects to the local backend
```

`docker-compose.yml` is an optional convenience for bringing up MongoDB (and, if the developer prefers, the backend itself) with one command — it is not a hosting requirement. A developer may equally install MongoDB Community natively and run `./gradlew run` directly; both are first-class, supported ways to run the MVP locally. There is no MinIO/S3-compatible service in the local stack — media storage is the local filesystem directly (§ [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md), [ADR-008](./adr/ADR-008-media-storage.md)).

Android Studio / Xcode run `androidApp`/`iosApp` directly against the locally-running `backend`, via an emulator/simulator-reachable host address — no containerization needed for the mobile apps themselves. Which backend URL each client uses is environment-configurable, never a single hardcoded `localhost` assumption — see § 4a below.

### 2. Local Demo

The same local stack as § 1, run with realistic seed data (real-looking course titles/thumbnails and at least one real demo lesson video, not "Test Course 1") so a walkthrough of the portfolio-priority flow — Discovery → Course Details → Demo Checkout → Purchase Success → Course Player → Progress → Quiz → AI Tutor → Certificate — looks and behaves like a finished product, on the developer's machine. "Local Demo" is a **data/readiness state** of the same local environment described in § 1, not a separately hosted environment.

| Environment | Purpose | Data |
|---|---|---|
| **Local Development** | Day-to-day development | Seeded, disposable, reset freely |
| **Local Demo** | Portfolio walkthrough, run locally (screen-recorded or shown live) | Seeded with realistic demo content — this is the environment demoed in the portfolio video/live walkthrough |

No **Staging** and no **Production** environment exists for the current MVP. If a future decision is made to stand either up, see § 6.

### 3. Environments Required Right Now

Only these two:

- **Local Development**
- **Local Demo**

No cloud infrastructure, staging environment, or production environment is required by the current implementation phase.

### 4. Configuration Boundaries

Every environment-specific value is an environment variable, never hardcoded — loaded via [`BACKEND_ARCHITECTURE.md § 6`](./BACKEND_ARCHITECTURE.md)'s typed, fail-fast config loader on the backend, and via each client's own build-config mechanism (`.env.local` for Web; Gradle `buildConfigField`/flavor-specific values for Android; an `.xcconfig`/scheme-specific setting for iOS):

| Variable | Local Development / Local Demo value |
|---|---|
| `API_BASE_URL` (client-facing, per client — see § 4a) | Web: `http://localhost:8080`. Android emulator: `http://10.0.2.2:8080`. iOS simulator: `http://localhost:8080`. Physical device (either platform), or iOS simulator on some network setups: the developer machine's LAN IP, e.g. `http://192.168.x.x:8080` |
| `MONGODB_URI` | `mongodb://localhost:27017/mentora` |
| `MEDIA_STORAGE_ROOT` | a local filesystem path (e.g. `backend/storage/media/`) — see [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md) |
| `JWT_SIGNING_SECRET` | a fixed local-only dev value (never reused elsewhere) |
| `AI_PROVIDER_API_KEY` | a personal dev key, or a stub/mock provider implementation for offline development — see [ADR-009](./adr/ADR-009-ai-provider-abstraction.md) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` (plus the LAN-IP web origin if the Web app is being reached from a mobile device/emulator on the same network) |
| `LOG_LEVEL` | `DEBUG` |
| Feature flags | none needed at MVP scope — see note below |

**Feature flags:** not introduced in MVP. The brief asks for them "where genuinely needed" — nothing in the 29-screen MVP scope requires toggling a feature independently of a run (no gradual rollout, no A/B test, no kill-switch requirement stated anywhere in product docs). If one becomes genuinely necessary during implementation (e.g. gating an unfinished AI Tutor provider integration behind a flag), a simple environment-variable-driven boolean is sufficient — no feature-flag service/SDK is justified at this scale.

### 4a. Local Networking Strategy (Web / Android Emulator / iOS Simulator)

Because Mentora is local-demo only, the architecture must not assume a single `localhost` address reaches the backend from every client — mobile emulators do not share the host machine's network namespace the way a desktop browser does. This section documents the **strategy and configuration mechanism**; it is not implemented yet, per the constraint that this phase produces architecture, not code.

| Client | How it reaches the local Ktor backend | Why |
|---|---|---|
| **Web browser** | `http://localhost:8080` (or `http://127.0.0.1:8080`) | The browser runs on the same machine as the backend — `localhost` resolves directly to it, no special handling needed. |
| **Android Emulator (AVD)** | `http://10.0.2.2:8080` | The Android emulator runs in its own virtual network; `10.0.2.2` is the emulator's well-known alias for the host loopback interface. A plain `localhost`/`127.0.0.1` value from inside the emulator refers to the emulator itself, not the host machine — this is the one hardcoded-localhost trap this architecture explicitly avoids. |
| **Android physical device** | `http://<host-machine-LAN-IP>:8080` (e.g. `http://192.168.1.42:8080`) | A physical device is a separate machine on the same Wi-Fi/LAN — it needs the developer machine's actual network address, not a loopback alias. Requires the device and host machine to be on the same network, and the backend to bind to `0.0.0.0`, not just `127.0.0.1`. |
| **iOS Simulator** | `http://localhost:8080` | The iOS Simulator (unlike the Android emulator) shares the host Mac's network stack, so `localhost` resolves correctly without an alias. |
| **iOS physical device** | `http://<host-machine-LAN-IP>:8080` | Same reasoning as an Android physical device — a separate machine needs the host's real LAN address. |

**Mechanism, not hardcoding:** each client resolves `API_BASE_URL` from its own build-time/runtime configuration (§ 4 table) — Web from `.env.local` (or a runtime-configurable value if the demo needs to be reached from another device on the LAN), Android from a Gradle build-config field with a build-variant/flavor default of `10.0.2.2` for emulator builds and an overridable value for device builds, iOS from an `.xcconfig`/scheme setting. No client's networking code hardcodes `localhost` as the only possible value — the base URL is always read from configuration, so switching between emulator/simulator/physical-device targets is a config change, not a code change. This mirrors [`KMP_ARCHITECTURE.md`](./KMP_ARCHITECTURE.md)'s shared networking client, which accepts its base URL as an injected value rather than assuming one.

### 4b. Local Media Serving

See [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md) and [ADR-008](./adr/ADR-008-media-storage.md) for the full design. Summary: course thumbnails, lesson resources, and demo lesson videos are stored on the local filesystem under `MEDIA_STORAGE_ROOT`; Ktor serves them through controlled HTTP endpoints (public-read for thumbnails/avatars, enrollment-checked signed references for lesson videos) — never a bare static-file mount with no access control, and never a video binary written into MongoDB. This is the active MVP media architecture, not a stand-in for a cloud one.

### 5. CI — GitHub Actions (lightweight, no production CD)

One repository, path-filtered workflows (avoids running, e.g., the Android build on a backend-only change). CI validates the codebase; it does **not** deploy anything, since there is no production/staging target in MVP scope:

| Workflow | Triggers on changes under | Steps |
|---|---|---|
| `backend.yml` | `backend/` | lint → unit tests → integration tests against a real local MongoDB instance → build (no deploy step) |
| `web.yml` | `web/` | lint → type-check → unit/component tests → Playwright E2E (§ [`TESTING_STRATEGY.md § 6`](./TESTING_STRATEGY.md)) against a CI-provisioned backend+Mongo (no deploy step) |
| `android.yml` | `mobile/shared/`, `mobile/androidApp/` | build → unit tests → (optionally) Compose UI tests on an emulator → assemble a debug APK as a build artifact (no store-release step) |
| `ios.yml` | `mobile/shared/`, `mobile/iosApp/` | build (macOS GitHub-hosted runner) → unit tests (no TestFlight/App Store step) |
| `tokens.yml` | `design-system/design-tokens.json`, `design-system/themes/*.json` | runs `tools/token-pipeline` and fails if regenerated output differs from what's committed (catches a forgotten regeneration — see [ADR-011](./adr/ADR-011-design-token-pipeline.md)) |

No production deployment automation (CD) is part of MVP scope. If production evolution is pursued later (§ 6), a deploy step can be appended to these same workflows without restructuring them.

---

## OPTIONAL FUTURE EVOLUTION — NOT PART OF CURRENT MVP IMPLEMENTATION

Everything in this section is **documentation only** — a plausible future path, not a plan, not scheduled, and not required for the MVP to be considered complete. It is retained so that a future decision to productionize the demo has a documented starting point, per [ADR-012](./adr/ADR-012-local-demo-scope.md)'s migration path.

### 6. Possible Production/Staging Hosting

| Component | Possible choice | Why (if ever pursued) |
|---|---|---|
| Backend (Ktor container) | Railway | Simplest deploy-from-Dockerfile experience, generous low-cost tier, minimal ops overhead for a solo/small team. **Alternative:** Fly.io — more control (global edge regions, persistent volumes) at slightly higher operational complexity. |
| Database | MongoDB Atlas (free M0 or low-cost M2/M10 shared tier) | Zero-ops managed MongoDB, the natural cloud pairing for [ADR-004](./adr/ADR-004-database-engine.md)'s local Community Edition choice — a `MONGODB_URI` change only, per that ADR's migration path. |
| Web (Next.js) | Vercel | Native Next.js hosting, zero-config for App Router/SSR/ISR, automatic preview deployments per PR. |
| Media storage + CDN | Cloudflare R2 + Cloudflare's CDN | S3-compatible (the `MediaStorage` interface from [ADR-008](./adr/ADR-008-media-storage.md) gains a second implementation, not a rewrite), zero egress fees. |
| AI provider secret | A hosting platform's secret store, referenced only by the backend | Never reaches Vercel's environment or any client bundle — same rule as local (§ 4). |
| Mobile distribution — Android | Google Play Console Internal Testing, or Firebase App Distribution | Avoids the multi-day Play Store review lag; requires no MVP change to build config. |
| Mobile distribution — iOS | TestFlight | Requires an Apple Developer Program enrollment ($99/year) — a real cost, only relevant if this path is pursued; the MVP's iOS app is demoed via simulator/local device instead. |

None of the above is created, provisioned, or account-registered by the current implementation phase.

### 7. Possible Production-Evolution Technical Changes

- **Backend:** horizontal scaling behind a load balancer once traffic genuinely justifies it (still one deployable unit per [ADR-003](./adr/ADR-003-backend-architecture-style.md) unless a specific module is extracted per that ADR's migration path).
- **Database:** Atlas paid tier with a proper replica set and point-in-time backup.
- **Media:** the HLS transcoding pipeline and/or S3-compatible object storage noted in [ADR-008](./adr/ADR-008-media-storage.md)'s migration path.
- **Secrets:** a dedicated secrets manager (AWS Secrets Manager/Vault) with rotation policy.
- **Observability:** a metrics/tracing stack (Prometheus/Grafana, or a hosted APM) — not built now (§ [`TECH_STACK.md § 15`](./TECH_STACK.md)), and not needed for a local demo.
- **Payments:** if Mentora ever became a real product (explicitly out of scope), a real payment gateway integration would replace the demo-checkout module entirely — a product decision, not a technical extension of it, and one this architecture deliberately never builds a code path toward.
- **CD pipelines:** a deploy step appended to the CI workflows in § 5, gated on the same tests already running.

### 8. Risk Notes (only relevant if § 6/§ 7 are ever pursued)

- Free/low-cost tiers (Atlas M0, Railway/Vercel free tiers) have real usage caps.
- Cloudflare R2's zero-egress model specifically mitigates the highest-risk cost surprise (video bandwidth) for this product shape, which is why it was preferred over plain AWS S3 in [ADR-008](./adr/ADR-008-media-storage.md)'s migration path.

None of these risks apply to the current local-only MVP — there is no public traffic, no billing surface, and no bandwidth cost.
