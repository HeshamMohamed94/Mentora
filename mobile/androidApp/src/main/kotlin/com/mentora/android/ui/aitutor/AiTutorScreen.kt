package com.mentora.android.ui.aitutor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraRadiusTokens
import com.mentora.android.theme.extendedColors
import com.mentora.android.theme.stateOpacities
import com.mentora.android.ui.components.AITutorBubble
import com.mentora.android.ui.components.AITutorQuickAction
import com.mentora.android.ui.components.AiTutorSender
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.MentoraTextButton
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk
import com.mentora.shared.domain.model.AiQuickAction

/**
 * T17 — the real AI Tutor screen, replacing `ui/screens/PlaceholderScreens.kt`'s placeholder. Built
 * from `ux/SCREEN_UX_SPECS.md § 15` (primary source — no exact-showcase mockup fully covers this
 * screen's TAB-ROOT case; `design-to-code/screens/mobile-ai-tutor.json` is an exact-showcase of the
 * CONTEXTUAL, Course-Player-launched variant instead, its own disclosed `conflicts[0]`) for content
 * order/behavior, with that same json's visual details (bubble radii/colors already live in
 * [AITutorBubble]/[AITutorQuickAction] verbatim — reused here, not rebuilt) for the pill composer/
 * circular send button shape.
 *
 * **Bottom nav / chrome**: this destination is [com.mentora.android.navigation.TabGraph.AiTutorGraph]'s
 * own tab root — `MentoraNavHost` already renders the shared [com.mentora.android.ui.shell
 * .MentoraTopBar] (title) above this composable and keeps the bottom nav visible for it (the
 * `mobile-ai-tutor.json` `conflicts[0]`-documented tab-root reading), so this screen owns only the
 * content below that chrome — no `Scaffold` of its own, same convention
 * [com.mentora.android.ui.mylearning.MyLearningScreen] already established for a plain tab-root screen.
 *
 * **Welcome message (disclosed judgment call).** Rendered here, purely from
 * `uiState.items.isEmpty()` — never stored in [AiTutorViewModel.uiState] itself; see that class's own
 * kdoc ("Welcome message is NOT part of `AiTutorUiState.items`") for why. This means the welcome bubble
 * shows immediately on first composition (before [AiTutorViewModel]'s own history load resolves,
 * matching the spec's "not empty white space") and disappears the instant real history (or this
 * session's own first turn) lands in `items` — correctly covering both "still loading" and "genuinely
 * empty conversation" with the same one condition, never a separate loading state of its own.
 *
 * **Live region (disclosed judgment call, revised in review).** No prior screen in this phase announces
 * a REPEATING stream of new content (`PurchaseSuccessScreen`'s own `Modifier.semantics { liveRegion =
 * ... }` on a `Text` node is the only precedent, a ONE-SHOT celebratory announcement) — this screen
 * applies the identical `LiveRegionMode.Polite` modifier to each assistant [AITutorBubble] (see
 * [AiTutorThreadRow]), the most Compose-idiomatic reading of `design-system/ACCESSIBILITY.md § 5`'s "AI
 * Tutor new message" requirement available without inventing a dedicated sr-only announcer element this
 * codebase has no other precedent for. Two corrections from the first review pass: (1) the modifier now
 * carries `mergeDescendants = true` — [AITutorBubble]'s own `Text` is a separate descendant of the
 * container this modifier sits on, so without it there was no text for TalkBack to actually announce;
 * (2) it's applied only once a turn's `isStreaming` flips to `false`, so a growing reply announces ONCE
 * on completion, never a fragment per arbitrary `Chunk` boundary (`AiStreamResult`'s own kdoc: chunk
 * boundaries are "never a semantic unit").
 *
 * **StreamFailed retry-affordance shape (disclosed judgment call).** A [AiStreamResult.StreamFailed]
 * with non-blank partial text renders as a REAL, normal assistant bubble (the text that arrived is
 * genuine content, never hidden) PLUS a small, separate retry row directly beneath it — never merged
 * into the bubble itself (which would misrepresent a partial reply as if it were the complete one) and
 * never a full [com.mentora.android.ui.components.ErrorState] (which would blank out the conversation
 * around it). See [AiTutorStreamRetryRow].
 *
 * **Composer — bespoke, not [com.mentora.android.ui.components.MentoraTextField] (disclosed; citation
 * corrected in review).** [com.mentora.android.ui.components.MentoraTextField] hard-requires a non-null
 * floating `label` and hardcodes its shape to `radius.medium` — neither fits this screen's locked
 * pill-shaped, label-less "Ask anything…" composer. The pill/circular-send TREATMENT itself is the
 * locked Web pattern from `execution/DECISIONS_LOG.md` D52 (`ai-tutor.json`'s own Web spec), not
 * `mobile-ai-tutor.json` — that file's cited lines 2225-2228 actually show a plain 12px
 * (`radius.medium`) corner on both the input and the send button, the same shape `MentoraTextField`
 * already has; the first review pass's claim that the mobile mockup pre-dates and confirms D52's Web
 * correction was independently checked and found FALSE. [AiTutorComposer] instead builds directly on
 * the same underlying M3 `OutlinedTextField` primitive [MentoraTextField] itself wraps, with `shape =
 * RoundedCornerShape(percent = 50)` and no `label` slot — not a modification to `MentoraTextField.kt`
 * itself (out of this task's scope). Its send icon ([MentoraIconName.ArrowUpward]) also deliberately
 * diverges from D52's locked `arrowForward` (and the showcase's own `send` glyph) — direction-neutral,
 * so RTL-safe regardless, but a plain judgment call, not spec fidelity.
 *
 * **Send button — bespoke filled circle, not [com.mentora.android.ui.components.MentoraIconButton]
 * (second review-pass fix, MEDIUM).** [com.mentora.android.ui.components.MentoraIconButton] is
 * `design-system/COMPONENTS.md § IconButton`'s deliberately low-emphasis, always-transparent control
 * (a secondary action, e.g. a topbar icon) — the first fix pass still used it here, rendering this
 * screen's one PRIMARY action as a faint grey glyph instead of the locked filled/circular send
 * affordance (`design-review-locked/Mentora Showcase.dc.html:2227`: `background:var(--brand)`,
 * matching Web's own `Button variant="primary"` treatment). [AiTutorSendButtonControl] inlines
 * [com.mentora.android.ui.components.MentoraButton]'s own Primary-variant color logic (container/
 * content/pressed/disabled) locally rather than modifying [com.mentora.android.ui.components
 * .MentoraIconButton] itself (out of this task's scope, same "bespoke, disclosed" precedent as the
 * composer field above), at [MentoraDimens.touchTargetMin] (48dp, not the showcase's own 52dp literal
 * — kept consistent with every other icon-sized control in this kit rather than a one-off size).
 *
 * **Keyboard behavior** (`ux/MOBILE_UX.md § 14`) — [onSendTapped] never calls any hide-keyboard/
 * `clearFocus` API, but the first review pass found the text field was ALSO disabled for the duration
 * of a send (`enabled = !isSending`), which achieves the same forbidden effect indirectly: Compose
 * clears focus from a field the instant it becomes disabled, closing the IME anyway. Fixed —
 * [AiTutorComposer]'s field now stays enabled unconditionally; only the send button itself gates on
 * `isSending`. A root-level `.imePadding()` (also missing in the first pass, given this app's
 * `targetSdk = 36` edge-to-edge default) keeps the composer pinned above the keyboard rather than
 * behind it, matching `ux/SCREEN_UX_SPECS.md:599`. The only explicit keyboard action here
 * ([ImeAction.Send]) forwards to the exact same [AiTutorViewModel.onSendTapped] the send button itself
 * calls, neither of which touches
 * focus/keyboard visibility.
 */
@Composable
fun AiTutorScreen(sdk: MentoraSdk, modifier: Modifier = Modifier) {
    // T18 fix: resolved HERE, at tap time, from `LocalContext.current` — not captured once inside a
    // `Factory`-held `Application` reference (see `AiTutorViewModel`'s own kdoc, T18 fix note, for the
    // regression that shape had once a real in-app language switch existed to expose it). This
    // Composable re-runs on every recomposition, so `context` always reflects whatever
    // `com.mentora.android.locale.LocalizedContent` currently has active — a remembered ViewModel/
    // Factory could not.
    val context = LocalContext.current
    val viewModel: AiTutorViewModel = viewModel(factory = AiTutorViewModel.Factory(sdk))
    val uiState by viewModel.uiState.collectAsState()

    AiTutorContent(
        uiState = uiState,
        onInputChanged = viewModel::onInputChanged,
        onSendTapped = viewModel::onSendTapped,
        onQuickActionTapped = { action -> viewModel.onQuickActionTapped(context.getString(quickActionPromptStringRes(action))) },
        onRetryTapped = viewModel::onRetryTapped,
        modifier = modifier,
    )
}

/** The stateless presentation half of [AiTutorScreen] — same split rationale as every other T7+
 *  screen in this phase. */
@Composable
internal fun AiTutorContent(
    uiState: AiTutorUiState,
    onInputChanged: (String) -> Unit,
    onSendTapped: () -> Unit,
    onQuickActionTapped: (AiQuickAction) -> Unit,
    onRetryTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val welcomeText = stringResource(R.string.ai_tutor_welcome_message)
    val threadItems = uiState.items.ifEmpty {
        listOf(AiTutorThreadItem.AssistantMessage(id = AiTutorWelcomeItemId, text = welcomeText))
    }
    val listState = rememberLazyListState()
    // Review fix (MEDIUM): without this, a new turn (or a growing streamed reply) renders below the
    // fold with no automatic scroll — the primary interaction of this screen went silently invisible
    // once the thread exceeded one viewport. Re-keyed on the item list's own content (not just size) so
    // this also re-fires on every `Chunk` while an assistant bubble is still growing, keeping the
    // newest content pinned in view for the whole duration of a stream, not just at its start/end.
    LaunchedEffect(threadItems) {
        if (threadItems.isNotEmpty()) listState.animateScrollToItem(threadItems.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().imePadding().testTag(AiTutorScreenTestTag)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(AiTutorThreadListTestTag),
            contentPadding = PaddingValues(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
        ) {
            items(threadItems, key = { it.id }) { item ->
                AiTutorThreadRow(item = item, onRetryTapped = onRetryTapped)
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space2)
                .testTag(AiTutorQuickActionsRowTestTag),
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
        ) {
            items(AiQuickAction.entries) { action ->
                // Accessibility point 3: `AITutorQuickAction.kt` itself cannot be touched beyond the
                // one disclosed 80%->85% `AITutorBubble` fix elsewhere in this task — merging this
                // chip's own child `Text` semantics at the CALL SITE (rather than inside the shared
                // component) is what makes it read as one focusable node with its full label as the
                // accessible name, not two separate stops (an unlabeled clickable node + a
                // non-actionable text node).
                AITutorQuickAction(
                    label = stringResource(quickActionLabelStringRes(action)),
                    onClick = { onQuickActionTapped(action) },
                    enabled = !uiState.isSending, // Review fix (MEDIUM): was a silent dead tap while sending.
                    modifier = Modifier
                        .semantics(mergeDescendants = true) {}
                        .testTag(aiTutorQuickActionTestTag(action)),
                )
            }
        }

        AiTutorComposer(
            value = uiState.inputText,
            onValueChange = onInputChanged,
            onSend = onSendTapped,
            isSending = uiState.isSending,
            modifier = Modifier.fillMaxWidth().padding(MentoraDimens.spacing.space4),
        )
    }
}

/** Review fix (HIGH): the text field itself is now ALWAYS enabled, regardless of [isSending] — Compose
 *  clears focus (and, with it, the IME) from a field the instant it's disabled, which was silently
 *  dismissing the keyboard on every send and contradicting `ux/MOBILE_UX.md § 14`'s own "sending a
 *  message keeps the keyboard open" requirement despite this file's own kdoc claiming compliance. Only
 *  the send button gates on [isSending] now — the ViewModel's own re-entrancy guard already makes a
 *  stray extra tap on it a no-op regardless. */
@Composable
private fun AiTutorComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).testTag(AiTutorComposerFieldTestTag),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.ai_tutor_composer_placeholder), style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(percent = 50), // pill composer — see this file's own top kdoc.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        AiTutorSendButtonControl(
            contentDescription = stringResource(R.string.ai_tutor_send_content_description),
            onClick = onSend,
            enabled = !isSending && value.isNotBlank(),
            modifier = Modifier.testTag(AiTutorSendButtonTestTag),
        )
    }
}

/** See [AiTutorScreen]'s own top kdoc, "Send button" section, for why this is bespoke rather than
 *  [com.mentora.android.ui.components.MentoraIconButton]. Color logic mirrors
 *  [com.mentora.android.ui.components.MentoraButtonVariant.Primary]'s own exact container/content/
 *  pressed/disabled states — inlined here, not shared, since neither `MentoraButton` (text-only) nor
 *  `MentoraIconButton` (always-transparent) exposes a filled circular icon variant. */
@Composable
private fun AiTutorSendButtonControl(
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val opacities = MaterialTheme.stateOpacities
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val containerColor = when {
        !enabled -> colorScheme.primary.copy(alpha = opacities.disabledContainer).compositeOver(colorScheme.surface)
        pressed -> extended.primaryPressed
        else -> colorScheme.primary
    }
    val iconTint = if (enabled) colorScheme.onPrimary else colorScheme.onSurface.copy(alpha = opacities.disabledContent)

    Box(
        modifier = modifier
            .size(MentoraDimens.touchTargetMin)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
            .semantics { if (!enabled) disabled() },
        contentAlignment = Alignment.Center,
    ) {
        MentoraIcon(
            name = MentoraIconName.ArrowUpward,
            contentDescription = contentDescription,
            size = MentoraDimens.iconSize.default,
            tint = iconTint,
        )
    }
}

/** One [AiTutorThreadItem] rendered per its own runtime shape — see [AiTutorViewModel]'s own kdoc for
 *  what each shape means and when it appears. */
@Composable
private fun AiTutorThreadRow(item: AiTutorThreadItem, onRetryTapped: (String) -> Unit) {
    when (item) {
        is AiTutorThreadItem.UserMessage -> AITutorBubble(
            text = item.text,
            sender = AiTutorSender.User,
            modifier = Modifier.fillMaxWidth().testTag(aiTutorMessageTestTag(item.id)),
        )

        is AiTutorThreadItem.AssistantMessage -> Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1)) {
            AITutorBubble(
                text = item.text,
                sender = AiTutorSender.Ai,
                // This file's own top kdoc, "Live region" section. Review fix (MEDIUM), two parts:
                // (1) `mergeDescendants = true` is required for TalkBack to have anything to announce —
                // the bare `liveRegion` modifier sat on `AITutorBubble`'s outer non-merging container,
                // a sibling of (not a parent merging) its own inner `Text` node, so it announced empty
                // content; (2) only applied while `!item.isStreaming`, so a growing reply announces ONCE
                // on completion rather than re-announcing a fragment on every arbitrary `Chunk` boundary
                // (`AiStreamResult`'s own kdoc: chunk boundaries are "never a semantic unit").
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (item.isStreaming) it else it.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    }
                    .testTag(aiTutorMessageTestTag(item.id)),
            )
            if (item.streamFailedRetryContent != null) {
                AiTutorStreamRetryRow(itemId = item.id, onRetryTapped = onRetryTapped)
            }
        }

        is AiTutorThreadItem.Thinking -> AiTutorThinkingRow(modifier = Modifier.fillMaxWidth().testTag(AiTutorThinkingIndicatorTestTag))

        is AiTutorThreadItem.PreStreamError -> AiTutorErrorRow(
            item = item,
            onRetryTapped = onRetryTapped,
            modifier = Modifier.fillMaxWidth().testTag(aiTutorMessageTestTag(item.id)),
        )
    }
}

/** `UX_STATES.md § 10`'s "three-dot pulse" — its own bubble-shaped row (AI-side surface/shape,
 *  duplicated in miniature from [AITutorBubble] rather than extending that component to accept
 *  arbitrary content in place of `text: String`, out of this task's "don't touch beyond the one
 *  disclosed fix" scope). `ACCESSIBILITY.md` line 430: linear easing, never `easing.standard`. */
@Composable
private fun AiTutorThinkingRow(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val contentDescriptionText = stringResource(R.string.ai_tutor_thinking_content_description)
    val shape = RoundedCornerShape(
        topStart = MentoraRadiusTokens.large,
        topEnd = MentoraRadiusTokens.large,
        bottomEnd = MentoraRadiusTokens.large,
        bottomStart = MentoraRadiusTokens.small,
    )
    val transition = rememberInfiniteTransition(label = "ai-tutor-thinking")

    Row(modifier = modifier.semantics { contentDescription = contentDescriptionText }, horizontalArrangement = Arrangement.Start) {
        Surface(shape = shape, color = colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space3),
                horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1),
            ) {
                repeat(ThinkingDotCount) { index ->
                    val alpha by transition.animateFloat(
                        initialValue = ThinkingDotMinAlpha,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = ThinkingDotDurationMillis,
                                delayMillis = index * ThinkingDotStaggerMillis,
                                easing = LinearEasing,
                            ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "ai-tutor-thinking-dot-$index",
                    )
                    Box(
                        modifier = Modifier
                            .size(ThinkingDotSize)
                            .clip(CircleShape)
                            .background(colorScheme.primary.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}

/** `ux/SCREEN_UX_SPECS.md § 15`'s "inline retry within the bubble slot" — [item.code] `null` means a
 *  blank-partial-text [com.mentora.shared.data.repository.aitutor.AiStreamResult.StreamFailed] (see
 *  [AiTutorThreadItem.PreStreamError]'s own kdoc), falling back to a generic localized string since no
 *  [com.mentora.shared.data.network.ApiErrorCode] exists for that case. */
@Composable
private fun AiTutorErrorRow(item: AiTutorThreadItem.PreStreamError, onRetryTapped: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(
        topStart = MentoraRadiusTokens.large,
        topEnd = MentoraRadiusTokens.large,
        bottomEnd = MentoraRadiusTokens.large,
        bottomStart = MentoraRadiusTokens.small,
    )
    val message = item.code?.let { apiErrorMessage(it) } ?: stringResource(R.string.ai_tutor_stream_failed_message)

    Row(modifier = modifier, horizontalArrangement = Arrangement.Start) {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(
                modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space3),
                verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
            ) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                MentoraTextButton(
                    text = stringResource(R.string.ai_tutor_retry_action),
                    onClick = { onRetryTapped(item.id) },
                    modifier = Modifier.testTag(aiTutorRetryButtonTestTag(item.id)),
                )
            }
        }
    }
}

/** The small, separate retry affordance next to an already-real partial-text bubble — see this file's
 *  own top kdoc, "StreamFailed retry-affordance shape." */
@Composable
private fun AiTutorStreamRetryRow(itemId: String, onRetryTapped: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ai_tutor_stream_failed_message),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            // Review fix (MEDIUM): unweighted in an unbounded `Row`, this text would consume the full
            // row width at large font scale and squeeze the Retry button — the only way to recover this
            // turn — to 0dp. `ACCESSIBILITY.md § 6`'s "layout gives way before touch targets do."
            modifier = Modifier.weight(1f),
        )
        MentoraTextButton(
            text = stringResource(R.string.ai_tutor_retry_action),
            onClick = { onRetryTapped(itemId) },
            modifier = Modifier.testTag(aiTutorRetryButtonTestTag(itemId)),
        )
    }
}

/** [AiQuickAction] -> the `R.string` resource id for its visible chip LABEL — the prompt-text half of
 *  this pairing lives in [AiTutorViewModel]'s own `quickActionPromptStringRes` (that file's own kdoc
 *  explains why the label lives here in the Screen while the prompt lives in the ViewModel/Factory). */
private fun quickActionLabelStringRes(action: AiQuickAction): Int = when (action) {
    AiQuickAction.ExplainThisLesson -> R.string.ai_tutor_quick_action_explain_lesson_label
    AiQuickAction.Summarize -> R.string.ai_tutor_quick_action_summarize_label
    AiQuickAction.GiveMeAnExample -> R.string.ai_tutor_quick_action_example_label
    AiQuickAction.QuizMe -> R.string.ai_tutor_quick_action_quiz_me_label
    AiQuickAction.WhatShouldILearnNext -> R.string.ai_tutor_quick_action_learn_next_label
}

private const val ThinkingDotCount = 3
private const val ThinkingDotDurationMillis = 600
private const val ThinkingDotStaggerMillis = 150
private const val ThinkingDotMinAlpha = 0.3f
private val ThinkingDotSize = 8.dp

/** The synthetic welcome bubble's own stable id — never round-tripped to [AiTutorViewModel], purely a
 *  `LazyColumn` item key (see this file's own top kdoc, "Welcome message"). */
private const val AiTutorWelcomeItemId = "welcome"

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val AiTutorScreenTestTag = "ai-tutor-screen"
const val AiTutorThreadListTestTag = "ai-tutor-thread-list"
const val AiTutorQuickActionsRowTestTag = "ai-tutor-quick-actions-row"
const val AiTutorComposerFieldTestTag = "ai-tutor-composer-field"
const val AiTutorSendButtonTestTag = "ai-tutor-send-button"
const val AiTutorThinkingIndicatorTestTag = "ai-tutor-thinking-indicator"

fun aiTutorMessageTestTag(itemId: String): String = "ai-tutor-message-$itemId"
fun aiTutorQuickActionTestTag(action: AiQuickAction): String = "ai-tutor-quick-action-${action.name}"

// Review fix (LOW): id-scoped — a single shared tag would throw "multiple nodes found" the moment two
// failed/partial turns coexist in the same thread (both `AiTutorErrorRow` and `AiTutorStreamRetryRow`
// can render their own retry button at once).
fun aiTutorRetryButtonTestTag(itemId: String): String = "ai-tutor-retry-button-$itemId"
