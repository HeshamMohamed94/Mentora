package com.mentora.android.ui.quiz

/**
 * Task 14 fix (round-1 review, HIGH-2). [QuizViewModel] is scoped to Quiz's own `NavBackStackEntry`,
 * which is destroyed by a plain pop (the back button, or system back) — but `ux/NAVIGATION_SPEC.md`
 * lines 42/71/116 ("Quiz answers survive back-navigation... Backing out of Quiz mid-attempt... preserves
 * selected answers rather than discarding progress") and `product/USER_FLOWS.md:135` ("Student leaves
 * mid-quiz -> answers preserved, can resume (not force-reset)") require exactly that "backed out
 * mid-attempt, come back later via a genuinely NEW push of Quiz" case to survive — not just the
 * recomposition/tab-switch-and-back case a ViewModel-scoped field already handles for free (a plain
 * pop does NOT preserve a `NavBackStackEntry`'s `ViewModelStore`, unlike `saveState = true`).
 *
 * A tiny process-scoped, courseId-keyed singleton, mirroring `MentoraApplication`'s own "one
 * process-lifetime instance" convention for state that need not survive process death — no local DB
 * exists anywhere in this app, and every other piece of ephemeral in-memory-only state here already
 * accepts that same limit (in-flight write queues, `AppSessionViewModel`'s own transient fields, etc.).
 *
 * [clear] is called from exactly the two paths that legitimately want a blank slate — a successful
 * submission (`QuizViewModel.onSubmitTapped`, that attempt is over) and "Retry Quiz"
 * (`MentoraNavHost.kt`'s `quizResultsContent.onRetry`, an explicit new attempt) — never from a plain
 * back-navigation, which is the whole point of this store existing.
 */
object QuizAttemptDraftStore {
    private class Draft {
        val answers = mutableMapOf<String, String>()
        var currentQuestionIndex = 0
    }

    private val drafts = mutableMapOf<String, Draft>()

    private fun draftFor(courseId: String): Draft = drafts.getOrPut(courseId) { Draft() }

    /** A live, mutable reference — writes into this map persist automatically, with no separate
     *  write-through step needed (unlike [currentQuestionIndexFor]/[setCurrentQuestionIndex], which
     *  wrap a plain `Int` field and so need an explicit setter). */
    fun answersFor(courseId: String): MutableMap<String, String> = draftFor(courseId).answers

    fun currentQuestionIndexFor(courseId: String): Int = draftFor(courseId).currentQuestionIndex

    fun setCurrentQuestionIndex(courseId: String, index: Int) {
        draftFor(courseId).currentQuestionIndex = index
    }

    fun clear(courseId: String) {
        drafts.remove(courseId)
    }

    /** Test-only hygiene hook — this object is a genuine JVM-process-wide singleton (that's the whole
     *  point, see this object's own kdoc), so without an explicit reset a draft left behind by one
     *  test's `QuizViewModel(courseId = "course-1", ...)` leaks into the next test that reuses the
     *  same default `courseId`. Call from `@Before`/`@After` in any test touching [QuizViewModel]. */
    fun clearAllForTests() {
        drafts.clear()
    }
}
