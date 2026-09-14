package com.mentora.android.ui.aitutor

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.android.R
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.aitutor.AiStreamResult
import com.mentora.shared.domain.model.AiConversation
import com.mentora.shared.domain.model.AiMessageRole
import com.mentora.shared.domain.model.AiQuickAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * T17 — one row in the AI Tutor chat thread. [id] is stable across recompositions/updates for the
 * SAME conversational turn — [Thinking]/[PreStreamError]/a streaming [AssistantMessage] for one send
 * all reuse the same [id] as they replace each other in place (see [AiTutorViewModel.sendTurn]'s own
 * kdoc), so the `LazyColumn` item this turn occupies never duplicates or reorders as its own state
 * resolves.
 */
sealed interface AiTutorThreadItem {
    val id: String

    data class UserMessage(override val id: String, val text: String) : AiTutorThreadItem

    /**
     * An assistant reply — [isStreaming] while [AiStreamResult.Chunk]s are still arriving for it (the
     * bubble grows in place, never truncates — `ux/SCREEN_UX_SPECS.md § 15`'s own "wraps and grows
     * vertically without limit"). [streamFailedRetryContent] is non-null only for a turn that ended in
     * [AiStreamResult.StreamFailed] WITH non-blank partial text — the partial text is kept as a real,
     * shown bubble (never discarded) per that result's own kdoc, with this field carrying the exact
     * content to resend if the small retry affordance rendered alongside it (never inside it — the
     * bubble's own text is genuine content, not an error) is tapped.
     */
    data class AssistantMessage(
        override val id: String,
        val text: String,
        val isStreaming: Boolean = false,
        val streamFailedRetryContent: String? = null,
    ) : AiTutorThreadItem

    /** The indeterminate "thinking" indicator — its own bubble-shaped row, per `UX_STATES.md § 10`. */
    data class Thinking(override val id: String) : AiTutorThreadItem

    /**
     * [AiStreamResult.PreStreamFailure] (or a [AiStreamResult.StreamFailed] whose own partial text
     * was blank — no real content ever arrived either way) — no assistant bubble exists for this turn
     * at all, so this REPLACES [Thinking] in place rather than sitting next to a bubble; renders as an
     * inline retry within that same slot (`ux/SCREEN_UX_SPECS.md § 15`'s own "inline retry within the
     * bubble slot"). [code] is `null` only for the blank-partial-text [AiStreamResult.StreamFailed]
     * case (no [ApiErrorCode] exists for a mid-stream drop) — the Screen falls back to a generic
     * localized "stream failed" string for that one case instead of [com.mentora.android.ui.error
     * .apiErrorMessage].
     */
    data class PreStreamError(override val id: String, val code: ApiErrorCode?, val retryContent: String) : AiTutorThreadItem
}

/**
 * T17 — [items] holds only REAL turns (loaded history + whatever this session has sent/received);
 * deliberately never includes a synthetic "welcome" entry — see [AiTutorViewModel]'s own kdoc,
 * "Welcome message" section, for why that's rendered by the Screen instead, purely from `items.isEmpty()`.
 */
data class AiTutorUiState(
    val items: List<AiTutorThreadItem> = emptyList(),
    val inputText: String = "",
    /** True for the whole duration of one in-flight turn (from tap/send to the flow's terminal event)
     *  — both the re-entrancy guard [AiTutorViewModel] itself checks (mirrors
     *  `LearningPathDetailsViewModel.onFollowToggleClicked`'s identical "double-tap while in flight is
     *  a no-op" shape) and the signal the Screen disables the composer/quick actions on. */
    val isSending: Boolean = false,
)

/**
 * T17 — AI Tutor's ViewModel. Same lambda-constructor seam as every prior task's ViewModel
 * (`sdk.aiTutor`'s constructor is `internal` to `:shared`).
 *
 * **Welcome message is NOT part of [AiTutorUiState.items].** `ux/SCREEN_UX_SPECS.md § 15` / `UX_STATES
 * .md § 10` want a lightweight welcome bubble on a genuinely empty conversation, worded in the
 * student's own locale — this class cannot resolve a localized string itself (a plain JVM unit test
 * has no Android resources to resolve against, and every other localized-copy call site in this phase
 * resolves `stringResource` from the Screen, never the ViewModel). [items] starts empty (the default)
 * and [loadConversation] either fills it with real history or leaves it empty (a genuinely empty
 * conversation, or a best-effort no-op on a load failure — same "secondary content, never blocks the
 * screen" philosophy `MyLearningViewModel`'s own followed-paths/certificates loads already establish,
 * chosen here because a history-load hiccup should not turn an otherwise-usable AI Tutor screen into a
 * dead [com.mentora.android.ui.components.ErrorState]). [AiTutorContent] (the Screen's stateless half)
 * renders a synthetic welcome [com.mentora.android.ui.components.AITutorBubble] whenever [items] is
 * empty — correct for BOTH the "still loading, nothing arrived yet" window (shows immediately, per
 * spec's own "not empty white space") and the "loaded, genuinely no history" outcome; it disappears the
 * moment [items] gets real content, never persisting alongside real history.
 *
 * **Turn identity — one [AiTutorThreadItem.id] per conversational turn, reused across its own state
 * transitions.** [sendTurn] mints one id (or, on a retry, reuses the failed turn's own id) and drives
 * that SAME id through [AiTutorThreadItem.Thinking] -> ([AiTutorThreadItem.AssistantMessage] growing
 * with each [AiStreamResult.Chunk]) -> its terminal shape ([AiTutorThreadItem.AssistantMessage] with
 * `isStreaming = false`, or [AiTutorThreadItem.PreStreamError] on a pre-stream/blank-partial-text
 * failure, or a real [AiTutorThreadItem.AssistantMessage] carrying `streamFailedRetryContent` on a
 * StreamFailed-with-partial-text) — never a fresh id per transition, so the `LazyColumn` item this turn
 * occupies updates in place rather than the failed placeholder lingering alongside a new one.
 *
 * **No lesson context, ever, from this screen** — `courseId`/`lessonContextId` are always passed
 * `null` to [sendMessage] (`execution/DECISIONS_LOG.md`'s own inherited limitation, see this task's own
 * log entry: this screen is only ever reached as a tab root or via Course Player's existing full-tab-
 * switch "Ask AI Tutor" link, never a contextual/docked push carrying a specific lesson — building
 * that variant is explicitly out of this task's scope, same as the Web precedent). All 5
 * [AiQuickAction]s are offered regardless — `ux/SCREEN_UX_SPECS.md § 15`'s own "opened globally...
 * fall back to asking the student which course/lesson they mean, a clarifying AI response, not a
 * UI-level error" instruction means this class adds no client-side gating beyond passing `null`/`null`
 * unconditionally; a genuinely ambiguous quick-action prompt is the backend's concern, not this
 * screen's.
 */
class AiTutorViewModel(
    private val getConversation: suspend (String?, Int?) -> ApiResult<AiConversation>,
    private val sendMessage: (String, String?, String?) -> Flow<AiStreamResult>,
    /** [AiQuickAction] -> the actual, localized prompt TEXT sent as that turn's message content —
     *  [AiQuickAction] itself deliberately carries none (that enum's own kdoc). A lambda-constructor
     *  seam, same convention as [com.mentora.android.ui.mylearning.MyLearningViewModel
     *  .resolveThumbnailUrl]: production ([Factory]) wires it to `Application.getString` (a real
     *  Android string resource resolves correctly there); a JVM test wires a plain, literal fake —
     *  letting [onQuickActionTapped] itself stay fully unit-testable without Android resources.
     *  Disclosed latent trap (review LOW): this resolves from the device's OS locale, same as the
     *  Screen's own `stringResource` calls for chip LABELS — both agree today only because this app
     *  never calls `setApplicationLocales`/`createConfigurationContext` (`LocaleController` only seeds
     *  `sdk.user.setLocale`, never the Android resource configuration itself). If per-app locale
     *  override is ever wired up, this specific pairing would need revisiting together. */
    private val quickActionPrompt: (AiQuickAction) -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiTutorUiState())
    val uiState: StateFlow<AiTutorUiState> = _uiState.asStateFlow()

    init {
        loadConversation()
    }

    /** Review fix (MEDIUM), disclosed: the backend pages FORWARD from the oldest message
     *  (`AiTutorRepository.kt`'s ascending `createdAt` sort) with a 20-message default page — a plain
     *  `getConversation(null, null)` silently returned only the oldest 20 turns, which could disagree
     *  with the true recent history the backend's own AI completion uses as context. Passing the
     *  server's own `MAX_LIMIT` (100, `backend/.../common/Pagination.kt`) instead of draining
     *  `nextCursor` with a real "load older" affordance — that's real added scope (a backward-paging UI
     *  this screen has no other need for) this task doesn't build; 100 messages comfortably covers this
     *  seed-scale MVP's realistic conversation lengths. */
    private fun loadConversation() {
        viewModelScope.launch {
            when (val result = getConversation(null, HistoryPageLimit)) {
                is ApiResult.Success -> {
                    val history = result.data.messages.map { message ->
                        when (message.role) {
                            AiMessageRole.User -> AiTutorThreadItem.UserMessage(message.id, message.content)
                            AiMessageRole.Assistant -> AiTutorThreadItem.AssistantMessage(message.id, message.content)
                        }
                    }
                    // Review fix (HIGH): unconditionally overwriting `items` here would destroy an
                    // in-flight or already-sent turn if the student sends before this suspending load
                    // resolves (the composer/quick actions are interactive from the very first frame).
                    // This load is a ONE-TIME best-effort history seed (see this class's own kdoc) —
                    // only apply it while `items` is still genuinely untouched by this session; if the
                    // student has already started a turn, this session's own state wins outright rather
                    // than attempting a merge (a merge would need id-collision handling this history
                    // endpoint gives no cheap way to do correctly for a locally-minted turn that hasn't
                    // round-tripped to the server yet).
                    _uiState.update { state -> if (state.items.isEmpty()) state.copy(items = history) else state }
                }
                // Best-effort — see this class's own kdoc. `items` simply stays empty, which the
                // Screen already renders identically to "genuinely no history yet."
                is ApiResult.Failure -> Unit
            }
        }
    }

    /** Review fix (LOW): clamped to [MaxContentLength] — [com.mentora.shared.domain.usecase.aitutor
     *  .SendAiTutorMessageUseCase]'s own identical limit already rejects anything longer as a generic
     *  [AiStreamResult.PreStreamFailure] with no explanation of WHY; clamping client-side here (a paste
     *  is the only realistic way to exceed it — typing 4000 characters one keystroke at a time is not a
     *  real user path) means that rejection can no longer actually happen from this screen. */
    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text.take(MaxContentLength)) }
    }

    /** Composer send — clears the input immediately (per `ux/MOBILE_UX.md § 14`'s own "sending a
     *  message keeps the keyboard open," the Screen must never call a hide-keyboard API after this;
     *  clearing the TEXT is a separate, required behavior and does not itself dismiss the keyboard). A
     *  blank/whitespace-only composer is a silent no-op — [com.mentora.shared.domain.usecase.aitutor
     *  .SendAiTutorMessageUseCase] would reject it as a [AiStreamResult.PreStreamFailure] anyway, but
     *  failing a message the student never meaningfully typed would be a confusing, unearned error. */
    fun onSendTapped() {
        // Checked here too (not only inside [sendTurn]) so a re-entrant tap while a turn is already in
        // flight never even clears the composer text it's about to no-op on — the Screen's own send
        // button is already disabled for this window ([AiTutorUiState.isSending]), this is a defensive
        // second guard, same "belt and suspenders" shape `LearningPathDetailsViewModel
        // .onFollowToggleClicked`'s own in-flight check establishes for its own button.
        if (_uiState.value.isSending) return
        val content = _uiState.value.inputText.trim()
        if (content.isBlank()) return
        _uiState.update { it.copy(inputText = "") }
        sendTurn(content)
    }

    fun onQuickActionTapped(action: AiQuickAction) {
        sendTurn(quickActionPrompt(action))
    }

    /** Retry for either an [AiTutorThreadItem.PreStreamError] row or a partial-text
     *  [AiTutorThreadItem.AssistantMessage] (its own `streamFailedRetryContent`) — a no-op for any
     *  other item id (including one that has already resolved/moved on since the retry affordance was
     *  rendered).
     *
     *  Review fix (MEDIUM): a [AiTutorThreadItem.PreStreamError] has no real content behind it (nothing
     *  was ever lost), so its retry safely REUSES that same item id — [sendTurn] replaces it in place.
     *  A partial-text [AiTutorThreadItem.AssistantMessage], however, already carries genuine streamed
     *  content per [AiStreamResult.StreamFailed]'s own kdoc ("never discarded") — reusing its id would
     *  overwrite that real text with a fresh [AiTutorThreadItem.Thinking] the moment the retry starts,
     *  destroying it permanently if the retry itself then failed too. Retrying THIS shape instead keeps
     *  the partial bubble exactly as-is (only clearing its own retry affordance, since a new attempt is
     *  now in flight for it) and calls [sendTurn] with NO `reuseItemId` — a genuinely new turn, complete
     *  with its own fresh [AiTutorThreadItem.UserMessage] bubble re-showing the same content being
     *  resent (a deliberate, visible "this is a new attempt," not merged into the original question),
     *  so the original partial reply is never at risk of being lost regardless of how the retry
     *  resolves. */
    fun onRetryTapped(itemId: String) {
        val item = _uiState.value.items.firstOrNull { it.id == itemId } ?: return
        when (item) {
            is AiTutorThreadItem.PreStreamError -> sendTurn(item.retryContent, reuseItemId = itemId)
            is AiTutorThreadItem.AssistantMessage -> {
                val content = item.streamFailedRetryContent ?: return
                _uiState.update { state ->
                    state.copy(items = state.items.map { existing ->
                        if (existing.id == itemId && existing is AiTutorThreadItem.AssistantMessage) {
                            existing.copy(streamFailedRetryContent = null)
                        } else {
                            existing
                        }
                    })
                }
                sendTurn(content)
            }
            else -> Unit
        }
    }

    /**
     * The one place [sendMessage] is collected from. `reuseItemId` (retry only) skips appending a NEW
     * [AiTutorThreadItem.UserMessage] — the original one from the first attempt is already in [items]
     * — and reuses that failed turn's own id for the fresh [AiTutorThreadItem.Thinking] row, so the
     * retry replaces the error in place rather than stacking a second bubble for the same turn.
     */
    private fun sendTurn(content: String, reuseItemId: String? = null) {
        if (_uiState.value.isSending) return
        val assistantItemId = reuseItemId ?: newId()

        _uiState.update { state ->
            val items = state.items.toMutableList()
            if (reuseItemId != null) {
                val index = items.indexOfFirst { it.id == reuseItemId }
                if (index != -1) items[index] = AiTutorThreadItem.Thinking(assistantItemId) else items += AiTutorThreadItem.Thinking(assistantItemId)
            } else {
                items += AiTutorThreadItem.UserMessage(newId(), content)
                items += AiTutorThreadItem.Thinking(assistantItemId)
            }
            state.copy(items = items, isSending = true)
        }

        viewModelScope.launch {
            try {
                val appendedText = StringBuilder()
                sendMessage(content, null, null).collect { result ->
                    when (result) {
                        is AiStreamResult.Chunk -> {
                            appendedText.append(result.text)
                            replaceItem(assistantItemId) {
                                AiTutorThreadItem.AssistantMessage(assistantItemId, appendedText.toString(), isStreaming = true)
                            }
                        }
                        is AiStreamResult.PreStreamFailure -> replaceItem(assistantItemId) {
                            AiTutorThreadItem.PreStreamError(assistantItemId, result.failure.code, retryContent = content)
                        }
                        is AiStreamResult.StreamFailed -> replaceItem(assistantItemId) {
                            if (result.partialText.isNotBlank()) {
                                AiTutorThreadItem.AssistantMessage(
                                    assistantItemId,
                                    result.partialText,
                                    isStreaming = false,
                                    streamFailedRetryContent = content,
                                )
                            } else {
                                AiTutorThreadItem.PreStreamError(assistantItemId, code = null, retryContent = content)
                            }
                        }
                    }
                }
                // Flow completed with no terminal failure event — mark whatever this turn resolved to
                // as no longer streaming. A no-op for PreStreamError/StreamFailed shapes (handled
                // above already); a defensive fallback (still `Thinking`, meaning not even one Chunk
                // ever arrived) becomes an empty, non-streaming bubble rather than a permanently stuck
                // indicator — not expected against the real stub provider, but cheap to guard.
                replaceItem(assistantItemId) { current ->
                    when (current) {
                        is AiTutorThreadItem.AssistantMessage -> current.copy(isStreaming = false)
                        is AiTutorThreadItem.Thinking, null ->
                            AiTutorThreadItem.AssistantMessage(assistantItemId, text = "", isStreaming = false)
                        else -> current
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Defensive — mirrors `CoursePlayerViewModel.load`'s own uncaught-exception guard: none
                // of `shared`'s streaming plumbing is expected to throw past its own try/catch, but an
                // uncaught exception here would otherwise propagate out of this `viewModelScope.launch`
                // root coroutine with no handler.
                replaceItem(assistantItemId) {
                    AiTutorThreadItem.PreStreamError(assistantItemId, code = ApiErrorCode.Unknown("AI_TUTOR_SEND_FAILED"), retryContent = content)
                }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    private fun replaceItem(id: String, transform: (AiTutorThreadItem?) -> AiTutorThreadItem) {
        _uiState.update { state ->
            val index = state.items.indexOfFirst { it.id == id }
            val items = state.items.toMutableList()
            if (index == -1) items += transform(null) else items[index] = transform(items[index])
            state.copy(items = items)
        }
    }

    private fun newId(): String = "local-${UUID.randomUUID()}"

    private companion object {
        /** The server's own `MAX_LIMIT` — see [loadConversation]'s own kdoc. */
        const val HistoryPageLimit = 100

        /** Mirrors [com.mentora.shared.domain.usecase.aitutor.SendAiTutorMessageUseCase]'s own
         *  `MAX_CONTENT_LENGTH` — see [onInputChanged]'s own kdoc. */
        const val MaxContentLength = 4_000
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `CoursePlayerViewModel.Factory`'s exact idiom for
     *  needing an [Application] (here: to resolve [quickActionPrompt]'s localized string resources via
     *  `Application.getString`, the one thing this ViewModel needs beyond `sdk.aiTutor` itself). */
    class Factory(private val sdk: MentoraSdk, private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AiTutorViewModel(
            getConversation = sdk.aiTutor.getConversation::invoke,
            sendMessage = sdk.aiTutor.sendMessage::invoke,
            quickActionPrompt = { action -> application.getString(quickActionPromptStringRes(action)) },
        ) as T
    }
}

/** [AiQuickAction] -> the `R.string` resource id for its localized prompt TEXT (message content) —
 *  kept as a standalone top-level function (rather than inline in [AiTutorViewModel.Factory]) so
 *  [AiTutorScreen]'s own quick-action LABEL mapping can sit right next to it for easy side-by-side
 *  comparison; see that file for the label half of this pairing. */
internal fun quickActionPromptStringRes(action: AiQuickAction): Int = when (action) {
    AiQuickAction.ExplainThisLesson -> R.string.ai_tutor_quick_action_explain_lesson_prompt
    AiQuickAction.Summarize -> R.string.ai_tutor_quick_action_summarize_prompt
    AiQuickAction.GiveMeAnExample -> R.string.ai_tutor_quick_action_example_prompt
    AiQuickAction.QuizMe -> R.string.ai_tutor_quick_action_quiz_me_prompt
    AiQuickAction.WhatShouldILearnNext -> R.string.ai_tutor_quick_action_learn_next_prompt
}
