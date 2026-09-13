package com.mentora.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/**
 * `design-system/COMPONENTS.md` § SearchField (lines 162-164) — extends [MentoraTextField]. Leading
 * icon fixed to `search` (`icon.medium`, `color.text.secondary` — [MentoraTextField]'s default
 * leading-icon tint). Trailing `close` `IconButton` appears only when [value] is non-empty and
 * clears the field on tap.
 *
 * (The spec's note about a recessed `color.surface.variant` resting background "when embedded in a
 * navbar" is a navbar-specific override, not part of this general-purpose field — left for whichever
 * later task builds that navbar-embedded usage.)
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    MentoraTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        singleLine = true,
        keyboardType = KeyboardType.Text,
        leadingIcon = MentoraIconName.Search,
        trailingContent = if (value.isNotEmpty()) {
            {
                MentoraIconButton(
                    icon = MentoraIconName.Close,
                    contentDescription = clearContentDescription,
                    onClick = { onValueChange("") },
                )
            }
        } else {
            null
        },
    )
}
