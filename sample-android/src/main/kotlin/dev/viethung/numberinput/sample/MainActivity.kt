package dev.viethung.numberinput.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import dev.viethung.numberinput.NumberInputConfig
import dev.viethung.numberinput.NumberInputField
import dev.viethung.numberinput.NumberInputHost
import dev.viethung.numberinput.NumberInputStyle
import dev.viethung.numberinput.numberInputKeypadPadding

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // enableEdgeToEdge is the other half of the manifest's adjustResize. NumberInputHost pins its
        // toolbar with imePadding(), which reads zero unless the window actually extends behind the
        // system bars. The README asks consumers for both; the sample has to do both.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SampleApp() }
    }
}

/**
 * Every case in here is one the old sample could not show.
 *
 * That is the selection rule, not a wish for coverage: the app this library used to be verified
 * against never set `textAlign`, never zeroed a border, and never gave a field a fixed height with a
 * visible background — so three rendering defects lived in the field's box through two releases while
 * the screenshots all looked right. A sample only proves the parameters it exercises. Adding a
 * parameter to `NumberInputStyle` without adding it here re-opens exactly that gap.
 */
@Composable
private fun SampleApp() {
    NumberInputHost {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF4F4F2))
                .safeDrawingPadding()
                // Both insets, and in this order. imePadding covers the system keyboard; the keypad is
                // drawn by the library and no platform inset describes it, so it publishes its own.
                // Applied to the scrolling *container* rather than its content — padding the content
                // leaves the obscured region exactly as obscured, which is the mistake the README
                // calls out.
                .imePadding()
                .numberInputKeypadPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Heading("number-input :sample-android")
            Note("Built from the library source, on the library's own toolchain. No mavenLocal.")

            RightAligned()
            Centered()
            ZeroBorder()
            FixedHeight()
            CustomPadding()
            Keypad()

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * `TextAlign.End` — accepted and silently ignored on Android before 2.3.0.
 *
 * The value must sit hard against the field's trailing inset. If it is at the leading edge, the node
 * alignment in the decoration box has regressed and passing `textAlign` through `TextStyle` is doing
 * nothing again, which is exactly how this looked for two releases.
 */
@Composable
private fun RightAligned() {
    var value by remember { mutableStateOf<Double?>(19198.2) }
    Case(
        title = "textAlign = End",
        expectation = "Value hard against the right inset, not the left.",
        value = value,
    ) {
        NumberInputField(
            value = value,
            onValueChange = { value = it },
            style = NumberInputStyle(textAlign = TextAlign.End, textSize = 17.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The middle case, which distinguishes "alignment works" from "alignment is stuck at End". */
@Composable
private fun Centered() {
    var value by remember { mutableStateOf<Double?>(2500.0) }
    Case(
        title = "textAlign = Center",
        expectation = "Value centred — proves alignment is read, not hardcoded.",
        value = value,
    ) {
        NumberInputField(
            value = value,
            onValueChange = { value = it },
            style = NumberInputStyle(textAlign = TextAlign.Center, textSize = 17.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * `borderWidth = 0.dp` — drew a 1px hairline before 2.3.0.
 *
 * `Modifier.border` admits a zero width and strokes it as a hairline, so a consumer moving chrome onto
 * their own wrapper got a grey line on Android and nothing on iOS. The background is tinted here so a
 * returning hairline is visible against it rather than lost on white.
 */
@Composable
private fun ZeroBorder() {
    var value by remember { mutableStateOf<Double?>(-1234.5) }
    Case(
        title = "borderWidth = 0.dp",
        expectation = "No outline at all. Any grey line means the hairline is back.",
        value = value,
    ) {
        NumberInputField(
            value = value,
            onValueChange = { value = it },
            style = NumberInputStyle(
                borderWidth = 0.dp,
                backgroundColor = Color(0xFFE8E8DF),
                textSize = 17.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A caller-sized field — the wrap-content-height case.
 *
 * Before 2.3.0 the decoration box sized to its text, so background and border drew ~40dp inside a 56dp
 * field and sat against the top, leaving a strip of bare surface underneath. iOS *requires* an explicit
 * height (a `UIKitView` has no useful intrinsic size), so this is the shape every real design uses.
 */
@Composable
private fun FixedHeight() {
    var value by remember { mutableStateOf<Double?>(88.0) }
    Case(
        title = "Explicit height, 56dp",
        expectation = "Fill and border span the whole 56dp; text vertically centred.",
        value = value,
    ) {
        NumberInputField(
            value = value,
            onValueChange = { value = it },
            style = NumberInputStyle(
                backgroundColor = Color(0xFFDCE7DC),
                borderColor = Color(0xFF4C6A4C),
                borderWidth = 2.dp,
                cornerRadius = 12.dp,
                textSize = 17.sp,
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )
    }
}

/**
 * `contentPadding`, new in 2.3.0 and the reason the parameter exists.
 *
 * Asymmetric on purpose: equal values pass whether or not start/end are resolved against the layout
 * direction, so they would not catch a mapping that ignores it.
 *
 * Only the *start* value is visible here, because the text is start-aligned and simply never reaches
 * the trailing inset — the end value governs where it would stop, which needs `TextAlign.End` to see.
 * That half, and the RTL swap, are pinned by `IosFieldContentPaddingTest` rather than by eye.
 */
@Composable
private fun CustomPadding() {
    var value by remember { mutableStateOf<Double?>(42.0) }
    Case(
        title = "contentPadding = start 40, end 8",
        expectation = "Text starts 40dp in from the border, not the default 12dp.",
        value = value,
    ) {
        NumberInputField(
            value = value,
            onValueChange = { value = it },
            style = NumberInputStyle(
                contentPadding = PaddingValues(start = 40.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                textSize = 17.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The built-in keypad, on a de-DE field.
 *
 * de-DE because it is the locale that makes the keypad's whole reason visible: the decimal key shows
 * `,` while the system decimal pad would offer whatever the device region says. It sits last in the
 * column so it is the field most likely to be covered — which is what makes the published keypad inset
 * testable rather than theoretical.
 */
@Composable
private fun Keypad() {
    var value by remember { mutableStateOf<Double?>(null) }
    Case(
        title = "useBuiltInKeypad, de-DE",
        expectation = "Keypad's background reaches the bottom edge; keys clear of the nav bar; " +
            "decimal key shows a comma; this field scrolls clear when focused.",
        value = value,
    ) {
        NumberInputField(
            value = value,
            onValueChange = { value = it },
            config = NumberInputConfig(
                locale = "de-DE",
                useBuiltInKeypad = true,
                placeholder = "Betrag",
            ),
            style = NumberInputStyle(textAlign = TextAlign.End, textSize = 17.sp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        )
    }
}

@Composable
private fun Case(
    title: String,
    expectation: String,
    value: Double?,
    field: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        BasicText(expectation, style = TextStyle(fontSize = 12.sp, color = Color(0xFF666666)))
        field()
        // The bound value, not the displayed text: it is the only way to see that grouping and the
        // locale separator are display-only and the state underneath stayed a plain Double.
        BasicText(
            "bound value = ${value ?: "null"}",
            style = TextStyle(fontSize = 12.sp, color = Color(0xFF337733)),
        )
    }
}

@Composable
private fun Heading(text: String) {
    BasicText(text, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun Note(text: String) {
    BasicText(text, style = TextStyle(fontSize = 12.sp, color = Color(0xFF666666)))
}
