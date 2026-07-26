package dev.viethung.numberinput

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Locale-aware numeric input with live thousands grouping and a Clear / ± / Done toolbar.
 *
 * Renders a Compose `BasicTextField` on Android and a native `UITextField` with a real
 * `UIInputAccessoryView` toolbar on iOS.
 *
 * This overload owns its [NumberInputState] internally. To drive the field from your own ViewModel
 * or DI graph, construct a [NumberInputState] yourself and use the other overload.
 */
@Composable
fun NumberInputField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
    config: NumberInputConfig = NumberInputConfig(),
    style: NumberInputStyle = NumberInputStyle(),
    enabled: Boolean = true,
) {
    // Keyed on config so a changed locale / digit cap rebuilds the state rather than silently
    // keeping stale formatting. Unlike viewModel(key=), remember is per-call-site, so two fields
    // with identical config never share an instance.
    val state = remember(config) {
        NumberInputState(initialValue = value, config = config)
    }

    // Outward: report only genuine value changes.
    var previous by remember { mutableStateOf(state.value) }
    LaunchedEffect(state.value) {
        if (state.value != previous) {
            previous = state.value
            onValueChange(state.value)
        }
    }

    // Inward: pull parent-driven changes in. Ignored mid-edit, and a no-op when already equal,
    // which is what stops the outward/inward binding loop.
    LaunchedEffect(value) { state.syncExternalValue(value) }

    NumberInputField(state = state, modifier = modifier, style = style, enabled = enabled)
}

/**
 * Overload for a caller-owned [state] — hoist it into a ViewModel, a Koin-provided holder, or
 * wherever your architecture keeps screen state.
 */
@Composable
fun NumberInputField(
    state: NumberInputState,
    modifier: Modifier = Modifier,
    style: NumberInputStyle = NumberInputStyle(),
    enabled: Boolean = true,
) {
    PlatformNumberInputField(
        state = state,
        modifier = modifier,
        style = style,
        enabled = enabled,
    )
}

/**
 * The platform rendering seam. Android draws the field in Compose; iOS hosts a native
 * `UITextField` so it can carry a real keyboard accessory toolbar.
 */
@Composable
internal expect fun PlatformNumberInputField(
    state: NumberInputState,
    modifier: Modifier,
    style: NumberInputStyle,
    enabled: Boolean,
)
