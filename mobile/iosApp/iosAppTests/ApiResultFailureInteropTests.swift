import XCTest
@testable import iosApp
import shared

// Phase 5 Task T5 (slice 1) -- DIAGNOSTIC LADDER, added by D115.
// CI run #15 crashed the test host on every test that put a real `ApiResult.Failure` through the
// bridge, while every `Success` test on the identical generic instantiations passed. RESOLVED by CI
// run #16 (see DECISIONS_LOG D115 section (g)): a real crash report named the exact cause -- a genuine
// Swift runtime "failed cast" trap on `makeFailure() as! ApiResult<NSString>` (the same operation the
// since-removed D114 test code performed). This is CONFIRMED as a real, reproducible defect in that
// specific cast, but has ZERO production call sites (grep-confirmed: every real `ApiResult<T>` value
// arrives already correctly, concretely typed from a genuine SKIE call site; production code never
// force-casts one). The two stages that performed this now-proven-broken cast (former Stage 0 and
// Stage 4b) have been deleted -- they would crash identically, forever, with no further diagnostic
// value, since the question they existed to answer is answered. The remaining stages below (1-5, no
// cast involved) are kept as a real regression suite for the parts of this investigation that are
// still genuinely load-bearing: constructing/reading a `Failure`, building a `MentoraError` from one,
// and SKIE's `onEnum(of:)` dispatch on a *correctly, non-mismatched* typed value (both `Failure` and
// `Success`) -- none of which this investigation ever found to be broken.
final class ApiResultFailureInteropTests: XCTestCase {

    private func makeFailure(fields: [String: String]? = nil) -> ApiResultFailure {
        ApiResultFailure(code: ApiErrorCode.InternalError(), message: "m", fields: fields, httpStatus: 500)
    }

    /// Stage 1 -- can Swift construct a Kotlin `ApiResult.Failure` at all? No green test ever did.
    func testStage1ConstructsFailure() {
        XCTAssertNotNil(makeFailure())
    }

    /// Stage 2 -- can Swift read its properties back? Includes the `Map<String,String>?` bridge.
    func testStage2ReadsFailureProperties() {
        let nilFields = makeFailure()
        XCTAssertEqual(nilFields.message, "m")
        XCTAssertEqual(nilFields.httpStatus, 500)
        XCTAssertNil(nilFields.fields)
        XCTAssertEqual(nilFields.code.wire, "INTERNAL_ERROR")

        let withFields = makeFailure(fields: ["email": "REQUIRED"])
        XCTAssertEqual(withFields.fields, ["email": "REQUIRED"])
    }

    /// Stage 3 -- `MentoraError(failure:)`, with no bridge and no SKIE dispatch involved.
    func testStage3MentoraErrorFromFailure() {
        let error = MentoraError(failure: makeFailure(fields: ["email": "REQUIRED"]))
        XCTAssertEqual(error.wire, "INTERNAL_ERROR")
        XCTAssertEqual(error.fields, ["email": "REQUIRED"])
        XCTAssertEqual(error.httpStatus, 500)
    }

    /// Stage 4 -- SKIE's generated `onEnum(of:)` on a `Failure`, called directly (no cast -- the value
    /// is already correctly typed at construction). This is the one dispatch mechanism
    /// `ApiResultBridge.unwrap` no longer depends on after D115, but this test is kept as a direct
    /// regression check that `onEnum(of:)` itself still works fine for well-typed values -- the real
    /// CI run #16 defect (DECISIONS_LOG D115 (g)) turned out to be an unrelated cast failure, not
    /// anything wrong with this dispatch.
    func testStage4OnEnumOfFailureSelectsFailureCase() {
        switch onEnum(of: makeFailure()) {
        case .success:
            XCTFail("SKIE dispatched an ApiResult.Failure to the .success case")
        case .failure(let failure):
            XCTAssertEqual(failure.httpStatus, 500)
        }
    }

    // Former Stage 4b (mismatched-static-type dispatch) performed the same `as! ApiResult<NSString>`
    // cast as former Stage 0, deleted above -- confirmed (DECISIONS_LOG D115 (g)) to be a genuine Swift
    // runtime "failed cast" trap, not something `onEnum(of:)` itself ever gets a chance to mis-dispatch.
    // Deleted for the same reason: the cast fails before dispatch is ever reached, so this test could
    // never have actually exercised the mismatched-dispatch question it was designed to answer.

    /// Stage 5 -- control: the same dispatch on a `Success`, already proven green elsewhere.
    func testStage5OnEnumOfSuccessSelectsSuccessCase() {
        let success: ApiResult<NSString> = ApiResultSuccess(data: "x")
        switch onEnum(of: success) {
        case .success(let s): XCTAssertEqual(s.data, "x")
        case .failure:        XCTFail("SKIE dispatched an ApiResultSuccess to the .failure case")
        }
    }
}
