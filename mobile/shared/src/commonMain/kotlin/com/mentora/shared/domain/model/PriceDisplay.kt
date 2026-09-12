package com.mentora.shared.domain.model

/**
 * Mirrors the backend's `PriceDisplayDto` (`CourseService.kt:27`): `amount, currency`.
 *
 * [amount] is an UNFORMATTED integer MINOR-UNIT value (e.g. cents) — `shared` performs zero
 * currency-formatting/rounding/grouping logic on it. Turning this into a display string (decimal
 * placement, thousands separators, currency-symbol placement for [currency]) is a future UI-layer
 * concern (`execution/PHASE_3_KMP_PLAN.md` Task 7 AC #8), never something `shared` computes.
 */
data class PriceDisplay(
    val amount: Int,
    val currency: String,
)
