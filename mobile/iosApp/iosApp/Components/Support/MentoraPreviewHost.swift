import SwiftUI
import shared

// Phase 5 Task T8 slice 1 (Component Kit A, atoms) — the ONE sanctioned place outside
// MentoraApp.swift (and Theme/MentoraTokenGallery.swift, T6 slice 3c's pre-existing sanctioned
// exception) allowed to call `.mentoraTheme(theme:locale:)` (Theme/MentoraTheme.swift). Every
// Components/*.swift atom's `#Preview` needs to vary light/dark theming and en/ar locale — the ONLY
// legal way to do that, since `tools/ios-checks/theme-checks.js`'s Checks C1-C3 ban
// `.preferredColorScheme(` / `.environment(\.locale` / `.environment(\.layoutDirection` outside
// Theme/MentoraTheme.swift, and Check C4 (amended in this same slice) recognizes exactly two
// sanctioned `.mentoraTheme(` call sites outside MentoraApp.swift: the gallery, and THIS file.
//
// #if DEBUG-gated, matching Theme/MentoraTokenGallery.swift's own precedent exactly: this must NEVER
// be referenced from any non-DEBUG, non-preview production code path — it exists solely to back
// `#Preview` blocks and does not exist at all in a Release build.
//
// `import SwiftUI` and `import shared` are BOTH required directly, not merely transitively via
// another file's import elsewhere in this target — module imports are not transitive in Swift (the
// exact mistake an earlier T6 slice's review caught, per Theme/MentoraTheme.swift's own header
// comment) — `shared` is needed for `ThemePreference`/`AppLocale`.
//
// Routes every preview through the REAL production `.mentoraTheme(theme:locale:)` entry point
// rather than injecting `.environment(\.locale, ...)` or `.preferredColorScheme(...)` directly — the
// exact same precedent `Theme/MentoraTokenGallery.swift`'s own `galleryPreview(...)` already
// established for T6's preview harness. `theme`/`locale` are plain (non-optional) values;
// `.mentoraTheme(theme:locale:)`'s real signature (`Theme/MentoraTheme.swift`, read directly, not
// guessed) is `(theme: ThemePreference, locale: AppLocale?) -> some View`, so passing a non-optional
// `AppLocale` here promotes implicitly, per ordinary Swift optional-parameter calling convention.
// `ThemePreference`/`AppLocale` are plain frozen Swift enums bridged from Kotlin via SKIE
// (`.light`/`.dark`/`.system` and `.english`/`.arabic` — confirmed real shape, T6 slice 3b/D121).
//
// `title` is rendered via `Text(title)` — a VARIABLE, never a string literal — so
// `tools/ios-checks/localization-checks.js`'s Check B1 (which only fires when the character
// immediately following `Text('s opening paren, skipping whitespace, is a literal `"` — confirmed by
// reading that check's exact regex, `\bText\(\s*"`, directly) never flags this call site, no matter
// what literal string a caller passes as `title:`. This mirrors `MentoraTokenGallery.swift`'s own
// `Text(entry.name)` / `Text(style.rawValue)` pattern, and the same non-user-facing debug-text
// carve-out already recorded for that file (D122) applies here identically: `title` is always a
// hardcoded English debug label naming which preview variant is on screen (e.g.
// "Badge — Light / en"), never real app content shown to a user.

#if DEBUG

/// Wraps `content()` in the real `.mentoraTheme(theme:locale:)` + `.dynamicTypeSize(_:)` application,
/// with a small debug-only `title` header identifying the preview variant — the visual harness every
/// `Components/*.swift` atom's `#Preview` uses (`PHASE_5_IOS_IMPLEMENTATION_PLAN.md` T8's own
/// "previews double as the visual harness" approach). `dynamicTypeSize` defaults to `.large` (the
/// standard Dynamic Type size), matching the gallery's own "default" convention.
func MentoraPreviewHost<Content: View>(
    title: String,
    theme: ThemePreference,
    locale: AppLocale,
    dynamicTypeSize: DynamicTypeSize = .large,
    @ViewBuilder content: () -> Content
) -> some View {
    VStack(alignment: .leading, spacing: MentoraSpacing.space2) {
        Text(title)
            .mentoraFont(.caption)
            .foregroundStyle(Color.mentoraTextSecondary)
        content()
    }
    .padding(MentoraSpacing.space4)
    .frame(maxWidth: .infinity, alignment: .leading)
    .background(Color.mentoraBackgroundPrimary)
    .mentoraTheme(theme: theme, locale: locale)
    .dynamicTypeSize(dynamicTypeSize)
}

#endif
