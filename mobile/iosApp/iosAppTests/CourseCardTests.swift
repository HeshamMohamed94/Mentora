import XCTest
@testable import iosApp

/// Phase 5 Task T11 slice 2 -- `Components/CourseCard.swift`'s pure logic. Android has no dedicated unit
/// test file for `CourseCard.kt`/`CourseProgressCard.kt`/etc (verified -- none exist under
/// `mobile/androidApp/src/test`), so there is no golden-vector port here; these tests cover the one
/// piece of real conditional logic these otherwise-declarative composites contain.
final class CourseCardTests: XCTestCase {

    // MARK: - CourseCardRules.effectiveProgress

    func test_effectiveProgressReturnsProgressWhenEnrolled() {
        XCTAssertEqual(CourseCardRules.effectiveProgress(isEnrolled: true, progress: 0.42), 0.42)
    }

    func test_effectiveProgressReturnsNilWhenNotEnrolledEvenIfProgressIsSupplied() {
        // COMPONENTS.md line 295: "Progress bar (only if enrolled)" -- `isEnrolled` is the single
        // source of truth, not `progress`'s own nullability.
        XCTAssertNil(CourseCardRules.effectiveProgress(isEnrolled: false, progress: 0.75))
    }

    func test_effectiveProgressReturnsNilWhenEnrolledButNoProgressSupplied() {
        XCTAssertNil(CourseCardRules.effectiveProgress(isEnrolled: true, progress: nil))
    }

    // MARK: - CourseMetaRowContent.hasContent

    func test_hasContentFalseWhenAllThreeFieldsAreNil() {
        XCTAssertFalse(CourseMetaRowContent.hasContent(rating: nil, studentCount: nil, durationLabel: nil))
    }

    func test_hasContentTrueWhenOnlyRatingIsSupplied() {
        XCTAssertTrue(CourseMetaRowContent.hasContent(rating: 4.5, studentCount: nil, durationLabel: nil))
    }

    func test_hasContentTrueWhenOnlyStudentCountIsSupplied() {
        XCTAssertTrue(CourseMetaRowContent.hasContent(rating: nil, studentCount: 120, durationLabel: nil))
    }

    func test_hasContentTrueWhenOnlyDurationLabelIsSupplied() {
        XCTAssertTrue(CourseMetaRowContent.hasContent(rating: nil, studentCount: nil, durationLabel: "3h 20m"))
    }
}
