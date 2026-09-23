import { describe, expect, it } from "vitest";
import { formatCount, formatDate, formatPrice } from "./format";

/**
 * Bounded Vitest unit tier (H2/C3). These formatters are the pure logic consumed by both the
 * Quiz Results screen (score/question-count display) and the Demo Checkout screen (price
 * display) — the screens themselves stay untested here (Quiz Results is owned by another
 * in-flight Phase 8 task; Checkout's flow logic is covered separately in enrollment.test.ts).
 *
 * `design-system/LOCALIZATION.md § 8` locks in Western Arabic numerals (0-9) even under the
 * `ar` locale — never the Eastern Arabic-Indic digit glyphs a naive `Intl` call would produce.
 */
describe("formatPrice", () => {
  it("formats a whole-number demo price in English with the currency code", () => {
    // `Intl.NumberFormat` inserts a non-breaking space (U+00A0) between the currency code and
    // the amount, so the assertion normalizes whitespace rather than depending on that glyph.
    expect(formatPrice(899, "EGP", "en").replace(/\s/g, " ")).toBe("EGP 899");
  });

  it("formats the same price in Arabic using Western (Latin) numerals, not Eastern Arabic-Indic digits", () => {
    const result = formatPrice(899, "EGP", "ar");
    expect(result).toContain("899");
    // Eastern Arabic-Indic digits for 8/9/9 are ٨٩٩ — must never appear.
    expect(result).not.toMatch(/[٠-٩]/);
  });

});

describe("formatCount", () => {
  it("formats an English count as-is", () => {
    expect(formatCount(3, "en")).toBe("3");
  });

  it("formats an Arabic-locale count using Western numerals, not Eastern Arabic-Indic digits", () => {
    const result = formatCount(12, "ar");
    expect(result).toBe("12");
    expect(result).not.toMatch(/[٠-٩]/);
  });
});

describe("formatDate", () => {
  it("accepts an ISO string and an Arabic locale without throwing, using Western numerals", () => {
    const result = formatDate("2026-01-15T00:00:00.000Z", "ar");
    expect(result).not.toMatch(/[٠-٩]/);
  });

});
