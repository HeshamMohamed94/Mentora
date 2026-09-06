/**
 * Locale-aware formatting helpers. Western Arabic numerals (0-9) in every locale, including
 * Arabic — a locked design-system decision (design-system/LOCALIZATION.md § 8) — via the
 * `-u-nu-latn` Unicode extension, not a translated numeral system.
 */

function numeralLocale(locale: string): string {
  return locale === "ar" ? "ar-u-nu-latn" : locale;
}

/** e.g. formatPrice(899, "EGP", "en") -> "EGP 899"; formatPrice(899, "EGP", "ar") -> Arabic-labeled,
 * Western-numeral equivalent. Demo prices are whole numbers (product/DEMO_PAYMENT_FLOW.md § 5). */
export function formatPrice(amount: number, currency: string, locale: string): string {
  return new Intl.NumberFormat(numeralLocale(locale), {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatCount(value: number, locale: string): string {
  return new Intl.NumberFormat(numeralLocale(locale)).format(value);
}

export function formatDate(value: string | Date, locale: string): string {
  const date = typeof value === "string" ? new Date(value) : value;
  return new Intl.DateTimeFormat(numeralLocale(locale), { dateStyle: "medium" }).format(date);
}
