package com.mentora.android.ui.aitutor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.mentora.android.R
import com.mentora.android.theme.MentoraTheme
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.domain.model.AiQuickAction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * T19 — real rendering/interaction coverage for [AiTutorContent] (the stateless presentation half of
 * [AiTutorScreen]), closing the Compose UI test gap D92 explicitly disclosed as LOW (this screen had
 * zero androidTest coverage — only [AiTutorViewModelTest]'s plain JVM tests). Same "hand-built
 * `uiState`, no network/ViewModel" convention as `ExploreScreenContentTest`/
 * `CourseDetailsScreenContentTest`. See `execution/DECISIONS_LOG.md` D94.
 */
class AiTutorContentTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        uiState: AiTutorUiState,
        onQuickActionTapped: (AiQuickAction) -> Unit = {},
        onRetryTapped: (String) -> Unit = {},
        onInputChanged: (String) -> Unit = {},
        onSendTapped: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MentoraTheme {
                AiTutorContent(
                    uiState = uiState,
                    onInputChanged = onInputChanged,
                    onSendTapped = onSendTapped,
                    onQuickActionTapped = onQuickActionTapped,
                    onRetryTapped = onRetryTapped,
                )
            }
        }
    }

    @Test
    fun emptyConversation_rendersTheWelcomeBubble() {
        setContent(uiState = AiTutorUiState())

        val welcomeText = composeTestRule.activity.getString(R.string.ai_tutor_welcome_message)
        composeTestRule.onNodeWithText(welcomeText).assertIsDisplayed()
    }

    @Test
    fun nonEmptyConversation_rendersTheWelcomeBubbleNoLonger() {
        setContent(
            uiState = AiTutorUiState(items = listOf(AiTutorThreadItem.UserMessage("u1", "Hello"))),
        )

        val welcomeText = composeTestRule.activity.getString(R.string.ai_tutor_welcome_message)
        composeTestRule.onNodeWithText(welcomeText).assertDoesNotExist()
    }

    @Test
    fun userAndAssistantMessages_bothRenderTheirOwnText() {
        setContent(
            uiState = AiTutorUiState(
                items = listOf(
                    AiTutorThreadItem.UserMessage("u1", "What is a coroutine?"),
                    AiTutorThreadItem.AssistantMessage("a1", "A coroutine is a lightweight thread."),
                ),
            ),
        )

        composeTestRule.onNodeWithText("What is a coroutine?").assertIsDisplayed()
        composeTestRule.onNodeWithText("A coroutine is a lightweight thread.").assertIsDisplayed()
    }

    @Test
    fun thinkingIndicator_rendersItsOwnTestTag() {
        setContent(uiState = AiTutorUiState(items = listOf(AiTutorThreadItem.Thinking("a1"))))

        composeTestRule.onNodeWithTag(AiTutorThinkingIndicatorTestTag).assertIsDisplayed()
    }

    @Test
    fun preStreamError_rendersARetryButton_tappingItInvokesOnRetryTappedWithTheItemId() {
        var retriedId: String? = null
        setContent(
            uiState = AiTutorUiState(
                items = listOf(
                    AiTutorThreadItem.PreStreamError("a1", ApiErrorCode.RateLimitedAiTutor, retryContent = "Quiz me"),
                ),
            ),
            onRetryTapped = { retriedId = it },
        )

        composeTestRule.onNodeWithTag(aiTutorRetryButtonTestTag("a1")).performClick()
        assertEquals("a1", retriedId)
    }

    @Test
    fun streamFailedWithPartialText_rendersBothTheRealBubbleAndItsOwnRetryButton() {
        setContent(
            uiState = AiTutorUiState(
                items = listOf(
                    AiTutorThreadItem.AssistantMessage(
                        "a1",
                        text = "The answer is",
                        streamFailedRetryContent = "Explain X",
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("The answer is").assertIsDisplayed()
        composeTestRule.onNodeWithTag(aiTutorRetryButtonTestTag("a1")).assertIsDisplayed()
    }

    @Test
    fun tappingAQuickAction_invokesOnQuickActionTappedWithThatAction_whenNotSending() {
        var tapped: AiQuickAction? = null
        setContent(
            uiState = AiTutorUiState(isSending = false),
            onQuickActionTapped = { tapped = it },
        )

        composeTestRule.onNodeWithTag(aiTutorQuickActionTestTag(AiQuickAction.ExplainThisLesson)).performClick()
        assertEquals(AiQuickAction.ExplainThisLesson, tapped)
    }

    /** Review fix regression (MEDIUM, D92) — quick-action chips must not silently no-op while a turn
     *  is already in flight; they should read as genuinely disabled instead. */
    @Test
    fun quickActionChips_areDisabled_whileATurnIsInFlight() {
        setContent(uiState = AiTutorUiState(isSending = true))

        composeTestRule.onNodeWithTag(aiTutorQuickActionTestTag(AiQuickAction.ExplainThisLesson)).assertIsNotEnabled()
    }

    /** Review fix regression (HIGH, D92) — the composer TEXT FIELD itself must stay enabled while a
     *  turn is in flight; disabling it silently dismisses the keyboard (Compose clears focus from a
     *  disabled field), violating `ux/MOBILE_UX.md § 14`'s "keeps the keyboard open" requirement. Only
     *  the send button is allowed to gate on [AiTutorUiState.isSending]. */
    @Test
    fun composerField_staysEnabled_whileATurnIsInFlight() {
        setContent(uiState = AiTutorUiState(isSending = true, inputText = "typing more…"))

        composeTestRule.onNodeWithTag(AiTutorComposerFieldTestTag).assertIsEnabled()
    }

    @Test
    fun sendButton_isDisabled_whileATurnIsInFlight() {
        setContent(uiState = AiTutorUiState(isSending = true, inputText = "Hello"))

        composeTestRule.onNodeWithTag(AiTutorSendButtonTestTag).assertIsNotEnabled()
    }

    @Test
    fun sendButton_isDisabled_whenTheComposerIsBlank() {
        setContent(uiState = AiTutorUiState(isSending = false, inputText = "   "))

        composeTestRule.onNodeWithTag(AiTutorSendButtonTestTag).assertIsNotEnabled()
    }

    @Test
    fun sendButton_isEnabled_withNonBlankInputAndNoTurnInFlight() {
        setContent(uiState = AiTutorUiState(isSending = false, inputText = "Hello"))

        composeTestRule.onNodeWithTag(AiTutorSendButtonTestTag).assertIsEnabled()
    }

    @Test
    fun typingInTheComposer_invokesOnInputChangedWithTheTypedText() {
        var lastValue: String? = null
        setContent(uiState = AiTutorUiState(), onInputChanged = { lastValue = it })

        composeTestRule.onNodeWithTag(AiTutorComposerFieldTestTag).performTextInput("Hello")
        assertEquals("Hello", lastValue)
    }

    @Test
    fun tappingSend_invokesOnSendTapped() {
        var sendCount = 0
        setContent(uiState = AiTutorUiState(inputText = "Hello"), onSendTapped = { sendCount++ })

        composeTestRule.onNodeWithTag(AiTutorSendButtonTestTag).performClick()
        assertEquals(1, sendCount)
    }
}
