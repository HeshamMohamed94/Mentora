import XCTest
@testable import iosApp
import shared

// Phase 5 Task T5 (slice 1) -- DIAGNOSTIC LADDER, added by D115.
// CI run #15 crashed the test host on every test that put a real `ApiResult.Failure` through the
// bridge, while every `Success` test on the identical generic instantiations passed. That localises
// the defect to an `ApiResultFailure` INSTANCE, not to the caller's static `T`. Each test below adds
// exactly one step over the previous one, so which of them crash names the failing stage precisely.
// There are now 7 stages total (0 through 5, with 4b as an extra insertion between 4 and 5). Stage 0
// and Stage 4b were added in a review round (both an Opus review and an independent Codex review) to
// close a gap in the original 1-5 ladder: none of the original stages actually reproduced a `Failure`
// value held behind a mismatched static type, which is what CI run #15's since-removed D114 force-cast
// produced. Delete this file once D115 is closed and the stage is recorded in DECISIONS_LOG.
final class ApiResultFailureInteropTests: XCTestCase {

    private func makeFailure(fields: [String: String]? = nil) -> ApiResultFailure {
        ApiResultFailure(code: ApiErrorCode.InternalError(), message: "m", fields: fields, httpStatus: 500)
    }

    /// Stage 0 -- isolates the FORCE-CAST OPERATION ITSELF, with no dispatch afterward. Per SE-0057,
    /// a cast between two specializations of an imported Obj-C lightweight-generic class should check
    /// only Obj-C class identity (erased generic arguments aren't checked) -- so this should always
    /// succeed for a value that genuinely IS-A `ApiResult`. Both an Opus review and an independent
    /// Codex review of D115 judged this hypothesis (H-cast) as unlikely on those grounds, but neither
    /// had a real compiler to confirm it. If THIS test alone crashes, H-cast is confirmed and neither
    /// `onEnum(of:)` (H1) nor `MentoraError`'s property reads (H2) are implicated by that result.
    func testStage0ForceCastToMismatchedStaticTypeSucceeds() {
        let mismatched = makeFailure() as! ApiResult<NSString>
        XCTAssertNotNil(mismatched)
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

    /// Stage 4 -- SKIE's generated `onEnum(of:)` on a `Failure`, called directly. This is the one
    /// suspect `ApiResultBridge.unwrap` no longer depends on after D115. If ONLY this test crashes
    /// while the rest of the suite is green, SKIE's generic-sealed dispatch is the confirmed root
    /// cause and this test should be deleted (with the finding recorded), not "fixed".
    func testStage4OnEnumOfFailureSelectsFailureCase() {
        switch onEnum(of: makeFailure()) {
        case .success:
            XCTFail("SKIE dispatched an ApiResult.Failure to the .success case")
        case .failure(let failure):
            XCTAssertEqual(failure.httpStatus, 500)
        }
    }

    /// Stage 4b -- reproduces the ACTUAL mismatch condition from CI run #15: a value whose STATIC type
    /// is a concrete, unrelated `ApiResult<X>` but whose DYNAMIC type is `ApiResultFailure` -- exactly
    /// what the since-removed D114 force-cast produced. Stage 4 above does NOT reproduce this (it binds
    /// `onEnum`'s generic parameter directly to `ApiResultFailure`, with no mismatch at all) -- both an
    /// Opus review and an independent Codex review of D115 identified this gap independently. If Stage 0
    /// passes but THIS stage crashes, that isolates the defect specifically to `onEnum(of:)`'s handling
    /// of a mismatched-static-type dispatch, which is H1's precise mechanism (see DECISIONS_LOG D115).
    func testStage4bOnEnumOfMismatchedStaticTypeHoldingFailure() {
        let mismatched = makeFailure() as! ApiResult<NSString>
        switch onEnum(of: mismatched) {
        case .success:
            XCTFail("SKIE dispatched a mismatched-static-type Failure to the .success case")
        case .failure(let failure):
            XCTAssertEqual(failure.httpStatus, 500)
        }
    }

    /// Stage 5 -- control: the same dispatch on a `Success`, already proven green elsewhere.
    func testStage5OnEnumOfSuccessSelectsSuccessCase() {
        let success: ApiResult<NSString> = ApiResultSuccess(data: "x")
        switch onEnum(of: success) {
        case .success(let s): XCTAssertEqual(s.data, "x")
        case .failure:        XCTFail("SKIE dispatched an ApiResultSuccess to the .failure case")
        }
    }
}
