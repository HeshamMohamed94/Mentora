import Foundation
import shared

// Phase 5 Task T5 (slice 1 of 2) — see `MentoraError.swift`'s header for the `SWIFT_VERSION: "5.0"`
// / no-strict-concurrency note; it applies to this file too.

enum ApiResultBridge {

    /// `Success` -> the value; `Failure` -> a thrown `MentoraError`.
    /// `T: AnyObject` is forced by SKIE's own generated `onEnum(of:)` constraint, not a choice made here.
    static func unwrap<T: AnyObject>(_ result: ApiResult<T>) throws -> T {
        switch onEnum(of: result) {
        case .success(let success):
            guard let data = success.data else {
                throw MentoraError.unexpectedNilData(String(describing: T.self))
            }
            return data
        case .failure(let failure):
            throw MentoraError(failure: failure)
        }
    }

    /// Optional-returning use cases (`RefreshPlaybackUrlUseCase`, `ReportPlaybackPositionUseCase`).
    /// nil in == nil out. A nil result means "the use case deliberately did nothing" and MUST NOT
    /// become an error, a retry, or a Swift-side timer (F6).
    static func unwrapOptional<T: AnyObject>(_ result: ApiResult<T>?) throws -> T? {
        guard let result else { return nil }
        return try unwrap(result)
    }

    /// `ApiResult<Unit>` -> `Void`. `KotlinUnit` never escapes this file.
    static func unwrapVoid(_ result: ApiResult<KotlinUnit>) throws { _ = try unwrap(result) }

    /// `ApiResult<Boolean>` -> `Bool`.
    static func unwrapBool(_ result: ApiResult<KotlinBoolean>) throws -> Bool { try unwrap(result).boolValue }

    /// Present for completeness/symmetry; no façade use case returns `ApiResult<Int>` in this slice.
    static func unwrapInt(_ result: ApiResult<KotlinInt>) throws -> Int { Int(try unwrap(result).int32Value) }

    /// `ApiResult<List<T>>` bridges as `ApiResult<NSArray>` -- element type is erased by Kotlin/Native
    /// for a generic class. Re-typed here, once, so no screen ever casts.
    ///
    /// Review fix round (D112): a count mismatch used to only `assert` -- which compiles out entirely
    /// under `-O` (Release builds), so a wrong element type (or a duplicate-runtime cast
    /// inconsistency, see `project.yml`'s `iosAppTests` `link: false` comment) would have silently
    /// returned a shorter, possibly empty, array with no error and no retry affordance (an I4
    /// violation). Now throws unconditionally; the `assert` stays too, purely for a louder debug-time
    /// signal.
    static func unwrapList<Element>(_ result: ApiResult<NSArray>, as: Element.Type = Element.self) throws -> [Element] {
        let raw = try unwrap(result)
        let mapped = raw.compactMap { $0 as? Element }
        assert(mapped.count == raw.count, "SharedBridge: \(raw.count - mapped.count) element(s) of \(Element.self) failed to cast -- the bridge method names the wrong element type.")
        guard mapped.count == raw.count else {
            throw MentoraError.elementCastFailed("\(Element.self) (expected \(raw.count), got \(mapped.count))")
        }
        return mapped
    }

    /// `CursorPage<T>.items` is `NSArray<id>` -> `[Any]` because `CursorPage` is a generic class.
    /// Converted to a Swift `Page<Element>` here so no screen ever casts. Same real-throw-not-just-
    /// `assert` fix as `unwrapList` above, for the identical reason.
    static func unwrapPage<Element: AnyObject>(_ result: ApiResult<CursorPage<Element>>) throws -> Page<Element> {
        let page = try unwrap(result)
        let items = page.items.compactMap { $0 as? Element }
        assert(items.count == page.items.count, "SharedBridge: CursorPage<\(Element.self)> element cast lost items.")
        guard items.count == page.items.count else {
            throw MentoraError.elementCastFailed("CursorPage<\(Element.self)> (expected \(page.items.count), got \(items.count))")
        }
        return Page(items: items, nextCursor: page.nextCursor)
    }
}

/// The Swift-native replacement for `shared.CursorPage<T>`, whose `items` erase to `[Any]`.
/// Screens see `Page<CourseSummary>`, never `CursorPage`, and never cast.
struct Page<Element> {
    let items: [Element]
    let nextCursor: String?
    var hasMore: Bool { nextCursor != nil }
}
