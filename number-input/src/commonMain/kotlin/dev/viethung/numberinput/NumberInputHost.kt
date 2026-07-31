package dev.viethung.numberinput

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
 * **iOS:** does nothing for the default system-keyboard path. The field there is a native
 * `UITextField` carrying a real `inputAccessoryView`, which the system attaches to the keyboard, so no
 * host is involved. The wrapper is accepted anyway so the same code compiles on both platforms.
 *
 * **With [NumberInputConfig.useBuiltInKeypad], the host matters on both platforms.** The keypad is
 * Compose on iOS too, and nothing hands a Compose composable to the system keyboard's accessory view,
 * so it has to be laid out at the bottom of the window like the Android toolbar. It brings its own
 * toolbar row, so the host renders one or the other, never both.
 *
 * Omitting the host is supported, and degrades differently per platform:
 *
 * - *Android* renders the toolbar — or the keypad — inline, directly beneath the field. The actions
 *   stay reachable rather than being silently dropped, but it will not sit above the safe area, an
 *   inline keypad pushes content below it down, and neither animates. The host is what makes the
 *   keypad slide in step with its own inset; the inline fallback predates that and is not asked to
 *   match it.
 * - *iOS* has no inline fallback — the field is a `UIKitView`. A field with `useBuiltInKeypad = true`
 *   therefore falls back to the **system keyboard** and its `UIToolbar` accessory, since suppressing
 *   the keyboard with nothing to draw a keypad would strand the field: focusable, caret showing, no
 *   way to type. The decimal key is then the device region's rather than the field's, which is the one
 *   thing the keypad exists to fix, so wrap the screen in a host if you want it. Values stay correct
 *   either way — [NumberInputState] translates a keypad "." into the field's own separator whichever
 *   keyboard sent it. See `NumberInputCoordinator.keypadHosted`.
 *
 * The keypad overlays your content, so a field low on the screen would sit behind it. Apply
 * [numberInputKeypadPadding] to your scroll container to reserve the space — the keypad's counterpart
 * to `Modifier.imePadding()`, which handles the system keyboard. See [LocalNumberInputKeypadHeight] for
 * the raw measurement.
 */
@Composable
fun NumberInputHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val host = remember { NumberInputToolbarHost() }
    val density = LocalDensity.current

    // The keypad's own measured height. Held here rather than inside the keypad branch so it survives
    // that branch leaving composition, which is what the exit animation needs it for. Deliberately
    // *not* cleared on close: it is the height to animate from on the next open, and zeroing it would
    // make every re-open snap into place and then jump once re-measurement arrived.
    var measuredKeypadHeight by remember { mutableStateOf(0.dp) }

    // The last request, kept past its own removal. `host.request` goes null the instant a field loses
    // focus, so without this the keypad would leave composition immediately and have nothing left on
    // screen to animate out. Cleared once the exit animation reports it has finished.
    var retainedRequest by remember { mutableStateOf<NumberInputToolbarRequest?>(null) }

    val keypadRequested = host.request?.state?.config?.useBuiltInKeypad == true

    // Retention is for one case only: a keypad closing because focus left it with nothing else
    // claiming the slot. If a *different* request — another field, of either kind — has already taken
    // the slot, that field's own UI must not wait on the old keypad's exit animation, so this cuts the
    // retention immediately rather than let `keypadVisible` below stay true. Sample app fields other
    // than the keypad one hit this on every tap between them.
    if (!keypadRequested && host.request != null && retainedRequest != null &&
        host.request !== retainedRequest
    ) {
        retainedRequest = null
    }

    // Where the keypad is heading: its full height while requested, zero once not. Settled, never
    // interpolated — anything that needs to react *once* per open/close keys on this rather than on the
    // animating value below. See [LocalNumberInputKeypadTargetHeight].
    val targetKeypadHeight = if (keypadRequested) measuredKeypadHeight else 0.dp

    // The single animated value, and the reason the keypad's edge and the space reserved for it cannot
    // drift apart: it is both the offset the keypad is drawn at and the inset published to content.
    // Faster out than in, which is what both platforms' own keyboards do.
    val animatedKeypadHeight by animateDpAsState(
        targetValue = targetKeypadHeight,
        animationSpec = if (keypadRequested) KeypadEnterSpec else KeypadExitSpec,
        label = "numberInputKeypadHeight",
        // Where the retained request is released — the one moment the keypad is provably off screen.
        // `onSizeChanged` never fires on removal, and disposal now happens at the *end* of the exit
        // rather than when focus was lost, so neither can be the trigger.
        finishedListener = { settled -> if (settled == 0.dp) retainedRequest = null },
    )

    // Still on screen while sliding out, after the request itself has gone.
    val keypadVisible = keypadRequested || retainedRequest != null

    // Latch only a keypad request — never a toolbar-only one. If focus moved straight from a keypad
    // field to a toolbar field, `retainedRequest` must keep pointing at the *outgoing* keypad's data
    // until its exit animation finishes; overwriting it with the incoming toolbar request here would
    // animate the wrong field out. Written during composition rather than a LaunchedEffect, which would
    // land a frame late — long enough for the keypad to blink out before its exit animation started.
    if (keypadRequested) host.request?.let { if (retainedRequest !== it) retainedRequest = it }

    CompositionLocalProvider(
        LocalNumberInputToolbarHost provides host,
        LocalNumberInputKeypadHeight provides animatedKeypadHeight,
        LocalNumberInputKeypadTargetHeight provides targetKeypadHeight,
    ) {
        Box(modifier.fillMaxSize()) {
            content()
            // Drawn from the retained request while the keypad animates out, so `host.request` being
            // null is not the end of its presence on screen. The toolbar-only path is unaffected: it
            // does not animate, so it follows `host.request` directly.
            val request = if (keypadVisible) retainedRequest else host.request
            request?.let { request ->
                // The keypad carries the toolbar itself, so this picks one or the other rather than
                // stacking them.
                //
                // No imePadding when the keypad is showing: it *replaces* the system keyboard, which
                // both platforms suppress, so that inset is zero and reserving space for it would
                // leave a gap. The navigation-bar inset still applies — the keypad's bottom row would
                // otherwise sit under the home indicator.
                val bottom = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()

                // Resolved here, not by the field that published `request`. This request can outlive
                // that field's focus by a full exit animation (see `retainedRequest` above), so
                // resolving in the field would freeze the style at whichever appearance held when focus
                // arrived — a device flipped to light mid-edit would keep a dark keypad, since the
                // field is no longer the one deciding. Resolving here means every frame reads the
                // appearance current *at that frame*, for as long as anything is on screen to read it.
                val resolvedStyle = request.style.resolvedForCurrentAppearance()

                if (request.state.config.useBuiltInKeypad) {
                    NumberInputKeypad(
                        state = request.state,
                        style = resolvedStyle,
                        onDone = request.onDone,
                        modifier = bottom
                            // Slides by exactly the height the animation has not yet given back: at
                            // rest this is 0 (measured == animated); at the start of the entrance it is
                            // the full measured height (animated == 0), pushing the keypad fully below
                            // the bottom edge. The same offset that drives this is published as the
                            // inset below, so the two cannot disagree about where the boundary is.
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = (measuredKeypadHeight - animatedKeypadHeight).roundToPx(),
                                )
                            }
                            // Measured after navigationBarsPadding, so the published height covers
                            // everything between the keypad's top edge and the bottom of the window.
                            // Not affected by the offset above: onSizeChanged reports this modifier
                            // chain's own size, not its placement.
                            .onSizeChanged { size ->
                                val measured = with(density) { size.height.toDp() }
                                if (measured != measuredKeypadHeight) measuredKeypadHeight = measured
                            },
                    )
                } else {
                    NumberInputToolbarBar(
                        state = request.state,
                        style = resolvedStyle,
                        onDone = request.onDone,
                        modifier = bottom.imePadding(),
                    )
                }
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
 * Height the built-in keypad currently occupies at the bottom of [NumberInputHost], or `0.dp` when no
 * keypad is showing. Includes the navigation-bar inset the host applies, so it is the full height
 * covered measured from the bottom of the window.
 *
 * Read this to keep your own content clear of the keypad — or use [numberInputKeypadPadding], which
 * does it for you. The system keyboard needs neither, since Compose already publishes it as
 * `WindowInsets.ime` and `Modifier.imePadding()` consumes it; the keypad is drawn by this library, so
 * it has to publish its own equivalent.
 *
 * Measured rather than derived from [NumberInputKeypadStyle.keyHeight]: the toolbar row, the row spacing and
 * the navigation-bar inset all contribute, and a computed guess would drift the moment any of them
 * changed.
 *
 * **This value animates.** It sweeps from `0.dp` to the keypad's height as the keypad slides in, and
 * back as it slides out — the same value the keypad's own offset is drawn from, so padding taken from it
 * tracks the keypad's edge exactly rather than snapping ahead of it. That also means it changes on every
 * frame of the transition: read it for layout, but key effects on
 * [LocalNumberInputKeypadTargetHeight] instead, or they will re-run ~15 times per open.
 */
val LocalNumberInputKeypadHeight = compositionLocalOf { 0.dp }

/**
 * Where [LocalNumberInputKeypadHeight] is heading: the keypad's full measured height while one is
 * showing, `0.dp` once it is dismissed. Never interpolated — it changes once per open and once per
 * close.
 *
 * The value to key a `LaunchedEffect` or `DisposableEffect` on. [LocalNumberInputKeypadHeight] is the
 * one to lay out against.
 */
val LocalNumberInputKeypadTargetHeight = compositionLocalOf { 0.dp }

/**
 * How the keypad enters and leaves, and with it the inset published to content.
 *
 * Not configurable through [NumberInputStyle]: the keypad stands in for the system keyboard, so the
 * timing that matters is the platform's, not a consumer's design system. Slightly faster out than in,
 * which is what both platforms' own keyboards do.
 */
private val KeypadEnterSpec = tween<Dp>(durationMillis = 250, easing = FastOutSlowInEasing)
private val KeypadExitSpec = tween<Dp>(durationMillis = 200, easing = FastOutLinearInEasing)

/**
 * Reserve space for the built-in keypad — the keypad's counterpart to `Modifier.imePadding()`.
 *
 * Apply it to the **scroll container**, before `verticalScroll`, rather than to the content inside it.
 * Padding the container shrinks the viewport, which is what makes a focused field scrollable up out
 * from behind the keypad; padding the content only adds space below it and leaves the viewport, and so
 * the obscured region, unchanged:
 *
 * ```kotlin
 * Column(
 *     Modifier
 *         .fillMaxSize()
 *         .numberInputKeypadPadding()   // shrink the viewport first
 *         .verticalScroll(scrollState)
 * ) { /* fields */ }
 * ```
 *
 * Zero unless a keypad is showing, so it costs nothing on the default system-keyboard path.
 */
@Composable
fun Modifier.numberInputKeypadPadding(): Modifier =
    this.padding(bottom = LocalNumberInputKeypadHeight.current)

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
            .background(style.toolbar.backgroundColor)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarAction(
            label = style.toolbar.clearLabel,
            enabled = state.clearEnabled,
            tint = style.toolbar.tint,
            disabledAlpha = style.disabledAlpha,
            testTag = TAG_CLEAR,
            onClick = state::clear,
        )
        Spacer(Modifier.width(4.dp))
        ToolbarAction(
            label = style.toolbar.signLabel,
            enabled = state.signEnabled,
            tint = style.toolbar.tint,
            disabledAlpha = style.disabledAlpha,
            testTag = TAG_SIGN,
            onClick = state::toggleSign,
        )
        Spacer(Modifier.weight(1f))
        ToolbarAction(
            label = style.toolbar.doneLabel,
            enabled = true,
            tint = style.toolbar.tint,
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
