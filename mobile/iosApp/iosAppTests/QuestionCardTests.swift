import XCTest
@testable import iosApp

/// Phase 5 Task T11 slice 3 -- `Components/QuestionCard.swift`'s pure divide-by-zero guard.
final class QuestionCardTests: XCTestCase {

    func test_progressFractionComputesQuestionNumberOverTotal() {
        XCTAssertEqual(QuestionCardRules.progressFraction(questionNumber: 3, totalQuestions: 10), 0.3, accuracy: 0.0001)
    }

    func test_progressFractionIsZeroWhenTotalQuestionsIsZero() {
        // Android's own identical guard: `if (totalQuestions > 0) ... else 0f`.
        XCTAssertEqual(QuestionCardRules.progressFraction(questionNumber: 1, totalQuestions: 0), 0)
    }

    func test_progressFractionAtFirstQuestion() {
        XCTAssertEqual(QuestionCardRules.progressFraction(questionNumber: 1, totalQuestions: 5), 0.2, accuracy: 0.0001)
    }

    func test_progressFractionAtLastQuestionIsOne() {
        XCTAssertEqual(QuestionCardRules.progressFraction(questionNumber: 5, totalQuestions: 5), 1.0, accuracy: 0.0001)
    }
}
