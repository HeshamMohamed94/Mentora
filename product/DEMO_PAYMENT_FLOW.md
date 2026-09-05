# Mentora — Demo Payment Flow

**This is not a payment system.** Mentora simulates the complete purchase UX for portfolio-demonstration purposes only. No real money, no real payment gateway, and no real financial information are ever involved, requested, or possible.

---

## 1. Strict Rules

**Never integrated:** Stripe, PayPal, Paymob, Fawry, Apple Pay, Google Pay, any real payment gateway, any banking system, any real payment SDK of any kind.

**Never requested, collected, stored, or simulated as an entry field:** card number, CVV, expiry date, bank account details, billing address, payment credentials, or any real financial information. The Demo Checkout screen contains **zero** input fields of this kind — not even fake-looking ones. There is no possibility of a real charge because there is no code path that could ever initiate one.

## 2. Demo Checkout Experience

Screen: **Demo Checkout** (`SCREEN_INVENTORY.md § 19`). Content:

- Course thumbnail, title, instructor
- Original price *(optional — see § 5, only when useful for showing a "discount" presentation)*
- Demo purchase price
- Order summary (course line item + total)
- Clear demo-payment notice: **"Demo Payment — no real charges or payment information required."**
- Primary confirmation action: **"Complete Demo Purchase"**

No label anywhere in this flow implies a real financial transaction is occurring — "Pay Now," "Confirm Card," "Charge," and similar real-payment phrasing are never used.

## 3. Demo Purchase Flow

```
Course Details
  → "Enroll" / "Buy Course"
  → Demo Checkout            (order summary + demo-payment notice)
  → "Complete Demo Purchase"
  → Processing state         (brief, in-place — not a separate screen)
  → Purchase Success         (success animation/illustration)
  → Enrollment created
  → "Start Learning" → Course Player
```

**Step by step:**

1. Student taps **Complete Demo Purchase** on Demo Checkout.
2. The UI enters a short, purely simulated processing state (in-place on the same screen — a loading indicator, not a route change; see `../design-system/COMPONENTS.md § LoadingState`).
3. A short, polished success animation plays (see `PRODUCT_SPEC.md § 15` — this is a portfolio-priority moment).
4. The system creates (or updates) the student's enrollment for that course.
5. Purchase Success displays: **"Payment Successful"** and **"You're now enrolled!"**
6. Primary action: **"Start Learning"** → Course Player. Secondary action: back to My Learning.

The processing state is intentionally short (no artificial multi-second delay beyond what reads as a believable brief pause) — this is a demo, not a simulation of real gateway latency.

## 4. Technical Product Meaning

The UI says "Payment Successful" for demo authenticity, but this represents a **simulated purchase/enrollment event**, never a real financial transaction, and the product's internal naming must keep that distinction unambiguous for whoever builds it next.

**Domain terminology to carry into technical design (later phase):**
- `DemoPurchase` — the record of a student completing the simulated checkout for a course.
- `SimulatedCheckout` — the flow/process itself.
- `DemoEnrollment` — the resulting access grant (or simply `Enrollment`, with a `source: demoCheckout` field — an implementation choice for the later architecture phase, not decided here).

**Explicitly avoided:** no fake bank transaction, fake card, fake payment-processor response, or fake transaction ID modeled anywhere — those would only add fictitious realism without any product value, and risk reading as an attempt to simulate real payment infrastructure. The eventual backend only needs to record that a student successfully completed the demo checkout for a course and is now enrolled — no financial ledger, no fake receipt numbers tied to fake payment rails.

## 5. Course Price Display

Courses display realistic demo prices for presentation purposes only — e.g. **EGP 499**, **EGP 899**, **EGP 1,299**. Prices exist to make the catalog and checkout feel real in a demo; they are display/seed data, never wired to any billing logic. This is documented here and cross-referenced from `PRODUCT_SPEC.md § 9` ("What Mentora Intentionally Does Not Solve") so it is never mistaken for a pricing/monetization feature.

## 6. Demo Payment Error State

One optional, simple simulated failure state — included because it meaningfully improves the demo (shows the product handles failure gracefully) without adding real payment-error complexity:

- Trigger: simulated only (e.g., a demo "randomly fail 1-in-N" toggle, or a deliberate test affordance) — never tied to any real validation.
- Message: **"Demo checkout could not be completed. Try again."**
- Action: **[ Try Again ]** → returns to Demo Checkout.

No card-decline-style messaging, no billing-specific error taxonomy (insufficient funds, expired card, etc.) — those would misleadingly imply real payment processing exists. The happy path (§ 3) remains the primary, expected demo experience; this failure state is a secondary polish item, not a feature to build out further.

## 6.1 Bilingual Compliance *(v1.3, locked MVP)*

Every string in this flow — the demo-payment notice, "Complete Demo Purchase," the processing state, "Payment Successful," "You're now enrolled!," "Start Learning," the price display, and the error state's message/retry action — must be fully localizable to Arabic (`PRODUCT_SPEC.md § 16`), same as any other MVP screen. Two rules carry over unchanged into the Arabic version:

- **No real-charge implication in either language.** The Arabic demo-payment notice must make the "no real charge" fact exactly as unambiguous as the English one — a literal or loose translation that reads as more transactional/authoritative than the English source (e.g. implying a real bank/gateway step) is not acceptable. The DEMO_PAYMENT_FLOW's strict rules (§ 1) apply to the Arabic UI with zero relaxation — no financial-input fields, no real gateway references, regardless of language.
- **Locale-aware price formatting, not translation of the number system.** Prices use Western Arabic numerals in both languages (locked decision, `LOCALIZATION.md`) — e.g. an English UI shows "EGP 1,299," and the Arabic UI shows the equivalent RTL-laid-out, Arabic-labeled presentation of the same figure (currency label and grouping per Arabic locale conventions where they differ from English), never a re-translated numeral system. Exact formatting API is deferred to Technical Architecture — see `../design-system/LOCALIZATION.md § 7`.

This is a UI-string and layout-direction change only — the flow's steps, screens, and simulated nature (§ 2–4) are unchanged.

## 7. Cross-References

- Screen definition: `SCREEN_INVENTORY.md § 19–20` (Demo Checkout, Purchase Success)
- Flow definition: `USER_FLOWS.md § 8` (Demo Purchase / Simulated Checkout)
- Product framing: `PRODUCT_SPEC.md § 9, § 15`
- Components: Demo Checkout's order-summary layout uses the `Checkout`/`OrderSummary` component, and Purchase Success uses `SuccessState` — both added to [Mentora Design System v1.2](../design-system/COMPONENTS.md) after this document first flagged them as needed. Checkout's field set (course summary, demo price, demo-payment notice, confirm action — no financial fields) is enforced at the component-spec level, not just in this product document.
