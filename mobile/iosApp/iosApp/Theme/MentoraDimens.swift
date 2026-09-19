import CoreGraphics

// Phase 5 Task T8 slice 1 (Component Kit A, atoms) — hand-authored (NOT generated) Avatar size
// tokens.
//
// Disclosed gap, mirroring Android's own precedent (`mobile/androidApp/.../theme/MentoraDimens.kt`'s
// `avatarSmall/Medium/Large/XLarge`, read directly, not copied blindly — Android is REFERENCE-ONLY on
// this project, never source of truth): `avatar.*` is confirmed genuinely present in
// `design-system/design-tokens.json#/avatar` (cross-checked directly against that JSON, not trusted
// solely from Android's Kotlin transcription — both were read: `"avatar": { "small": 24, "medium":
// 40, "large": 64, "xlarge": 96 }`, lines ~357-359), but `tools/token-pipeline/generate.js` never
// walks an `avatar.*` namespace into any generated constant (Swift OR Kotlin), so
// `Theme/MentoraTokens.swift` has nothing to re-export. Hardcoded here, once, traceable to
// `design-system/design-tokens.json#/avatar` / `design-system/COMPONENTS.md § Avatar`, rather than
// re-typed at `Components/Avatar.swift`'s call sites.
enum MentoraAvatarSize {
    case small
    case medium
    case large
    case xlarge

    /// `design-system/COMPONENTS.md § Avatar`: "Radius always `radius.full`" — every size renders as
    /// a full circle, so this is the one dimension an `Avatar` view needs per size (width == height).
    var diameter: CGFloat {
        switch self {
        case .small:  return 24
        case .medium: return 40
        case .large:  return 64
        case .xlarge: return 96
        }
    }
}
