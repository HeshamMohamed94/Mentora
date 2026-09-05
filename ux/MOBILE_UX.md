# Mentora UX — Mobile (Android & iOS) Experience

The native Student experience — Android and iOS share this exact UX specification; platform differences are limited to native interaction conventions (gestures, system pickers, share sheets), never to layout or visual identity, per `../design-system/DESIGN_RULES.md` rule 10. **This is the same Mentora product as `WEB_UX.md`** — same colors, same type scale, same component contracts, same feature set for Students — wearing mobile-appropriate navigation instead of Web's Navbar/Sidebar.

Full per-screen field-by-field specs live in [`SCREEN_UX_SPECS.md`](./SCREEN_UX_SPECS.md). This file defines the mobile navigation/layout system and any mobile-specific interaction pattern (the Curriculum Bottom Sheet, keyboard behavior, etc.).

---

## 1. Bottom Navigation Behavior

Locked 5-item `MobileBottomNavigation`: **Home, Explore, My Learning, AI Tutor, Profile** (`../design-system/COMPONENTS.md`, non-negotiable per this task's brief).

- **Persistence:** always visible on every tab-root screen; hidden (or unobtrusively present, platform-convention-dependent) on immersive pushed screens where it would compete for space — Course Player and Quiz hide it; Course Details, My Learning's course list, Certificates, and Settings keep it visible since they're still "browsing," not "doing."
- **Tap behavior:** tapping the active tab's icon while already on that tab's root pops that tab's stack back to root (standard convention — lets a user "get back to the top" of a tab without using system-back repeatedly).
- **Badge/indicator:** none in MVP (no unread-count or notification badge — Mentora has no notification system in MVP, per `../product/MVP_SCOPE.md`).

## 2. Screen Hierarchy & Back Navigation

Full graph in [`NAVIGATION_SPEC.md § 3`](./NAVIGATION_SPEC.md). Summary of the interaction model:

- Each of the 5 tabs owns an independent push/pop stack (a `CourseCard` tap in Explore pushes Course Details; that push doesn't affect My Learning's stack).
- **Android:** hardware/gesture back pops the current tab's stack one level; at a tab root, back either exits the app or (optionally, a product polish decision, not required for MVP) shows a "press back again to exit" convention.
- **iOS:** edge-swipe-back pops the current tab's stack one level; there is no system back button at a tab root (iOS convention — the user switches tabs instead).
- **Modal/full-screen contexts** (AppDialog, BottomSheet, Login/Register pre-auth) dismiss via their own explicit close affordance or a swipe-down (BottomSheet) rather than the tab stack's back behavior — see `../design-system/COMPONENTS.md § Dialogs, Sheets, Feedback`.

## 3. Mobile Course Discovery

Explore tab: `SearchField` pinned near the top, filter chips scrollable in a row below it, course grid below that (1 column at Mobile width per `RESPONSIVE_BEHAVIOR.md § 3`). Learning Paths live as a segment/tab within Explore (not a 6th bottom-nav item — `../product/INFORMATION_ARCHITECTURE.md § 3`).

## 4. Course Details (Mobile)

Same content hierarchy as Web's Course Details (`WEB_UX.md § 2`) in a single scrolling column: thumbnail → title/instructor/price → primary CTA (sticky at the bottom of the viewport once the user scrolls past it, so "Enroll"/"Continue Learning" is always one thumb-reach away) → curriculum outline → instructor detail.

## 5. Demo Checkout (Mobile)

Single-column `Checkout`/`OrderSummary` card, full-width, per `RESPONSIVE_BEHAVIOR.md § 10`. Confirm action ("Complete Demo Purchase") is full-width and positioned so it's reachable without scrolling past the fold on typical device heights, since the order summary content is intentionally short.

## 6. Course Player (Mobile)

**Video at the top, full width, 16:9.** Below it, in this order: lesson title → short description → Previous/Next lesson controls → completion action → resources. **Curriculum lives in a `BottomSheet`**, triggered by a "Curriculum" affordance (e.g. an icon button near the video or a persistent small handle), rather than permanently consuming horizontal screen space the way Web's sidebar does — this is the single largest layout divergence from Web, and it's a *platform-appropriate* one (mobile has no room for a persistent side panel), not a visual-identity divergence (the Bottom Sheet itself, its content rows, and its typography are the same Mentora components used everywhere else).

The video scrubber **stays left-to-right even in an RTL layout** — `../design-system/LOCALIZATION.md § 3` and `COMPONENTS.md § Media & Playback`, unchanged on mobile.

## 7. Curriculum Bottom Sheet

- **Trigger:** tapping the "Curriculum" affordance in Course Player.
- **Content:** the same section/lesson list Web shows in its persistent sidebar — section headers, lesson rows with per-lesson completion state (`Badge`), current lesson highlighted.
- **Interaction:** tapping a lesson row navigates the player to that lesson and dismisses the sheet (swipe-down or scrim-tap also dismisses without navigating).
- **Height:** a partial sheet by default (shows several lessons, scrollable) with a drag handle to expand toward full-height if the course has many sections — standard `BottomSheet` behavior, not a custom variant.

## 8. Quiz (Mobile)

Full-screen, bottom nav hidden (§ 2) — one question per screen, `QuizCard` + progress indicator, Next/Submit `PrimaryButton` pinned at the bottom. Identical content/state model to Web's Quiz (`SCREEN_UX_SPECS.md § Quiz`), just single-column and full-width.

## 9. Progress

No dedicated "Progress" screen — progress is a property shown *within* other screens (Course Player's `ProgressBar`, My Learning's per-course progress, Dashboard/Home's "Continue learning" module, Learning Path Details' path-level progress), consistent with `../product/SCREEN_INVENTORY.md` (no such screen exists in the 29). Mentioned here only to confirm this isn't a mobile-specific omission.

## 10. AI Tutor (Mobile)

Full-screen tab (§ `RESPONSIVE_BEHAVIOR.md § 9`) — chat thread, quick-action row (horizontally scrollable, above the input), message input pinned above the on-screen keyboard. See § 12 for keyboard behavior.

## 11. Certificate (Mobile)

Certificates list nested under My Learning (`../product/INFORMATION_ARCHITECTURE.md § 3`); Certificate Detail is a pushed full-screen view of the certificate artifact with a "Share" action using the native OS share sheet (still a UI-only affordance — `../product/DEMO_PAYMENT_FLOW.md`-style constraint doesn't apply here, but the LinkedIn integration itself remains unbuilt per `../product/PRODUCT_SPEC.md § 13`).

## 12. Learning Paths (Mobile)

Nested under Explore (§ 3). Learning Path Details shows the same ordered course sequence and path-level `ProgressBar` as Web, in a single scrolling column.

## 13. Profile (Mobile)

Tab root — avatar, name, email, stats, "Edit Profile," and an entry into Settings (pushed). Logout lives in Settings or directly on Profile (single, consistent placement — Profile, matching Web's pattern of Logout being reachable from the account surface).

**Language selector *(v1.3, locked MVP):*** lives inside Settings (pushed from Profile), not the Bottom Navigation bar — the 5-item tab set (`§ 1`) is unchanged. Same functional English/العربية `Select` and immediate-apply behavior as Web (`SCREEN_UX_SPECS.md § 17`, `../product/PRODUCT_SPEC.md § 16`).

## 14. Mobile Keyboard Behavior (Conceptual)

- Any screen with a text input (Login, Register, AI Tutor's message field, Instructor forms if ever used on a resized mobile browser) shifts its focused input above the keyboard rather than letting the keyboard obscure it — standard platform-native keyboard-avoidance behavior, not a custom Mentora pattern.
- AI Tutor's input stays pinned directly above the keyboard when it's open, with the message thread scrollable behind it — sending a message keeps the keyboard open (so a Student can send several quick follow-ups without repeated taps) unless they explicitly dismiss it.
- Quiz and other non-text-input screens are unaffected (no keyboard-avoidance logic needed).

---

## 15. Visual Consistency Confirmation

Every component named above — `VideoPlayer`, `BottomSheet`, `QuizCard`, `AITutorBubble`, `CertificateCard`, `LearningPathCard`, `CourseProgressCard` — is the exact same [Mentora Design System v1.3.1](../design-system/DESIGN_SYSTEM.md) component Web uses, styled from the same tokens. The only things that differ between `WEB_UX.md` and this file are **navigation shape** (Sidebar/Navbar vs. bottom tabs) and a small number of **platform-appropriate layout decisions** (Curriculum Bottom Sheet instead of a persistent sidebar; single-column instead of two-column Course Player) — both explicitly sanctioned by `../design-system/DESIGN_RULES.md` rule 10 ("platform-specific UX differences are allowed where they improve usability"). Nothing here introduces a new color, radius, motion style, or component variant that Web doesn't also have access to.
