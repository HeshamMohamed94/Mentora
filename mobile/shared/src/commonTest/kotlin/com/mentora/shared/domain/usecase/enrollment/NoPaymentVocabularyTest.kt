package com.mentora.shared.domain.usecase.enrollment

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `execution/PHASE_3_KMP_PLAN.md` Task 8 AC #5: zero payment vocabulary anywhere in this task's
 * diff — no card/gateway/PSP/financial-credential field, type, or string constant, matching the
 * MVP's "no real payment" constraint (`product/DEMO_PAYMENT_FLOW.md`).
 *
 * `commonTest` has no portable (multiplatform) source-reflection facility to grep this task's own
 * `.kt` files at test-run time, so this is instead an explicit, maintained checklist of every
 * identifier this task actually introduced (every field/type/constant name across
 * `domain/model/{Enrollment,CheckoutPreview,EnrollmentCompletion,MyLearningItem}.kt`,
 * `data/network/dto/EnrollmentDto.kt`, everything under `data/repository/enrollment`, and
 * everything under `domain/usecase/enrollment`) checked against a banned-word list — anyone adding a new field to
 * those files is expected to add it below. This complements (not replaces) a real manual
 * `grep -RniE "card|payment|gateway|psp|stripe|paypal|billing|cvv|cardNumber|amountCharged"` over
 * this task's diff before merge — see the task report for that grep's (empty) output.
 */
class NoPaymentVocabularyTest {

    private val bannedWords = listOf(
        "card", "payment", "gateway", "psp", "stripe", "paypal", "billing", "invoice",
        "charge", "cvv", "creditcard", "debitcard", "checkoutmethod", "amountcharged",
    )

    /** Every identifier (type name, field name, function name, enum value) this task's new files
     * introduce, lowercased, for the banned-word scan below. */
    private val identifiersIntroducedByThisTask = listOf(
        // domain/model
        "EnrollmentSource", "DemoCheckout", "EnrollmentStatus", "Active", "Enrollment", "id",
        "courseId", "source", "enrolledAt", "status", "CheckoutCourse", "title",
        "thumbnailMediaId", "CheckoutPreview", "course", "instructorName", "priceDisplay",
        "EnrollmentCompletion", "enrollment", "alreadyEnrolled", "MyLearningItem",
        // data/network/dto/EnrollmentDto.kt
        "CheckoutCourseDto", "CheckoutPreviewDto", "EnrollmentDto", "EnrollmentCompletionDto",
        "EmptyCheckoutCompleteRequestDto",
        // data/repository/enrollment
        "EnrollmentRepository", "getCheckoutPreview", "completeCheckout", "listEnrollments",
        "EnrollmentRepositoryImpl", "cursor", "limit",
        // domain/usecase/enrollment
        "GetCheckoutPreviewUseCase", "CompleteDemoCheckoutUseCase", "ListEnrollmentsUseCase",
        "GetMyLearningUseCase",
    )

    @Test
    fun `no identifier introduced by Task 8 contains payment vocabulary`() {
        identifiersIntroducedByThisTask.forEach { identifier ->
            val lower = identifier.lowercase()
            bannedWords.forEach { banned ->
                assertTrue(
                    banned !in lower,
                    "Identifier '$identifier' contains banned payment-vocabulary word '$banned'",
                )
            }
        }
    }
}
