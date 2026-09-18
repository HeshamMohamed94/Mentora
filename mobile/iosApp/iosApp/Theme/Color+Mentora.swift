// GENERATED — DO NOT EDIT.
// Source: design-system/design-tokens.json, design-system/themes/theme-{light,dark}.json
// Regenerate with: npm run generate (from tools/token-pipeline/), or node tools/token-pipeline/generate.js

import SwiftUI

/// One computed property per semantic color dot-path (design-tokens.json#/color/semantic),
/// reading MentoraColors.xcassets — "Any Appearance" is the light theme's value, "Dark" is the
/// dark theme's, so SwiftUI's automatic dark-mode resolution does the theme switch; no view ever
/// branches on colorScheme for these. Mirrors platform-contract.json#/ios/colorMapping.
extension Color {
    static var mentoraBackgroundPrimary: Color { Color("mentoraBackgroundPrimary") }
    static var mentoraBackgroundSecondary: Color { Color("mentoraBackgroundSecondary") }
    static var mentoraSurfaceDefault: Color { Color("mentoraSurfaceDefault") }
    static var mentoraSurfaceElevated: Color { Color("mentoraSurfaceElevated") }
    static var mentoraSurfaceVariant: Color { Color("mentoraSurfaceVariant") }
    static var mentoraSurfaceInverse: Color { Color("mentoraSurfaceInverse") }
    static var mentoraTextPrimary: Color { Color("mentoraTextPrimary") }
    static var mentoraTextSecondary: Color { Color("mentoraTextSecondary") }
    static var mentoraTextDisabled: Color { Color("mentoraTextDisabled") }
    static var mentoraTextInverse: Color { Color("mentoraTextInverse") }
    static var mentoraTextLink: Color { Color("mentoraTextLink") }
    static var mentoraBorderDefault: Color { Color("mentoraBorderDefault") }
    static var mentoraBorderStrong: Color { Color("mentoraBorderStrong") }
    static var mentoraBorderFocus: Color { Color("mentoraBorderFocus") }
    static var mentoraBorderError: Color { Color("mentoraBorderError") }
    static var mentoraBrandPrimary: Color { Color("mentoraBrandPrimary") }
    static var mentoraBrandPrimaryHover: Color { Color("mentoraBrandPrimaryHover") }
    static var mentoraBrandPrimaryPressed: Color { Color("mentoraBrandPrimaryPressed") }
    static var mentoraBrandPrimaryContainer: Color { Color("mentoraBrandPrimaryContainer") }
    static var mentoraBrandOnPrimary: Color { Color("mentoraBrandOnPrimary") }
    static var mentoraBrandOnPrimaryContainer: Color { Color("mentoraBrandOnPrimaryContainer") }
    static var mentoraBrandOnSurfaceInverse: Color { Color("mentoraBrandOnSurfaceInverse") }
    static var mentoraSecondaryDefault: Color { Color("mentoraSecondaryDefault") }
    static var mentoraSecondaryHover: Color { Color("mentoraSecondaryHover") }
    static var mentoraSecondaryContainer: Color { Color("mentoraSecondaryContainer") }
    static var mentoraSecondaryOnSecondary: Color { Color("mentoraSecondaryOnSecondary") }
    static var mentoraSecondaryOnSecondaryContainer: Color { Color("mentoraSecondaryOnSecondaryContainer") }
    static var mentoraAccentDefault: Color { Color("mentoraAccentDefault") }
    static var mentoraSuccessDefault: Color { Color("mentoraSuccessDefault") }
    static var mentoraSuccessContainer: Color { Color("mentoraSuccessContainer") }
    static var mentoraSuccessOnSuccess: Color { Color("mentoraSuccessOnSuccess") }
    static var mentoraSuccessOnSuccessContainer: Color { Color("mentoraSuccessOnSuccessContainer") }
    static var mentoraWarningDefault: Color { Color("mentoraWarningDefault") }
    static var mentoraWarningContainer: Color { Color("mentoraWarningContainer") }
    static var mentoraWarningOnWarning: Color { Color("mentoraWarningOnWarning") }
    static var mentoraWarningOnWarningContainer: Color { Color("mentoraWarningOnWarningContainer") }
    static var mentoraErrorDefault: Color { Color("mentoraErrorDefault") }
    static var mentoraErrorContainer: Color { Color("mentoraErrorContainer") }
    static var mentoraErrorOnError: Color { Color("mentoraErrorOnError") }
    static var mentoraErrorOnErrorContainer: Color { Color("mentoraErrorOnErrorContainer") }
    static var mentoraInfoDefault: Color { Color("mentoraInfoDefault") }
    static var mentoraInfoContainer: Color { Color("mentoraInfoContainer") }
    static var mentoraInfoOnInfo: Color { Color("mentoraInfoOnInfo") }
    static var mentoraInfoOnInfoContainer: Color { Color("mentoraInfoOnInfoContainer") }
    static var mentoraOverlayScrim: Color { Color("mentoraOverlayScrim") }
    static var mentoraOverlayChipScrim: Color { Color("mentoraOverlayChipScrim") }
}
