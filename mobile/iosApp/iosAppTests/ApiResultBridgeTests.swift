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
        let result: ApiResult<NSString> = failure

        XCTAssertThrowsError(try ApiResultBridge.unwrap(result)) { error in
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

    func testUnwrapVoidThrowsOnFailure() {
        let failure = ApiResultFailure(code: ApiErrorCode.InternalError(), message: "m", fields: nil, httpStatus: 500)
        let result: ApiResult<KotlinUnit> = failure
        XCTAssertThrowsError(try ApiResultBridge.unwrapVoid(result))
    }

    func testUnwrapBoolTrue() throws {
        let result: ApiResult<KotlinBoolean> = ApiResultSuccess(data: KotlinBoolean(bool: true))
        XCTAssertTrue(try ApiResultBridge.unwrapBool(result))
    }

    func testUnwrapBoolFalse() throws {
        let result: ApiResult<KotlinBoolean> = ApiResultSuccess(data: KotlinBoolean(bool: false))
        XCTAssertFalse(try ApiResultBridge.unwrapBool(result))
    }

    // `unwrapInt` is present on `ApiResultBridge` for completeness/symmetry only -- no façade use
    // case returns `ApiResult<Int>` in this slice (see `ApiResultBridge.swift`'s own doc comment),
    // and `KotlinInt`'s exact Swift constructor spelling is not part of this slice's confirmed
    // ground truth, so it is deliberately left untested here rather than guessed.

    // MARK: - unwrapList

    func testUnwrapListDeErasesNSArrayToStringArray() throws {
        let result: ApiResult<NSArray> = ApiResultSuccess(data: ["a", "b"] as NSArray)
        let items: [String] = try ApiResultBridge.unwrapList(result)
        XCTAssertEqual(items, ["a", "b"])
    }

    func testUnwrapListThrowsOnFailure() {
        let failure = ApiResultFailure(code: ApiErrorCode.InternalError(), message: "m", fields: nil, httpStatus: 500)
        let result: ApiResult<NSArray> = failure
        XCTAssertThrowsError(try ApiResultBridge.unwrapList(result, as: String.self))
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
        let result: ApiResult<NSString>? = failure
        XCTAssertThrowsError(try ApiResultBridge.unwrapOptional(result))
    }
}
