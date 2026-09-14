package com.mentora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.stateOpacities

/**
 * `design-system/COMPONENTS.md` § Select / Dropdown (lines 189-241). Field styling is identical to
 * [MentoraTextField]'s (reuses [mentoraTextFieldColors]) — Select is an input variant, not a visually
 * distinct control. **Built on Material3's `ExposedDropdownMenuBox`**, per the spec's own explicit
 * native-mapping instruction (line 235) — not a hand-built popover.
 *
 * Trailing indicator `expand_more`/`expand_less` — non-directional, **never mirrored in RTL**: the
 * icon is used exactly as ported (no `Modifier` RTL-flip applied to it anywhere in this file).
 * Option list: `color.surface.elevated`, `elevation.3`, `radius.medium`. The spec cites
 * `touchTarget.web_px` (44dp, [MentoraDimens.touchTargetWeb]) for option rows; the "Loading
 * options…" placeholder row (non-interactive) uses that literal height. The real, selectable option
 * rows instead use `Modifier.heightIn(min = touchTargetMin)` (48dp floor) — **measured** (bounds
 * probe): forcing an exact `.height(touchTargetWeb)` on a `DropdownMenuItem` clamps its *internal*
 * `clickable`'s tappable area down to ~44.2dp (M3's own `minimumInteractiveComponentSize()` can't
 * inflate past a caller-imposed exact height), which fails ACCESSIBILITY.md § 4's 48dp Android
 * minimum. Since `DropdownMenuItem`'s `clickable` is created inside the composable (not something a
 * caller-supplied modifier can reorder around, unlike this kit's own [MentoraButton]/[CategoryChip]),
 * a `heightIn` floor lets M3's own default win instead, measuring at a clean 48dp for both the visual
 * row and its tappable area — see the option-row `DropdownMenuItem` call site for the full measured
 * finding.
 *
 * Selected-value vs. placeholder color distinction, error state, disabled state, individually
 * disabled options (`color.text.disabled`, excluded from selection), and a loading-options state are
 * all supported — see each parameter's kdoc below.
 *
 * **Field height (F6 disclosure):** the field itself is the same `OutlinedTextField` as
 * [MentoraTextField], so it shares that file's height deviation — spec is 52dp
 * (`component.input.height`), **measured** (bounds probe) real rendered height with no
 * helper/error text is 64dp, not the spec's 52dp or M3's nominal 56dp default. See
 * [MentoraTextField]'s own kdoc for the full disclosure; not re-derived here.
 */
data class MentoraSelectOption<T>(val value: T, val label: String, val enabled: Boolean = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MentoraSelect(
    label: String,
    options: List<MentoraSelectOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    /** Disabled-looking field + inline spinner replacing the trailing indicator; if opened before
     * options resolve, the menu shows a single "Loading options…" row instead of the real list. */
    loading: Boolean = false,
    // T19 — was a raw English literal (D93 LOW-6, disclosed); every Select in this app is currently
    // static (this default was unreachable from any real call site), so this had no visible effect
    // yet, but a future dynamically-populated Select would have silently shown untranslated English
    // under Arabic. `stringResource` as a Composable function's own default parameter expression is
    // evaluated per-call like any other default, same idiom Compose itself uses throughout.
    loadingOptionsLabel: String = stringResource(R.string.select_loading_options_label),
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val opacities = MaterialTheme.stateOpacities
    val disabledText = colorScheme.onSurface.copy(alpha = opacities.disabledContent)
    val fieldEnabled = enabled && !loading

    val selectedOption = options.firstOrNull { it.value == selected }
    val displayText = selectedOption?.label ?: placeholder.orEmpty()
    // Disabled/loading always renders disabledText regardless of whether a value is selected —
    // otherwise a disabled Select's value text stays full-strength while the sibling
    // MentoraTextField correctly dims (F2 fix). The placeholder branch already used disabledText,
    // so this only changes behavior for the "disabled/loading AND has a selected value" case.
    val displayColor = when {
        !fieldEnabled -> disabledText
        selectedOption != null -> colorScheme.onSurface
        else -> disabledText
    }

    ExposedDropdownMenuBox(
        expanded = expanded && fieldEnabled,
        onExpandedChange = { if (fieldEnabled) expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            enabled = fieldEnabled,
            singleLine = true, // COMPONENTS.md line 237 — 1-line truncation contract (F3 fix).
            isError = errorText != null,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = fieldEnabled)
                .fillMaxWidth()
                .semantics { if (errorText != null) error(errorText) },
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            // .copy(color = ...) preserves letterSpacing/lineHeight/fontWeight/fontFamily from the
            // real bodyMedium token (including MentoraTheme's Arabic-specific adjustments) instead of
            // dropping everything but fontSize (F3 fix).
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = displayColor),
            trailingIcon = {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MentoraDimens.iconSize.medium),
                        strokeWidth = MentoraDimens.borderWidthFocus,
                        color = colorScheme.onSurfaceVariant,
                    )
                } else {
                    MentoraIcon(
                        name = if (expanded) MentoraIconName.ExpandLess else MentoraIconName.ExpandMore,
                        contentDescription = null,
                        size = MentoraDimens.iconSize.medium,
                    )
                }
            },
            supportingText = if (helperText != null || errorText != null) {
                { MentoraFieldSupportingText(helperText = helperText, errorText = errorText) }
            } else {
                null
            },
            shape = MaterialTheme.shapes.small,
            colors = mentoraTextFieldColors(success = false),
        )

        ExposedDropdownMenu(
            expanded = expanded && fieldEnabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colorScheme.surfaceContainerHigh), // color.surface.elevated.
        ) {
            if (loading) {
                DropdownMenuItem(
                    text = { Text(loadingOptionsLabel, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant) },
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.height(MentoraDimens.touchTargetWeb),
                )
            } else {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    val optionTextColor = when {
                        !option.enabled -> disabledText
                        isSelected -> colorScheme.onPrimaryContainer
                        else -> colorScheme.onSurface
                    }
                    val optionBackground = if (isSelected) colorScheme.primaryContainer else Color.Transparent
                    DropdownMenuItem(
                        text = { Text(option.label, style = MaterialTheme.typography.bodyMedium, color = optionTextColor) },
                        onClick = {
                            if (option.enabled) {
                                onSelect(option.value)
                                expanded = false
                            }
                        },
                        enabled = option.enabled,
                        // F4 fix + disclosure: a hard `.height(touchTargetWeb)` (44dp) here overrides
                        // M3's own internal `minimumInteractiveComponentSize()` DOWNWARD — measured
                        // (bounds probe): the option row's real tappable area came out to ~44.2dp,
                        // not 48dp, because `.height()` forces an exact constraint that clamps
                        // DropdownMenuItem's internal clickable before it can inflate. Since
                        // `DropdownMenuItem`'s clickable is created *inside* the composable (not
                        // reorderable from the caller side, unlike MentoraButton/CategoryChip above,
                        // which own their own `clickable` call), there is no ordering of an
                        // externally-applied modifier that both keeps the row at an exact 44dp AND
                        // separately inflates just the tappable region — measured, every variant
                        // tried (`minimumInteractiveComponentSize()` before/after `.height(44.dp)`,
                        // wrapping in an outer `Box` with `defaultMinSize`) still clamped to ~44.2dp.
                        // `.heightIn(min = touchTargetMin)` (a floor, not a fixed value) instead lets
                        // M3's own default win, which measured at a clean 48dp — matching
                        // ACCESSIBILITY.md § 4's Android minimum outright rather than forcing a
                        // sub-minimum 44dp with a broken hit-slop claim (same "disclosed correct
                        // deviation over a broken exact value" reasoning as MentoraTabs' height, see
                        // that file's kdoc).
                        modifier = Modifier
                            .heightIn(min = MentoraDimens.touchTargetMin)
                            .background(optionBackground),
                    )
                }
            }
        }
    }
}
