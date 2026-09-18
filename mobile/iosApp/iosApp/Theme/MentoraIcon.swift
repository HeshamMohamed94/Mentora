// Source: mobile/androidApp/src/main/kotlin/com/mentora/android/ui/components/MentoraIcons.kt's
// `MentoraIconName` enum (42 entries) and its two `buildIcon(..., autoMirror = true)` call sites —
// which itself transcribes web/src/components/ui/icon.tsx's `IconName` union. Not GENERATED (no
// tool regenerates this), but hand-authored to mirror that exact Android enum/flag 1:1.
//
// PHASE_5_IOS_IMPLEMENTATION_PLAN.md T3: `design-tokens.json#/icon/directional`'s Material-Symbols
// snake_case mirrorInRtl/neverMirror lists are a DIFFERENT, mostly-unrelated glyph set from this
// 42-glyph hand-drawn set and are deliberately NOT reconciled against here — Android's actual
// two-icon `autoMirror` set (`arrowForward`, `arrowBack`) is the only source of truth for mirroring
// in this icon set, per an explicit, already-reviewed decision.

import SwiftUI

/// The 42 hand-drawn icon names, named identically (camelCase) to web's `IconName` union and
/// Android's `MentoraIconName` enum. Backs `MentoraIcons.xcassets/<rawValue>.imageset`.
enum MentoraIconName: String, CaseIterable {
    case dashboard, explore, myLearning, learningPaths, aiTutor, certificates, profile, settings,
        menu, logout, play, pause, volumeOn, volumeMuted, fullscreen, fullscreenExit, checkCircle,
        cancel, expandMore, expandLess, visibility, visibilityOff, search, close, add, delete,
        arrowUpward, arrowDownward, dragHandle, upload, courseAnalytics, courseDesign, courseCode,
        courseGrid, courseLayers, arrowForward, arrowBack, darkMode, lightMode, people, school,
        moreVert

    /// Exactly Android's `autoMirror = true` set — `arrowForward` and `arrowBack`, and nothing
    /// else. Encoded once as data here rather than scattered per-call-site logic, so `MentoraIcon`
    /// (and any future call site) can look it up instead of re-deciding it.
    var flipsForRightToLeftLayoutDirection: Bool {
        switch self {
        case .arrowForward, .arrowBack:
            return true
        default:
            return false
        }
    }
}

/// Renders a [MentoraIconName] from `MentoraIcons.xcassets`, applying RTL mirroring for the two
/// directional arrows per `flipsForRightToLeftLayoutDirection` above. Analogous to Android's
/// `MentoraIcon(name, contentDescription, modifier, size, tint)`.
struct MentoraIcon: View {
    let name: MentoraIconName
    var size: CGFloat = 24

    var body: some View {
        Image(name.rawValue)
            .renderingMode(.template)
            .resizable()
            .frame(width: size, height: size)
            .flipsForRightToLeftLayoutDirection(name.flipsForRightToLeftLayoutDirection)
    }
}
