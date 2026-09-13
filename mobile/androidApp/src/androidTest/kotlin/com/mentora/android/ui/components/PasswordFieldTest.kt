package com.mentora.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The field's *underlying* value (`SemanticsProperties.EditableText`) is always the raw, unmasked
 * text regardless of [androidx.compose.ui.text.input.VisualTransformation] — masking is a rendering
 * concern. To genuinely assert "actually masks/unmasks," this reads the field's real laid-out text
 * (post-`VisualTransformation`) via the `GetTextLayoutResult` semantics action, the same mechanism
 * Compose itself uses to paint the field.
 */
private fun SemanticsNodeInteraction.getRenderedText(): String {
    val node = fetchSemanticsNode()
    val results = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    return results.firstOrNull()?.layoutInput?.text?.text.orEmpty()
}

/** T5 — `design-system/COMPONENTS.md` § PasswordField: the visibility toggle actually masks/unmasks. */
@RunWith(AndroidJUnit4::class)
class PasswordFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun visibilityToggleActuallyMasksAndUnmasksTheInput() {
        composeTestRule.setContent {
            MentoraTheme {
                var value by remember { mutableStateOf("") }
                PasswordField(
                    value = value,
                    onValueChange = { value = it },
                    label = "Password",
                    showPasswordLabel = "Show password",
                    hidePasswordLabel = "Hide password",
                    modifier = Modifier.testTag("password-field"),
                )
            }
        }

        composeTestRule.onNodeWithTag("password-field").performTextInput("secret")

        val masked = composeTestRule.onNodeWithTag("password-field").getRenderedText()
        assertTrue("expected masked rendering, got \"$masked\"", masked.length == "secret".length && masked.none { it.isLetter() })

        composeTestRule.onNodeWithContentDescription("Show password").performClick()

        val unmasked = composeTestRule.onNodeWithTag("password-field").getRenderedText()
        assertEquals("secret", unmasked)

        composeTestRule.onNodeWithContentDescription("Hide password").performClick()

        val maskedAgain = composeTestRule.onNodeWithTag("password-field").getRenderedText()
        assertTrue(maskedAgain.none { it.isLetter() })
    }
}
