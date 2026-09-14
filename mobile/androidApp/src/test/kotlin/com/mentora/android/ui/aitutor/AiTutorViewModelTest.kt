package com.mentora.android.ui.aitutor

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.aitutor.AiStreamResult
import com.mentora.shared.domain.model.AiConversation
import com.mentora.shared.domain.model.AiMessage
import com.mentora.shared.domain.model.AiMessageRole
import com.mentora.shared.domain.model.AiQuickAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun message(id: String, role: AiMessageRole, content: String) = AiMessage(
    id = id,
    role = role,
    content = content,
    lessonContextId = null,
    createdAt = Instant.fromEpochMilliseconds(0),
)

/**
 * T17 — [AiTutorViewModel]'s conversation-load/send/retry/quick-action logic, as a plain JVM unit
 * test. Mirrors `QuizViewModelTest`/`LearningPathDetailsViewModelTest`'s exact style (hand-built fakes
 * wired to the ViewModel's own constructor lambdas, no mocking framework, no Android resources).
 *
 * T18 fix: [AiTutorViewModel.onQuickActionTapped] used to take an [AiQuickAction] and resolve its
 * prompt text internally via a constructor-injected `quickActionPrompt` lambda — that param is gone;
 * resolution now happens at [com.mentora.android.ui.aitutor.AiTutorScreen]'s own call site (see that
 * ViewModel's own kdoc, T18 fix note, for why). [onQuickActionTapped] now just takes the already-
 * resolved prompt text directly, same as [AiTutorViewModel.onSendTapped]'s composer path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiTutorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        getConversation: suspend (String?, Int?) -> ApiResult<AiConversation> =
            { _, _ -> ApiResult.Success(AiConversation("conversation-1", emptyList(), null)) },
        sendMessage: (String, String?, String?) -> Flow<AiStreamResult> = { _, _, _ -> flow {} },
    ) = AiTutorViewModel(getConversation = getConversation, sendMessage = sendMessage)

    // ---- Conversation load ----------------------------------------------------------------------

    @Test
    fun load_emptyConversation_leavesItemsEmpty_soTheScreenRendersTheWelcomeBubble() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun load_nonEmptyConversation_rendersHistoryInOrder() = runTest(testDispatcher) {
        val history = listOf(
            message("m1", AiMessageRole.User, "Hi"),
            message("m2", AiMessageRole.Assistant, "Hello! How can I help?"),
        )
        val viewModel = buildViewModel(
            getConversation = { _, _ -> ApiResult.Success(AiConversation("conversation-1", history, null)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(2, items.size)
        assertEquals("Hi", (items[0] as AiTutorThreadItem.UserMessage).text)
        assertEquals("Hello! How can I help?", (items[1] as AiTutorThreadItem.AssistantMessage).text)
    }

    @Test
    fun load_failure_leavesItemsEmpty_bestEffort_neverBlocksTheScreen() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getConversation = { _, _ -> ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    /** Review fix regression test (HIGH) — the history load used to unconditionally overwrite `items`,
     *  which would silently delete an in-flight turn if the student sent a message before this
     *  suspending load resolved (the composer/quick actions are interactive from the very first
     *  frame). Uses a [CompletableDeferred] to hold the load open past the send, proving the fix
     *  actually depends on timing and isn't just incidentally correct. */
    @Test
    fun send_whileHistoryLoadStillInFlight_historyArrivingLateDoesNotEraseTheNewTurn() = runTest(testDispatcher) {
        val historyDeferred = CompletableDeferred<ApiResult<AiConversation>>()
        val viewModel = buildViewModel(
            getConversation = { _, _ -> historyDeferred.await() },
            sendMessage = { _, _, _ -> flow { emit(AiStreamResult.Chunk("Hi!")) } },
        )
        // Deliberately NOT resolving the history load yet.
        viewModel.onInputChanged("Quiz me")
        viewModel.onSendTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        // The history load NOW resolves, late, with an unrelated (empty) conversation.
        historyDeferred.complete(ApiResult.Success(AiConversation("conversation-1", emptyList(), null)))
        testDispatcher.scheduler.advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(2, items.size)
        assertTrue(items[0] is AiTutorThreadItem.UserMessage)
        assertEquals("Quiz me", (items[0] as AiTutorThreadItem.UserMessage).text)
        assertEquals("Hi!", (items[1] as AiTutorThreadItem.AssistantMessage).text)
    }

    // ---- Send-message happy path -----------------------------------------------------------------

    @Test
    fun sendMessage_happyPath_chunksAccumulateIntoOneGrowingAssistantBubble() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            sendMessage = { content, courseId, lessonContextId ->
                assertEquals("Hello", content)
                assertNull(courseId)
                assertNull(lessonContextId)
                flow {
                    emit(AiStreamResult.Chunk("Hi "))
                    emit(AiStreamResult.Chunk("there!"))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChanged("Hello")
        viewModel.onSendTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        val items = viewModel.uiState.value.items
        assertEquals(2, items.size)
        assertEquals("Hello", (items[0] as AiTutorThreadItem.UserMessage).text)
        val assistant = items[1] as AiTutorThreadItem.AssistantMessage
        assertEquals("Hi there!", assistant.text)
        assertFalse(assistant.isStreaming)
        assertNull(assistant.streamFailedRetryContent)
        assertFalse(viewModel.uiState.value.isSending)
        assertEquals("", viewModel.uiState.value.inputText)
    }

    @Test
    fun onSendTapped_withBlankInput_isANoOp() = runTest(testDispatcher) {
        var sendCallCount = 0
        val viewModel = buildViewModel(sendMessage = { _, _, _ -> sendCallCount++; flow {} })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChanged("   ")
        viewModel.onSendTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, sendCallCount)
        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun onSendTapped_whileATurnIsAlreadyInFlight_isANoOp() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(sendMessage = { _, _, _ -> callCount++; flow { emit(AiStreamResult.Chunk("x")) } })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChanged("First")
        viewModel.onSendTapped()
        // Deliberately NOT advancing the dispatcher — the first send is still in flight.
        viewModel.onInputChanged("Second")
        viewModel.onSendTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, callCount)
        // The still-in-flight guard fires before the composer is ever cleared for the rejected tap.
        assertEquals("Second", viewModel.uiState.value.inputText)
    }

    // ---- PreStreamFailure -------------------------------------------------------------------------

    @Test
    fun sendMessage_preStreamFailure_neverCreatesABubble_showsAnInlineRetryableError() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            sendMessage = { _, _, _ ->
                flow { emit(AiStreamResult.PreStreamFailure(ApiResult.Failure(ApiErrorCode.RateLimitedAiTutor, "slow down", null, 429))) }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChanged("Hello")
        viewModel.onSendTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        val items = viewModel.uiState.value.items
        // Exactly the user's own bubble + the inline error — no assistant bubble was ever created.
        assertEquals(2, items.size)
        assertTrue(items[0] is AiTutorThreadItem.UserMessage)
        val error = items[1] as AiTutorThreadItem.PreStreamError
        assertEquals(ApiErrorCode.RateLimitedAiTutor, error.code)
        assertEquals("Hello", error.retryContent)
        assertFalse(viewModel.uiState.value.isSending)
    }

    // ---- StreamFailed ------------------------------------------------------------------------------

    @Test
    fun sendMessage_streamFailedWithPartialText_keepsThePartialTextAsARealBubble_andSurfacesRetry() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            sendMessage = { _, _, _ ->
                flow {
                    emit(AiStreamResult.Chunk("The answer is"))
                    emit(AiStreamResult.StreamFailed("The answer is", "connection dropped"))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChanged("Explain X")
        viewModel.onSendTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        val assistant = viewModel.uiState.value.items[1] as AiTutorThreadItem.AssistantMessage
        assertEquals("The answer is", assistant.text)
        assertEquals("Explain X", assistant.streamFailedRetryContent)
        assertFalse(assistant.isStreaming)
    }

    @Test
    fun sendMessage_streamFailedWithBlankPartialText_becomesAnInlineErrorWithNoApiErrorCode() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            sendMessage = { _, _, _ -> flow { emit(AiStreamResult.StreamFailed("", "connection dropped before anything arrived")) } },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChanged("Hello")
        viewModel.onSendTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.items[1] as AiTutorThreadItem.PreStreamError
        assertNull(error.code)
        assertEquals("Hello", error.retryContent)
    }

    // ---- Retry ---------------------------------------------------------------------------------------

    @Test
    fun retry_afterPreStreamFailure_reusesTheSameItemId_andResendsTheOriginalContent() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            sendMessage = { content, _, _ ->
                callCount++
                if (callCount == 1) {
                    flow { emit(AiStreamResult.PreStreamFailure(ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500))) }
                } else {
                    flow { emit(AiStreamResult.Chunk("Recovered: $content")) }
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onInputChanged("Hello")
        viewModel.onSendTapped()
        testDispatcher.scheduler.advanceUntilIdle()
        val failedItemId = viewModel.uiState.value.items[1].id

        viewModel.onRetryTapped(failedItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, callCount)
        val items = viewModel.uiState.value.items
        // Still just [user, assistant] — the retry replaced the error in place, no new row appended.
        assertEquals(2, items.size)
        val assistant = items[1] as AiTutorThreadItem.AssistantMessage
        assertEquals(failedItemId, assistant.id)
        assertEquals("Recovered: Hello", assistant.text)
    }

    /** Review fix regression test (MEDIUM) — retrying a [AiStreamResult.StreamFailed] partial-text
     *  bubble used to reuse that item's own id, which meant the moment the retry started, the fresh
     *  [AiTutorThreadItem.Thinking] row for it overwrote the real, already-arrived partial text. If the
     *  retry then failed too, that content was gone permanently. This proves the fix: the original
     *  bubble's text survives untouched (only its own retry affordance clears) and the retry attempt
     *  lands as its own, separate turn. */
    @Test
    fun retry_afterStreamFailedWithPartialText_preservesTheOriginalPartialBubble_asANewSeparateTurn() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            sendMessage = { content, _, _ ->
                callCount++
                if (callCount == 1) {
                    flow {
                        emit(AiStreamResult.Chunk("The answer is"))
                        emit(AiStreamResult.StreamFailed("The answer is", "connection dropped"))
                    }
                } else {
                    flow { emit(AiStreamResult.Chunk("Recovered: $content")) }
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onInputChanged("Explain X")
        viewModel.onSendTapped()
        testDispatcher.scheduler.advanceUntilIdle()
        val partialItemId = viewModel.uiState.value.items[1].id

        viewModel.onRetryTapped(partialItemId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, callCount)
        val items = viewModel.uiState.value.items
        // [original user msg, the ORIGINAL partial bubble (untouched, retry affordance cleared),
        // a whole NEW turn (its own user msg + assistant reply) for the retry attempt].
        assertEquals(4, items.size)
        val original = items[1] as AiTutorThreadItem.AssistantMessage
        assertEquals(partialItemId, original.id)
        assertEquals("The answer is", original.text)
        assertNull(original.streamFailedRetryContent)
        assertTrue(items[2] is AiTutorThreadItem.UserMessage)
        val retryAttempt = items[3] as AiTutorThreadItem.AssistantMessage
        assertNotEquals(partialItemId, retryAttempt.id)
        assertEquals("Recovered: Explain X", retryAttempt.text)
    }

    @Test
    fun retry_onAnUnknownItemId_isANoOp() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(sendMessage = { _, _, _ -> callCount++; flow {} })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onRetryTapped("does-not-exist")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, callCount)
    }

    // ---- Quick actions ---------------------------------------------------------------------------

    @Test
    fun onQuickActionTapped_sendsTheGivenPromptTextVerbatim_asThatTurnsMessage() = runTest(testDispatcher) {
        // T18: prompt-text resolution moved to `AiTutorScreen`'s own call site — this ViewModel just
        // forwards whatever it's given, same as `onSendTapped`'s composer path. Resolution-per-action
        // is `quickActionPromptStringRes`'s own concern now (a pure function, not this class's).
        val sentContents = mutableListOf<String>()
        val viewModel = buildViewModel(
            sendMessage = { content, courseId, lessonContextId ->
                sentContents += content
                assertNull(courseId)
                assertNull(lessonContextId)
                flow { emit(AiStreamResult.Chunk("ok")) }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onQuickActionTapped("Explain this lesson to me.")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onQuickActionTapped("Quiz me on what I've learned so far.")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("Explain this lesson to me.", "Quiz me on what I've learned so far."), sentContents)
    }

    @Test
    fun onQuickActionTapped_alwaysPassesNullCourseAndLessonContext() = runTest(testDispatcher) {
        var capturedCourseId: String? = "not-yet-captured"
        var capturedLessonContextId: String? = "not-yet-captured"
        val viewModel = buildViewModel(
            sendMessage = { _, courseId, lessonContextId ->
                capturedCourseId = courseId
                capturedLessonContextId = lessonContextId
                flow { emit(AiStreamResult.Chunk("ok")) }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Summarize/ExplainThisLesson/GiveMeAnExample all carry `requiresLessonContext = true`, but
        // this screen never has lesson context to thread through (`AiTutorViewModel`'s own kdoc) —
        // both must still be `null`, never a client-side gate/error.
        viewModel.onQuickActionTapped("Summarize this lesson for me.")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(capturedCourseId)
        assertNull(capturedLessonContextId)
    }

    // ---- Input clamp (review fix, LOW) -------------------------------------------------------------

    @Test
    fun onInputChanged_clampsToTheSharedFacadesOwnMaxContentLength() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onInputChanged("a".repeat(5_000))

        assertEquals(4_000, viewModel.uiState.value.inputText.length)
    }

    // ---- quickActionPromptStringRes (T18 review fix, LOW) ------------------------------------------

    /** T18 review fix (LOW): the T18 regression (prompt-text resolution moved out of this ViewModel
     *  entirely — see this class's own kdoc, T18 fix note) left `quickActionPromptStringRes` itself
     *  with no test locking its mapping. A plain resource-id equality check — no Android resources
     *  needed, `R.string.*` are compile-time `Int` constants. */
    @Test
    fun quickActionPromptStringRes_mapsEveryActionToADistinctNonZeroResourceId() {
        val resourceIds = AiQuickAction.entries.map { quickActionPromptStringRes(it) }

        assertEquals(AiQuickAction.entries.size, resourceIds.toSet().size)
        assertTrue(resourceIds.all { it != 0 })
    }
}
