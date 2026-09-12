package com.mentora.shared.domain.model

/**
 * The fixed set of AI Tutor quick actions a platform UI can offer as one-tap shortcuts — exactly
 * these 5, per `execution/PHASE_3_KMP_PLAN.md` Task 14 AC #3, Decision D-D.
 *
 * **Deliberately carries ZERO localized/user-facing prompt text, label, or description of any
 * kind** — `shared` owns only the identity of each action and [requiresLessonContext]; the
 * platform UI (Phase 4/5, out of scope for `shared`) supplies the actual localized prompt string
 * that gets sent as the ordinary [AiMessage] `content` once a student taps one of these. This
 * satisfies both `ADR-002`'s "no localized strings in shared" and `AI_TUTOR_ARCHITECTURE.md §
 * 7`'s "content is localized." Do not add a `promptText`/`label`/`description` field to this
 * type — see Decision D-D.
 *
 * [requiresLessonContext] governs whether the platform UI must thread `courseId`+
 * `lessonContextId` (the [AiQuickAction.QuizMe]/[AiQuickAction.WhatShouldILearnNext] pair does
 * not require it) when sending the resulting message via
 * [com.mentora.shared.domain.usecase.aitutor.SendAiTutorMessageUseCase] — it does not forbid
 * passing lesson context anyway if the UI has it and it makes sense to; both `courseId`/
 * `lessonContextId` remain optional at the wire level regardless of which quick action prompted
 * the send. This split is a judgment call (the backend does not encode any per-quick-action
 * requirement of its own — quick actions are purely a `shared`/UI-layer concept, never a backend
 * one): [ExplainThisLesson]/[Summarize]/[GiveMeAnExample] are inherently about "this lesson," so
 * a UI offering them outside an active lesson screen would not make sense; [QuizMe]/
 * [WhatShouldILearnNext] are course/account-level asks that make sense without a specific lesson
 * open.
 *
 * Note (Task 14 architecture boundary): even though [QuizMe] reads like it should touch the quiz
 * domain, tapping it only ever results in an ordinary text message sent to
 * `POST /api/v1/ai-tutor/conversation/messages` — see
 * [com.mentora.shared.domain.usecase.aitutor.SendAiTutorMessageUseCase]'s kdoc for why that class
 * has zero dependency on `com.mentora.shared.data.repository.quiz.QuizRepository`.
 */
enum class AiQuickAction(val requiresLessonContext: Boolean) {
    ExplainThisLesson(requiresLessonContext = true),
    Summarize(requiresLessonContext = true),
    GiveMeAnExample(requiresLessonContext = true),
    QuizMe(requiresLessonContext = false),
    WhatShouldILearnNext(requiresLessonContext = false),
}
