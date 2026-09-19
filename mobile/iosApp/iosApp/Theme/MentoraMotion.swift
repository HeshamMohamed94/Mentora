import SwiftUI

// Phase 5 Task T8 slice 1 (Component Kit A, atoms) — hand-authored (NOT generated) motion tokens.
//
// Disclosed gap, mirroring Android's own precedent (`mobile/androidApp/.../theme/MentoraMotion.kt`,
// read directly, not copied blindly — Android is REFERENCE-ONLY on this project, never source of
// truth): `tools/token-pipeline/generate.js` never walks `design-tokens.json#/motion` into any
// generated Swift constant (it only ever emits color/typography/shape/elevation/spacing/icon/
// touchTarget into `Theme/MentoraTokens.swift`) — motion has no generated Kotlin OR Swift constant at
// all. Every value below is hardcoded exactly once here, traceable to
// `design-system/design-tokens.json#/motion` (cross-checked directly against that JSON, not trusted
// solely from Android's Kotlin transcription — both were read), rather than re-typed as raw literals
// (150/200/300, cubic-bezier coordinates) at each animated component's call site.
//
// Real numbers confirmed from `design-system/design-tokens.json#/motion` (lines ~306-325):
//   duration: { fast: 150, normal: 200, slow: 300 }   (milliseconds)
//   easing:   standard   = cubic-bezier(0.4, 0.0, 0.2, 1)
//             decelerate = cubic-bezier(0.0, 0.0, 0.2, 1)
//             accelerate = cubic-bezier(0.4, 0.0, 1,   1)
// `linear` has no bezier curve in the token file (Compose's own built-in `LinearEasing`/SwiftUI's own
// built-in `.linear` is the exact analogue — a straight line has no control points to transcribe),
// matching Android's own `MentoraMotionEasing.linear` re-export precedent exactly.
//
// SwiftUI's `Animation.timingCurve(_:_:_:_:duration:)` bundles a duration into every curve value,
// unlike Compose's `Easing`, which is duration-independent and combined with a separate
// `tween(durationMillis:, easing:)` at the animation call site (Android's own split
// `MentoraMotionDuration`/`MentoraMotionEasing` objects, composed by the caller). This file keeps the
// SAME split shape Android's does — the raw cubic-bezier control points live as plain, directly-
// testable `MentoraCubicBezier` `static let`s (never re-derived from, or asserted only through, an
// opaque `Animation` value, which is not a stable, publicly-inspectable API surface) — and a single
// `MentoraMotionEasing.animation(_:duration:)` factory function combines any curve with an explicit
// duration (in seconds — pass a `MentoraMotionDuration` constant) into a real `Animation`, e.g.
// `MentoraMotionEasing.animation(MentoraMotionEasing.standard, duration: MentoraMotionDuration.fast)`
// — retaining the same duration/easing composability Android's split objects provide, translated into
// SwiftUI's `Animation` shape rather than approximated away.
//
// `.easeInOut`/`.easeOut`/etc. are DELIBERATELY NOT used as approximations: SwiftUI's `.easeInOut` is
// cubic-bezier(0.42, 0.0, 0.58, 1.0) — NOT the same curve as `easing.standard`'s
// cubic-bezier(0.4, 0.0, 0.2, 1.0) — and none of SwiftUI's named built-in curves match
// `easing.decelerate`/`easing.accelerate` either. Every curve here is therefore a real
// `.timingCurve(...)` construction from the token file's own control points, never a named built-in
// standing in for a close-but-different curve.

/// `motion.duration` (`design-system/design-tokens.json#/motion/duration`), converted from the
/// token's milliseconds to the `Double` SECONDS `Animation.timingCurve(duration:)`/`.linear(duration:)`
/// expect (150ms -> 0.15s, 200ms -> 0.2s, 300ms -> 0.3s).
enum MentoraMotionDuration {
    static let fast: Double = 0.15
    static let normal: Double = 0.2
    static let slow: Double = 0.3
}

/// One cubic-bezier curve's four control points, as plain testable `Double`s — never re-derived from
/// (or asserted only through) an opaque `Animation` value.
struct MentoraCubicBezier: Equatable {
    let x1: Double
    let y1: Double
    let x2: Double
    let y2: Double
}

/// `motion.easing` (`design-system/design-tokens.json#/motion/easing`) as both the raw
/// `MentoraCubicBezier` control points (for drift testing) and `Animation` factory functions (for real
/// call sites) built from those SAME points — never a second, independently-typed copy of the numbers.
enum MentoraMotionEasing {
    static let standard = MentoraCubicBezier(x1: 0.4, y1: 0.0, x2: 0.2, y2: 1.0)
    static let decelerate = MentoraCubicBezier(x1: 0.0, y1: 0.0, x2: 0.2, y2: 1.0)
    static let accelerate = MentoraCubicBezier(x1: 0.4, y1: 0.0, x2: 1.0, y2: 1.0)

    /// Builds a real SwiftUI `Animation` from one of the curves above plus an explicit duration (in
    /// seconds — pass a `MentoraMotionDuration` constant). E.g.
    /// `.animation(MentoraMotionEasing.animation(MentoraMotionEasing.standard, duration:
    /// MentoraMotionDuration.fast), value: someValue)`.
    static func animation(_ curve: MentoraCubicBezier, duration: Double) -> Animation {
        .timingCurve(curve.x1, curve.y1, curve.x2, curve.y2, duration: duration)
    }

    /// `motion.easing` has no `linear` entry with bezier control points — SwiftUI's own built-in
    /// `.linear(duration:)` is the exact analogue of Compose's `LinearEasing`, per
    /// `COMPONENTS.md`'s ProgressBar indeterminate-variant rule ("looping linear sweep ... never uses
    /// `easing.standard`").
    static func linear(duration: Double) -> Animation {
        .linear(duration: duration)
    }
}
