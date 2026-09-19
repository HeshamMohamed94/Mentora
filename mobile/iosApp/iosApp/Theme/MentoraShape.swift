import SwiftUI

// MentoraShape is radius-STEP-keyed (design-tokens.json#/shape/radius), never component-role-keyed
// (no .button/.card cases) -- System Design § 15.2 forbids a component-token layer. One concrete
// InsettableShape struct, not an enum-of-AnyShape, because AnyShape (iOS 17) does not conform to
// InsettableShape, which would force .stroke() instead of .strokeBorder() and render half the
// border width outside the layout bounds (diverging from Android/web's border-box convention).
// Uses RoundedRectangle style: .circular (NOT .continuous) because platform-mapping.md (LOCKED)
// specifies bare RoundedRectangle(cornerRadius:), whose default is .circular, matching Compose's
// RoundedCornerShape and CSS border-radius -- .continuous squircles would be an unmandated
// cross-platform visual divergence.

/// `shape.radius` (design-tokens.json#/shape/radius) as a concrete, insettable SwiftUI `Shape`.
struct MentoraShape: InsettableShape, Equatable {
    enum Step: String, CaseIterable {
        case none, small, medium, large, xlarge, sheetTop, full
    }

    let step: Step
    private var insetAmount: CGFloat = 0

    init(_ step: Step) {
        self.step = step
    }

    /// `.sheetTop` deliberately shares `.xlarge`'s radius (24) -- design-tokens.json's
    /// `componentUsage.bottomSheet` is "radius.xlarge (24) top corners only", not a distinct value.
    var cornerRadius: CGFloat {
        switch step {
        case .none: return MentoraRadius.none
        case .small: return MentoraRadius.small
        case .medium: return MentoraRadius.medium
        case .large: return MentoraRadius.large
        case .xlarge, .sheetTop: return MentoraRadius.xlarge
        case .full: return MentoraRadius.full
        }
    }

    func path(in rect: CGRect) -> Path {
        let r = max(0, cornerRadius - insetAmount)
        let insetRect = rect.insetBy(dx: insetAmount, dy: insetAmount)
        switch step {
        case .full:
            return Capsule(style: .circular).path(in: insetRect)
        case .sheetTop:
            return UnevenRoundedRectangle(
                topLeadingRadius: r, bottomLeadingRadius: 0,
                bottomTrailingRadius: 0, topTrailingRadius: r,
                style: .circular
            ).path(in: insetRect)
        default:
            return RoundedRectangle(cornerRadius: r, style: .circular).path(in: insetRect)
        }
    }

    func inset(by amount: CGFloat) -> MentoraShape {
        var copy = self
        copy.insetAmount += amount
        return copy
    }

    static let none = MentoraShape(.none)
    static let small = MentoraShape(.small)
    static let medium = MentoraShape(.medium)
    static let large = MentoraShape(.large)
    static let xlarge = MentoraShape(.xlarge)
    static let sheetTop = MentoraShape(.sheetTop)
    static let full = MentoraShape(.full)
}
