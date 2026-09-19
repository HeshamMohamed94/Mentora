import XCTest
@testable import iosApp
import shared

// Phase 5 Task T5 (slice 1 of 2) — unit tests over every `ApiResultBridge` unwrap branch, built
// from real Kotlin constructors (`ApiResultSuccess`/`ApiResultFailure`/`CursorPage`) -- no SDK, no
// network needed. Real constructor/case-name spellings are per `mobile/shared/src/commonMain/kotlin/
// com/mentora/shared/data/network/{ApiResult,ApiErrorCode,CursorPage}.kt`; if any spelling here is
// wrong, the compiler names the exact right one (a one-line fix — this file has never compiled on
// this Windows host).
final class ApiResultBridgeTests: XCTestCase {

    // MARK: - unwrap

    func testUnwrapSuccessReturnsValue() throws {
        let result: ApiResult<NSString> = ApiResultSuccess(data: "x")
        let value = try ApiResultBridge.unwrap(result)
        XCTAssertEqual(value, "x")
    }

    func testUnwrapFailureThrowsMentoraErrorWithFieldsAndHttpStatus() {
        let failure = ApiResultFailure(
            code: ApiErrorCode.ValidationError(),
            message: "m",
            fields: ["email": "REQUIRED"],
            httpStatus: 422
        )
        XCTAssertThrowsError(try ApiResultBridge.unwrap(failure)) { error in
            guard let mentoraError = error as? MentoraError else {
                XCTFail("Expected MentoraError, got \(error)")
                return
            }
            XCTAssertEqual(mentoraError.wire, "VALIDATION_ERROR")
            XCTAssertEqual(mentoraError.fields, ["email": "REQUIRED"])
            XCTAssertEqual(mentoraError.httpStatus, 422)
        }
    }

    // MARK: - unwrapVoid / unwrapBool / unwrapInt

    func testUnwrapVoidDoesNotThrowOnSuccess() throws {
        let result: ApiResult<KotlinUnit> = ApiResultSuccess(data: KotlinUnit.shared)
        XCTAssertNoThrow(try ApiResultBridge.unwrapVoid(result))
    }

    // `unwrapVoid`'s failure path is `unwrap`'s failure path verbatim (`_ = try unwrap(result)`),
    // covered directly by `testUnwrapFailureThrowsMentoraErrorWithFieldsAndHttpStatus`. Keeping
    // `unwrapVoid`'s concrete `ApiResult<KotlinUnit>` parameter (reverted, see DECISIONS_LOG D115)
    // means a dedicated failure test here would need the same kind of force-cast into a mismatched
    // type that this fix round removed everywhere else -- deleted for the same reason as
    // `testUnwrapListThrowsOnFailure` above.

    func testUnwrapBoolTrue() throws {
        let result: ApiResult<KotlinBoolean> = ApiResultSuccess(data: KotlinBoolean(bool: true))
        XCTAssertTrue(try ApiResultBridge.unwrapBool(result))
    }

    func testUnwrapBoolFalse() throws {
        let result: ApiResult<KotlinBoolean> = ApiResultSuccess(data: KotlinBoolean(bool: false))
        XCTAssertFalse(try ApiResultBridge.unwrapBool(result))
    }

    // MARK: - unwrapInt / boxedInt (T5 slice 2)
    //
    // `KotlinInt`'s constructor spelling was deliberately left untested in slice 1 ("not part of that
    // slice's confirmed ground truth"). Slice 2 makes it production code -- every paged use case's
    // `limit:` parameter is a `KotlinInt?` -- so it is tested for real here, in both directions.

    func testUnwrapIntUnboxesKotlinInt() throws {
        let result: ApiResult<KotlinInt> = ApiResultSuccess(data: KotlinInt(int: 7))
        XCTAssertEqual(try ApiResultBridge.unwrapInt(result), 7)
    }

    func testBoxedIntNilStaysNil() {
        XCTAssertNil(ApiResultBridge.boxedInt(nil))
    }

    func testBoxedIntRoundTripsThroughKotlinInt() throws {
        let boxed = try XCTUnwrap(ApiResultBridge.boxedInt(20))
        XCTAssertEqual(boxed.int32Value, 20)
    }

    /// I4: a nonsensical caller value must clamp, never trap the process.
    func testBoxedIntClampsRatherThanTrapping() throws {
        let boxed = try XCTUnwrap(ApiResultBridge.boxedInt(Int(Int32.max) + 1))
        XCTAssertEqual(boxed.int32Value, Int32.max)
    }

    // MARK: - unwrapList

    func testUnwrapListDeErasesNSArrayToStringArray() throws {
        let result: ApiResult<NSArray> = ApiResultSuccess(data: ["a", "b"] as NSArray)
        let items: [String] = try ApiResultBridge.unwrapList(result)
        XCTAssertEqual(items, ["a", "b"])
    }

    // `unwrapList`'s failure path is `unwrap`'s failure path verbatim (`let raw = try unwrap(result)`),
    // covered directly by `testUnwrapFailureThrowsMentoraErrorWithFieldsAndHttpStatus`. A dedicated
    // `unwrapList` failure test would require force-casting a bare `ApiResultFailure` into a
    // mismatched `ApiResult<NSArray>` -- deleted in D115 as a statically false claim about the value.
    // The genuinely untested branch is D112's element-cast throw, covered here instead.
    // (Same reasoning applies to `unwrapPage`: its own failure path is skipped for the identical
    // "already covered by `unwrap`'s own failure test" reason above -- NOT because `.items` is
    // statically typed by `Element`. It isn't: `CursorPage<Element>.items` erases to `[Any]` just
    // like `unwrapList`'s `NSArray`, per `ApiResultBridge.swift`'s own doc comment on `unwrapPage`
    // ("`CursorPage<T>.items` is `NSArray<id>` -> `[Any]` because `CursorPage` is a generic class"),
    // which is exactly why `unwrapPage`'s own code does `page.items.compactMap { $0 as? Element }`.)
    func testUnwrapListThrowsWhenAnElementIsNotTheNamedType() {
        let result: ApiResult<NSArray> = ApiResultSuccess(data: ["a", 1] as NSArray)
        XCTAssertThrowsError(try ApiResultBridge.unwrapList(result, as: String.self)) { error in
            guard let mentoraError = error as? MentoraError else {
                XCTFail("Expected MentoraError, got \(error)")
                return
            }
            XCTAssertEqual(mentoraError.wire, "IOS_BRIDGE_ELEMENT_CAST_FAILED")
        }
    }

    // MARK: - unwrapPage

    func testUnwrapPageDeErasesItemsAndCarriesNextCursor() throws {
        let page = CursorPage<NSString>(items: ["a", "b"] as [NSString], nextCursor: "cur")
        let result: ApiResult<CursorPage<NSString>> = ApiResultSuccess(data: page)
        let bridged: Page<NSString> = try ApiResultBridge.unwrapPage(result)
        XCTAssertEqual(bridged.items, ["a", "b"])
        XCTAssertEqual(bridged.nextCursor, "cur")
        XCTAssertTrue(bridged.hasMore)
    }

    func testUnwrapPageNilNextCursorMeansNoMore() throws {
        let page = CursorPage<NSString>(items: ["a"] as [NSString], nextCursor: nil)
        let result: ApiResult<CursorPage<NSString>> = ApiResultSuccess(data: page)
        let bridged: Page<NSString> = try ApiResultBridge.unwrapPage(result)
        XCTAssertNil(bridged.nextCursor)
        XCTAssertFalse(bridged.hasMore)
    }

    /// D115 (d) D4 deliberately left `unwrapPage`'s element-cast-mismatch throw untested while NO
    /// production code called `unwrapPage` at all. Slice 2 gives it four real call sites
    /// (`searchCourses`/`enrollments`/`myLearning`/`certificates`), so the gap is closed now --
    /// through the already-proven `Success` path, with no force-cast anywhere (D115 (g): a
    /// `Failure`-to-mismatched-`ApiResult<T>` force-cast is a CONFIRMED runtime trap; never write one).
    func testUnwrapPageThrowsWhenAnElementIsNotTheNamedType() {
        let page = CursorPage<NSString>(items: ["a" as NSString, NSNumber(value: 1)] as [Any], nextCursor: nil)
        let result: ApiResult<CursorPage<NSString>> = ApiResultSuccess(data: page)
        XCTAssertThrowsError(try ApiResultBridge.unwrapPage(result)) { error in
            guard let mentoraError = error as? MentoraError else {
                XCTFail("Expected MentoraError, got \(error)")
                return
            }
            XCTAssertEqual(mentoraError.wire, "IOS_BRIDGE_ELEMENT_CAST_FAILED")
        }
    }

    // MARK: - unwrapOptional

    func testUnwrapOptionalNilInputReturnsNilWithoutThrowing() throws {
        let result: ApiResult<NSString>? = nil
        XCTAssertNil(try ApiResultBridge.unwrapOptional(result))
    }

    func testUnwrapOptionalSuccessReturnsValue() throws {
        let result: ApiResult<NSString>? = ApiResultSuccess(data: "x")
        XCTAssertEqual(try ApiResultBridge.unwrapOptional(result), "x")
    }

    func testUnwrapOptionalFailureThrows() {
        let failure = ApiResultFailure(code: ApiErrorCode.InternalError(), message: "m", fields: nil, httpStatus: 500)
        XCTAssertThrowsError(try ApiResultBridge.unwrapOptional(failure)) { error in
            guard let mentoraError = error as? MentoraError else {
                XCTFail("Expected MentoraError, got \(error)")
                return
            }
            XCTAssertEqual(mentoraError.wire, "INTERNAL_ERROR")
        }
    }
}
