package com.mentora.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.theme.stateOpacities

/**
 * `design-system/COMPONENTS.md` § Inputs — the base TextField (lines 133-156), shared by
 * [PasswordField]/[SearchField]/[MentoraSelect].
 *
 * **Built on Material3's `OutlinedTextField` deliberately, not a hand-rolled label** — this is the
 * exact D45 lesson called out in the task brief: a previous (web) TextField shipped a static,
 * always-visible label instead of the spec's floating behavior (starts inline/placeholder-sized at
 * rest, floats up and shrinks on focus or once filled). `OutlinedTextField`'s native `label` slot
 * already implements exactly that animation and already defers showing the separate `placeholder`
 * slot until the field is both focused AND empty (so an unfocused+empty field shows only the
 * unfloated label, never both stacked) — reusing it inherits the correct behavior for free instead
 * of re-implementing (and risking re-breaking) it by hand.
 *
 * Every color below resolves from `MaterialTheme.colorScheme`/`MaterialTheme.extendedColors`
 * (never a raw literal): border default/focused/error/success/disabled, background
 * default/disabled, label/placeholder/helper/error text — see [mentoraTextFieldColors]. Radius
 * `radius.medium` -> `MaterialTheme.shapes.small`. Typography: label `label.medium`, input/
 * placeholder `body.medium`, helper/error `caption` (`MaterialTheme.typography.labelSmall`, per
 * `MentoraTheme.kt`'s own `caption -> labelSmall` mapping).
 *
 * **Disclosed height deviation (F6):** the spec's literal field height is 52dp
 * (`component.input.height`), but forcing that exact height onto `OutlinedTextField` risks an
 * inverted-constraint conflict against M3's own internal `defaultMinSize` (`TextFieldDefaults.
 * MinHeight`, 56dp) — undefined/fragile behavior. Given the explicit priority on *correct
 * floating-label behavior* over exact pixel height (the whole point of building on `OutlinedTextField`
 * rather than hand-rolling one), this field is left at M3's own native height rather than forcing
 * 52dp and risking exactly the kind of subtle layout bug this task is written to avoid.
 * **Measured (bounds probe), the real rendered height with no helper/error text is 64dp** — not
 * 56dp (`TextFieldDefaults.MinHeight`, the nominal M3 default one might assume) and not the spec's
 * 52dp; label+placeholder+internal padding push it past M3's own nominal minimum. Recorded here so a
 * future task doesn't have to re-measure it. A full 52dp-exact rebuild via `BasicTextField`/a custom
 * `DecorationBox` would be the way to actually hit 52dp — out of scope for this disclosure-only fix.
 *
 * **Error state pairs icon + text + color** (`ACCESSIBILITY.md` § 7's "never color alone" rule) —
 * see [MentoraFieldSupportingText]: the error icon (`cancel`) and error text render together in
 * `color.error.default`, never a bare colored border with no icon/text signal.
 */
@Composable
fun MentoraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    success: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: MentoraIconName? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val isError = errorText != null
    val trailing: (@Composable () -> Unit)? = trailingContent ?: if (success && !isError) {
        { MentoraIcon(name = MentoraIconName.CheckCircle, contentDescription = null, size = MentoraDimens.iconSize.medium, tint = MaterialTheme.extendedColors.success) }
    } else {
        null
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // F10: programmatically links the error message to the field (ACCESSIBILITY.md § 7) so a
        // screen reader announces the actual text on focus, not just M3's generic "Error" state.
        modifier = modifier.semantics { if (errorText != null) error(errorText) },
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        leadingIcon = leadingIcon?.let {
            { MentoraIcon(name = it, contentDescription = null, size = MentoraDimens.iconSize.medium) }
        },
        trailingIcon = trailing,
        supportingText = if (helperText != null || errorText != null) {
            { MentoraFieldSupportingText(helperText = helperText, errorText = errorText) }
        } else {
            null
        },
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.small,
        colors = mentoraTextFieldColors(success = success),
    )
}

/** Test-only hook (`ui.test.onNodeWithTag`) for asserting the error icon renders alongside the text. */
const val MentoraFieldErrorIconTestTag = "mentora-field-error-icon"

/** Error (icon + text + color together) / helper (`typography.caption`, `color.text.secondary`) row. */
@Composable
internal fun MentoraFieldSupportingText(helperText: String?, errorText: String?) {
    if (errorText != null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MentoraIcon(
                name = MentoraIconName.Cancel,
                contentDescription = null,
                modifier = Modifier.testTag(MentoraFieldErrorIconTestTag),
                size = MentoraDimens.iconSize.small,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall, // typography.caption -> labelSmall.
                color = MaterialTheme.colorScheme.error,
            )
        }
    } else if (helperText != null) {
        Text(
            text = helperText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * `component.input.border`/`.background`/`.text` (design-tokens.json), mapped to
 * `OutlinedTextFieldDefaults.colors()`. [success] overrides the border to `color.success.default`
 * when the field isn't in an error state (error always wins — M3's own `isError` param already takes
 * precedence for the border/label/cursor colors it drives).
 */
@Composable
internal fun mentoraTextFieldColors(success: Boolean) = run {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val disabledContent = colorScheme.onSurface.copy(alpha = MaterialTheme.stateOpacities.disabledContent)
    val successBorder = if (success) extended.success else null

    OutlinedTextFieldDefaults.colors(
        focusedTextColor = colorScheme.onSurface,
        unfocusedTextColor = colorScheme.onSurface,
        disabledTextColor = disabledContent,
        errorTextColor = colorScheme.onSurface,
        focusedContainerColor = colorScheme.surface,
        unfocusedContainerColor = colorScheme.surface,
        disabledContainerColor = colorScheme.surfaceVariant,
        errorContainerColor = colorScheme.surface,
        cursorColor = colorScheme.primary,
        errorCursorColor = colorScheme.error,
        focusedBorderColor = successBorder ?: colorScheme.primary,
        unfocusedBorderColor = successBorder ?: colorScheme.outlineVariant,
        disabledBorderColor = colorScheme.outlineVariant,
        errorBorderColor = colorScheme.error,
        focusedLabelColor = colorScheme.onSurfaceVariant,
        unfocusedLabelColor = colorScheme.onSurfaceVariant,
        // F8 fix: disabled label = `color.text.disabled` (the same `disabledContent` expression
        // already used for the disabled value text above), not `onSurfaceVariant` (color.text.
        // secondary) — measured, the label previously rendered at full text.secondary strength
        // (#5F6069) instead of dimmed.
        disabledLabelColor = disabledContent,
        errorLabelColor = colorScheme.onSurfaceVariant,
        focusedPlaceholderColor = disabledContent,
        unfocusedPlaceholderColor = disabledContent,
        disabledPlaceholderColor = disabledContent,
        errorPlaceholderColor = disabledContent,
        focusedSupportingTextColor = colorScheme.onSurfaceVariant,
        unfocusedSupportingTextColor = colorScheme.onSurfaceVariant,
        // F8 fix: same as disabledLabelColor above — color.text.disabled, not color.text.secondary.
        disabledSupportingTextColor = disabledContent,
        errorSupportingTextColor = colorScheme.error,
    )
}
