package com.mentora.android.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/*
 * Hand-authored (NOT generated) — mirrors [MentoraDimens]'s "re-export a generated value under a
 * call-site-friendly name" pattern, except motion.* (design-tokens.json § motion) has no generated
 * Kotlin constant at all: Task 2's generator only ever walked color/typography/shape/elevation/
 * spacing/icon/touchTarget into MentoraTokens.kt, never motion.duration/motion.easing. Disclosed gap
 * (Task 5): every value below is hardcoded exactly once here, traceable to design-tokens.json
 * § motion, rather than re-typed as raw literals (150/200/300, cubic-bezier coordinates) at each
 * animated component's call site (ProgressBar fill animation, Tabs indicator, ExposedDropdownMenu
 * open/close, ...).
 */

/** motion.duration (design-tokens.json), in milliseconds — feed directly into `tween(durationMillis = ...)`. */
object MentoraMotionDuration {
    const val fast: Int = 150
    const val normal: Int = 200
    const val slow: Int = 300
}

/**
 * motion.easing (design-tokens.json) as Compose [Easing] curves. `linear` is Compose's own built-in
 * [LinearEasing] constant (a straight line has no cubic-bezier control points to transcribe) —
 * re-exported here only so every motion token is reachable from one object, per
 * `COMPONENTS.md` § ProgressBar's explicit "indeterminate variant ... `easing.linear`, never
 * `easing.standard`" rule.
 */
object MentoraMotionEasing {
    val standard: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
    val decelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1f)
    val accelerate: Easing = CubicBezierEasing(0.4f, 0.0f, 1f, 1f)
    val linear: Easing = LinearEasing
}
