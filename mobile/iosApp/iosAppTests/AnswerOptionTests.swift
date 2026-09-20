import XCTest
@testable import iosApp

/// Phase 5 Task T11 slice 3 -- `Components/AnswerOption.swift`'s pure state-resolution logic, ported
/// directly from Android's own `answerOptionStateFor` (`AnswerOption.kt`) -- no dedicated Android unit
/// test file exists for it (verified), so these are newly authored here, not a golden-vector port.
final class AnswerOptionTests: XCTestCase {

    // MARK: - AnswerOptionRules.state

    func test_notSubmittedAndNotSelectedIsDefault() {
        XCTAssertEqual(AnswerOptionRules.state(isSelected: false, isSubmitted: false, isCorrectAnswer: false), .`default`)
    }

    func test_notSubmittedAndSelectedIsSelectedUnsubmittedRegardlessOfCorrectness() {
        XCTAssertEqual(AnswerOptionRules.state(isSelected: true, isSubmitted: false, isCorrectAnswer: false), .selectedUnsubmitted)
        XCTAssertEqual(AnswerOptionRules.state(isSelected: true, isSubmitted: false, isCorrectAnswer: true), .selectedUnsubmitted)
    }

    func test_submittedAndCorrectAnswerIsCorrectRegardlessOfWhetherItWasSelected() {
        // The real answer reveals as Correct whether or not the user actually picked it.
        XCTAssertEqual(AnswerOptionRules.state(isSelected: false, isSubmitted: true, isCorrectAnswer: true), .correct)
        XCTAssertEqual(AnswerOptionRules.state(isSelected: true, isSubmitted: true, isCorrectAnswer: true), .correct)
    }

    func test_submittedAndSelectedButNotCorrectIsIncorrect() {
        XCTAssertEqual(AnswerOptionRules.state(isSelected: true, isSubmitted: true, isCorrectAnswer: false), .incorrect)
    }

    func test_submittedAndNeitherSelectedNorCorrectIsDisabledPostSubmit() {
        XCTAssertEqual(AnswerOptionRules.state(isSelected: false, isSubmitted: true, isCorrectAnswer: false), .disabledPostSubmit)
    }

    // MARK: - AnswerOptionRules.isClickable

    func test_onlyDefaultAndSelectedUnsubmittedAreClickable() {
        XCTAssertTrue(AnswerOptionRules.isClickable(.`default`))
        XCTAssertTrue(AnswerOptionRules.isClickable(.selectedUnsubmitted))
        XCTAssertFalse(AnswerOptionRules.isClickable(.correct))
        XCTAssertFalse(AnswerOptionRules.isClickable(.incorrect))
        XCTAssertFalse(AnswerOptionRules.isClickable(.disabledPostSubmit))
    }

    // MARK: - AnswerOptionState.colorSet -- distinct color identity per state

    func test_everyStateProducesADistinctColorSet() {
        let opacity = 0.38
        let sets: [AnswerOptionColorSet] = [
            AnswerOptionState.`default`.colorSet(disabledTextOpacity: opacity),
            AnswerOptionState.selectedUnsubmitted.colorSet(disabledTextOpacity: opacity),
            AnswerOptionState.correct.colorSet(disabledTextOpacity: opacity),
            AnswerOptionState.incorrect.colorSet(disabledTextOpacity: opacity),
            AnswerOptionState.disabledPostSubmit.colorSet(disabledTextOpacity: opacity),
        ]
        for i in 0..<sets.count {
            for j in (i + 1)..<sets.count {
                XCTAssertNotEqual(sets[i], sets[j], "states at index \(i) and \(j) must not share an identical color set")
            }
        }
    }
}
