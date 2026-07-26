package dev.viethung.numberinput

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wrap your screen in this to get a Clear / ± / Done toolbar pinned to the software keyboard.
 *
 * ```kotlin
 * NumberInputHost {
 *     Column { NumberInputField(...) }
 * }
 * ```
 *
 * **Android:** the toolbar is drawn bottom-aligned inside this host with [imePadding], which is the
 * supported way to ride the keyboard — Compose drives that inset from the IME animation itself, so
 * the toolbar moves in lockstep instead of chasing it. It must be laid out at the bottom of the
 * window for that to work, which is the whole reason this wrapper exists: a field sitting mid-column
 * cannot reach there. Two things are required of the consumer, and neither can be set from a
 * library: `android:windowSoftInputMode="adjustResize"` on the Activity, and edge-to-edge
 * (`enableEdgeToEdge()`). Without them the Activity never receives IME insets and the toolbar will
 * sit still while the keyboard opens.
 *
 * **iOS:** does nothing. The field there is a native `UITextField` carrying a real
 * `inputAccessoryView`, which the system attaches to the keyboard, so no host is involved. The
 * wrapper is accepted anyway so the same code compiles on both platforms.
 *
 * Omitting the host is supported: the field falls back to rendering its toolbar inline, directly
 * beneath itself. That keeps the actions reachable rather than silently dropping them, but it will
 * not track the keyboard.
 */
@Composable
fun NumberInputHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val host = remember { NumberInputToolbarHost() }
    CompositionLocalProvider(LocalNumberInputToolbarHost provides host) {
        Box(modifier.fillMaxSize()) {
            content()
            host.request?.let { request ->
                NumberInputToolbarBar(
                    state = request.state,
                    style = request.style,
                    onDone = request.onDone,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding(),
                )
            }
        }
    }
}

/**
 * Slot the focused field publishes itself into. Single-slot on purpose: only one field can hold
 * focus, so a second registration always means focus moved.
 */
internal class NumberInputToolbarHost {
    var request: NumberInputToolbarRequest? by mutableStateOf(null)
        private set

    fun show(state: NumberInputState, style: NumberInputStyle, onDone: () -> Unit) {
        request = NumberInputToolbarRequest(state, style, onDone)
    }

    /** Ignores stale hides from a field that already lost the slot to another one. */
    fun hide(state: NumberInputState) {
        if (request?.state === state) request = null
    }
}

internal data class NumberInputToolbarRequest(
    val state: NumberInputState,
    val style: NumberInputStyle,
    val onDone: () -> Unit,
)

internal val LocalNumberInputToolbarHost = compositionLocalOf<NumberInputToolbarHost?> { null }

/**
 * The toolbar itself. Shared so the host and the inline fallback cannot drift apart.
 *
 * [onDone] is supplied by the field rather than defaulted here. It has to drop focus, not just
 * commit — committing alone leaves the keyboard up and the toolbar on screen. Routing it from the
 * field keeps one dismissal path for both the host and the inline fallback.
 */
@Composable
internal fun NumberInputToolbarBar(
    state: NumberInputState,
    style: NumberInputStyle,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(style.toolbarBackgroundColor)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarAction(
            label = style.clearLabel,
            enabled = state.clearEnabled,
            tint = style.toolbarTint,
            disabledAlpha = style.disabledAlpha,
            testTag = TAG_CLEAR,
            onClick = state::clear,
        )
        Spacer(Modifier.width(4.dp))
        ToolbarAction(
            label = style.signLabel,
            enabled = state.signEnabled,
            tint = style.toolbarTint,
            disabledAlpha = style.disabledAlpha,
            testTag = TAG_SIGN,
            onClick = state::toggleSign,
        )
        Spacer(Modifier.weight(1f))
        ToolbarAction(
            label = style.doneLabel,
            enabled = true,
            tint = style.toolbarTint,
            disabledAlpha = style.disabledAlpha,
            testTag = TAG_DONE,
            onClick = onDone,
        )
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    enabled: Boolean,
    tint: Color,
    disabledAlpha: Float,
    testTag: String,
    onClick: () -> Unit,
) {
    BasicText(
        text = label,
        style = TextStyle(
            color = tint.copy(alpha = if (enabled) tint.alpha else tint.alpha * disabledAlpha),
            fontSize = 16.sp,
        ),
        modifier = Modifier
            .testTag(testTag)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
