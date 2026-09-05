# Mentora — Feature / Platform Matrix (MVP)

Legend: ✓ full · ◐ partial/read-only · — not applicable/not present. "Public Web" = unauthenticated Guest experience on the website. "Student Web/Android/iOS" = authenticated Student experience per client. "Instructor/Admin Web" = the Web-only management surfaces (two roles, one column, differences noted).

| Feature | Public Web | Student Web | Android | iOS | Instructor/Admin Web |
|---|:---:|:---:|:---:|:---:|:---:|
| **Authentication** |
| Register | ✓ | — (already authed) | ✓ | ✓ | ✓ (own account) |
| Login | ✓ | — | ✓ | ✓ | ✓ |
| Logout | — | ✓ | ✓ | ✓ | ✓ |
| Forgot/reset password | — *(Post-MVP)* | — | — *(Post-MVP)* | — *(Post-MVP)* | — *(Post-MVP)* |
| **Course Discovery** |
| Browse catalog | ✓ | ✓ | ✓ | ✓ | ◐ (Admin: all courses incl. Draft; Instructor: own only) |
| Search | ✓ | ✓ | ✓ | ✓ | ✓ (Admin course search) |
| Filter | ✓ | ✓ | ✓ | ✓ | ✓ (Admin course filters) |
| **Course Details** |
| View course details | ✓ | ✓ | ✓ | ✓ | ◐ (read-only preview, Admin moderation / Instructor own-course view) |
| Curriculum outline preview | ✓ | ✓ | ✓ | ✓ | — (Instructor sees full editor instead) |
| **Demo Checkout** |
| Demo purchase / simulated checkout | — (must auth first) | ✓ | ✓ | ✓ | — |
| **Enrollment** |
| Enrollment created/tracked | — | ✓ | ✓ | ✓ | ◐ (Instructor/Admin see aggregate counts, not the flow itself) |
| **Learning / Player** |
| Course player (video, lessons) | — | ✓ | ✓ | ✓ | — |
| Lesson resources | — | ✓ | ✓ | ✓ | — |
| **Progress** |
| Progress tracking | — | ✓ | ✓ | ✓ | ◐ (Instructor sees aggregate completion rate, not per-student detail) |
| Resume learning | — | ✓ | ✓ | ✓ | — |
| **Quiz** |
| Take quiz | — | ✓ | ✓ | ✓ | — |
| Quiz results | — | ✓ | ✓ | ✓ | — |
| Author quiz questions | — | — | — | — | ✓ (Instructor) |
| **Certificates** |
| Earn/view certificate | — | ✓ | ✓ | ✓ | — |
| Share certificate (UI-only) | — | ✓ | ✓ | ✓ | — |
| **AI Tutor** |
| Chat + quick actions | — | ✓ | ✓ | ✓ | — |
| **Learning Paths** |
| Browse paths | ✓ | ✓ | ✓ | ✓ | — |
| Follow path / track progress | — | ✓ | ✓ | ✓ | — |
| Author/curate paths | — | — | — | — | — *(Post-MVP — curated as seed data; see `MVP_SCOPE.md`)* |
| **Profile** |
| View/edit profile | — | ✓ | ✓ | ✓ | ✓ (own account, minimal) |
| Settings | — | ✓ | ✓ | ✓ | ✓ (account/password + language, v1.3 — see Localization below) |
| **Instructor Course Management** |
| Create/edit course | — | — | — | — | ✓ (Instructor) |
| Manage sections/lessons | — | — | — | — | ✓ (Instructor) |
| Reorder curriculum | — | — | — | — | ✓ (Instructor) |
| Publish/unpublish own course | — | — | — | — | ✓ (Instructor) |
| Own-course basic stats | — | — | — | — | ✓ (Instructor) |
| **Admin Features** |
| Platform overview (counts) | — | — | — | — | ✓ (Admin) |
| Manage all courses / moderate | — | — | — | — | ✓ (Admin) |
| Manage users | — | — | — | — | ✓ (Admin) |
| Manage instructors | — | — | — | — | ✓ (Admin) |
| Manage categories | — | — | — | — | ✓ (Admin) |
| **Localization *(v1.3, locked MVP)*** |
| Functional language selector (English/العربية) | ✓ (Guest, local pref) | ✓ | ✓ | ✓ | ✓ |
| Localized UI strings (nav, buttons, forms, errors, empty/loading/success states) | ✓ | ✓ | ✓ | ✓ | ✓ |
| RTL layout (mirrored where semantically directional) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Locale-aware date/number/duration/price formatting | ✓ | ✓ | ✓ | ✓ | ✓ |
| Course-content translation (any) | — *(out of scope — see `PRODUCT_SPEC.md § 16`)* | — | — | — | — |

---

## Platform-Difference Notes

- **Instructor course creation is Web-only, by design** (`USER_ROLES.md`) — not listed on Android/iOS columns at all, rather than shown as a limitation, since it was never intended for mobile.
- **Student learning (discovery → purchase → player → progress → quiz → certificate → AI Tutor) is fully symmetric across Web, Android, and iOS** — the one role/journey where all three client columns must always read identically. Any future divergence here would be a defect against `PRODUCT_SPEC.md § 10`, not a product decision.
- **Guest discovery (browse/search/filter/course details/Learning Paths browse) is available on Public Web *and* the mobile apps pre-login** (`USER_ROLES.md`) — the "Public Web" column's ✓ rows for discovery are mirrored on Android/iOS before authentication, not exclusive to Web.
- **Instructor and Admin share the "Instructor/Admin Web" column** but are separate roles/sidebars (`INFORMATION_ARCHITECTURE.md § 4`) — a ✓ in that column means "the role that owns this feature has it," not that both roles share every row (e.g., Admin cannot author a course; Instructor cannot manage categories).
