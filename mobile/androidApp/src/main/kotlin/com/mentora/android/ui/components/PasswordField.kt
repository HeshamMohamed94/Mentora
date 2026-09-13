package com.mentora.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * `design-system/COMPONENTS.md` § PasswordField (line 158-160) — extends [MentoraTextField].
 * "Must always ship the toggle — never a password field with no reveal option": the visibility
 * `IconButton` below is unconditional, not behind a parameter that could disable it. Masked
 * characters use [PasswordVisualTransformation] (Compose's built-in `•` mask), the same
 * `typography.body.medium` token as the unmasked text (the transformation only swaps glyphs, not
 * the text style).
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showPasswordLabel: String,
    hidePasswordLabel: String,
    modifier: Modifier = Modifier,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    MentoraTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        helperText = helperText,
        errorText = errorText,
        enabled = enabled,
        singleLine = true,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingContent = {
            MentoraIconButton(
                icon = if (visible) MentoraIconName.VisibilityOff else MentoraIconName.Visibility,
                contentDescription = if (visible) hidePasswordLabel else showPasswordLabel,
                onClick = { visible = !visible },
                modifier = Modifier,
            )
        },
    )
}
