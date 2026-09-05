# ADR-011: Design Token Generation Pipeline

**Status:** Locked
**Date:** 2026-09-04

## Decision

**Style Dictionary** transforms [`../design-system/design-tokens.json`](../../design-system/design-tokens.json) into three generated, never-hand-edited outputs:
- `web/styles/tokens.css` — CSS custom properties, theme-scoped via `[data-theme="light"]`/`[data-theme="dark"]`.
- `mobile/androidApp/.../MentoraTokens.kt` — Kotlin objects exposing `androidx.compose.ui.graphics.Color` and Compose `TextStyle` values.
- `mobile/iosApp/.../MentoraTokens.swift` — Swift `Color`/`Font` values (and a `MentoraColors.xcassets` Color Set catalog for asset-catalog-based any/dark appearance switching).

Design tokens are **not** part of the `mobile/shared` KMP module.

## Context

[`../design-system/platform-mapping.md § 9`](../../design-system/platform-mapping.md) already names this exact mechanism ("via a token-transform script, e.g. Style Dictionary... generated, never hand-edited") and [`../design-system/DESIGN_RULES.md`](../../design-system/DESIGN_RULES.md)'s Ownership Model states "if a platform token file and `design-tokens.json` ever disagree, `design-tokens.json` wins and the generated file is regenerated — never patched by hand." This ADR is the Technical Architecture's formal adoption of that already-specified mechanism, plus the one nuance the design system doesn't resolve: **where, in the code, do generated tokens live relative to the KMP shared module.**

## Options Considered

### Option A — Style Dictionary, three platform-native outputs, tokens live outside `shared` (chosen)

Style Dictionary is the industry-standard tool for exactly this transform (JSON design tokens → per-platform native code), actively maintained, and requires no custom parser to be written — a "no unnecessary dependencies" win since building a bespoke transform script would just be a worse, unmaintained version of a solved problem. Tokens are generated directly into `androidApp` and `iosApp` (not `shared`) because Compose's `Color`/`TextStyle` and SwiftUI's `Color`/`Font` are platform-UI-framework types with no shared representation — putting them in `shared` would mean either (a) `shared` takes a Compose dependency just to hold `Color` values, which is architecturally backwards (a KMP module should not depend on an Android-only UI toolkit), or (b) representing colors as raw hex-string primitives in `shared` and re-parsing them into `Color`/`Color` on each platform — extra indirection for zero benefit, since Style Dictionary can emit the correctly-typed value directly to each platform anyway.

### Option B — Hand-maintain three separate token files, one per platform

**Why not:** this is precisely the anti-pattern [`../design-system/DESIGN_RULES.md`](../../design-system/DESIGN_RULES.md) already forbids ("if a platform token file and `design-tokens.json` ever disagree... never patched by hand") — three hand-maintained copies of one source of truth will drift, silently, the first time someone updates one platform's file under deadline pressure and forgets the other two. Rejected as a correctness risk the design system itself already ruled out.

### Option C — Put design tokens inside the KMP `shared` module as plain data (hex strings, numeric values), consumed and converted to `Color`/`Font` on each platform

A middle ground between A and B.

**Why not chosen over A:** still requires a conversion step on both Android and iOS (hex string → `Color`) that Style Dictionary can simply do once, at generation time, for free — this option trades "generate the right type per platform" for "generate one loosely-typed representation and write the same conversion code twice," which is strictly more runtime code for no benefit. The token *values* are already the single source of truth in `design-tokens.json` regardless of which option is chosen; Option A just also gets the generated *types* right per platform.

## Consequences

- The token pipeline is a build/tooling concern (`tools/token-pipeline/`, a Style Dictionary config + npm/Node script — see [`REPOSITORY_STRUCTURE.md`](../REPOSITORY_STRUCTURE.md)), run in CI whenever `design-system/design-tokens.json` changes, with a CI check that fails if generated output would differ from what's committed (catching a forgotten regeneration).
- Component code in all three clients references only the generated token objects/CSS variables — never a raw hex value — enforcing [`../design-system/DESIGN_RULES.md` rule 1](../../design-system/DESIGN_RULES.md) at the tooling level, not just by convention.
- `mobile/shared` has zero design-token dependency, keeping it a pure business-logic module (reinforces [ADR-002](./ADR-002-kmp-sharing-boundary.md)'s UI-free boundary).

## Migration Path

If a future 4th platform is ever added (unlikely for this MVP, but Style Dictionary's whole value proposition is exactly this), it's a new Style Dictionary output target/format, not a change to the source-of-truth JSON or to any existing platform's generated files.
