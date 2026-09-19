import Foundation
import shared

// Phase 5 Task T5 (slice 1 of 2) — see `MentoraError.swift`'s header for the `SWIFT_VERSION: "5.0"`
// / no-strict-concurrency note; it applies to this file too.

enum ApiResultBridge {

    /// `Success` -> the value; `Failure` -> a thrown `MentoraError`.
    /// `T: AnyObject` is required by Swift's own Obj-C-generics-import rules for a type parameter
    /// bridging to an imported Obj-C lightweight-generic class -- not specific to any particular
    /// dispatch mechanism. What IS our own explicit design choice (D115) is the dispatch mechanism
    /// itself: an `as?` chain through `Any`, checking `Failure` first, instead of SKIE's generated
    /// `onEnum(of:)`. See below and DECISIONS_LOG D115 for why.
    static func unwrap<T: AnyObject>(_ result: ApiResult<T>) throws -> T {
        // `let erased: Any` is required, not stylistic: SE-0057 makes a conditional cast between
        // two specializations of an imported Obj-C lightweight-generic class a compile error
        // ("conditional cast to specialized Objective-C instance doesn't check type argument").
        // Going through `Any` is a plain dynamic cast and is allowed -- the identical shape SKIE's
        // own generated `onEnum(of:)` uses for generic sealed classes.
        let erased: Any = result

        // Failure is checked FIRST and is the only one of the two checks with no type argument at
        // all (`ApiResult.Failure : ApiResult<Nothing>` is a non-generic class), so it cannot be
        // affected by Obj-C generic-argument erasure. See DECISIONS_LOG D115.
        if let failure = erased as? ApiResultFailure {
            throw MentoraError(failure: failure)
        }
        guard let success = erased as? ApiResultSuccess<T> else {
            throw MentoraError.unexpectedSubtype(String(describing: type(of: result)))
        }
        guard let data = success.data else {
            throw MentoraError.unexpectedNilData(String(describing: T.self))
        }
        return data
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

    /// Kotlin `Int?` PARAMETERS export as `KotlinInt?` -- real `.swiftinterface`:
    /// `invoke(cursor: Swift.String?, limit: shared.KotlinInt?)`. Boxed here, once, so `KotlinInt`
    /// never appears in a `Features/` file (A5) -- the parameter-direction twin of `unwrapInt`.
    /// `Int32(clamping:)` rather than `Int32(_:)`: a bridge must never trap on a caller value (I4).
    static func boxedInt(_ value: Int?) -> KotlinInt? {
        guard let value else { return nil }
        return KotlinInt(int: Int32(clamping: value))
    }

    /// `ApiResult<List<T>>` bridges as `ApiResult<NSArray>` -- element type is erased by Kotlin/Native
    /// for a generic class. Re-typed here, once, so no screen ever casts.
    ///
    /// Review fix round (D112): a count mismatch used to only `assert` -- which compiles out entirely
    /// under `-O` (Release builds), so a wrong element type (or a duplicate-runtime cast
    /// inconsistency, see `project.yml`'s `iosAppTests` `link: false` comment) would have silently
    /// returned a shorter, possibly empty, array with no error and no retry affordance (an I4
    /// violation). Now throws unconditionally. D115: the `assert` that used to sit alongside the
    /// `guard` was removed -- in a Debug build (what CI's `xcodebuild build -configuration Debug`
    /// produces), `assert` fires and aborts the process BEFORE the `throw` is ever reached, making
    /// the cast-failure path untestable and reintroducing exactly the hard-abort-instead-of-throw
    /// problem this fix round exists to close (I4).
    static func unwrapList<Element>(_ result: ApiResult<NSArray>, as: Element.Type = Element.self) throws -> [Element] {
        let raw = try unwrap(result)
        let mapped = raw.compactMap { $0 as? Element }
        guard mapped.count == raw.count else {
            throw MentoraError.elementCastFailed("\(Element.self) (expected \(raw.count), got \(mapped.count))")
        }
        return mapped
    }

    /// `CursorPage<T>.items` is `NSArray<id>` -> `[Any]` because `CursorPage` is a generic class.
    /// Converted to a Swift `Page<Element>` here so no screen ever casts. Same real-throw fix as
    /// `unwrapList` above, for the identical reason. D115: the `assert` that used to sit alongside
    /// the `guard` was removed for the same reason as `unwrapList`'s (a Debug-build `assert` would
    /// abort before the `throw` is ever reached), even though no test currently exercises this path.
    static func unwrapPage<Element: AnyObject>(_ result: ApiResult<CursorPage<Element>>) throws -> Page<Element> {
        let page = try unwrap(result)
        let items = page.items.compactMap { $0 as? Element }
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
