package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals

/** Reproduces the on-device vi-VN display defect: typing 9500 rendered "9.500..." */
class IosLiveFormatProbeTest {

    private val f = newLocaleNumberFormatter()

    @Test
    fun live_grouping_vi_VN() {
        assertEquals("9.500", f.formatLive("9500", "vi-VN"))
        assertEquals("200.000", f.formatLive("200000", "vi-VN"))
    }

    @Test
    fun separators_are_the_characters_the_display_uses() {
        val gs = f.groupingSeparator("vi-VN")
        val ds = f.decimalSeparator("vi-VN")
        println("vi-VN grouping=${gs.map { it.code }} decimal=${ds.map { it.code }}")
        println("formatLive(9500)=${f.formatLive("9500", "vi-VN").map { it.code }}")
        assertEquals(listOf(46), gs.map { it.code })  // U+002E FULL STOP
    }

    /** Mirrors NumberInputField.ios.kt: strip grouping on input, re-format for display. */
    @Test
    fun editing_loop_reproduces_the_field_text() {
        val state = NumberInputState(
            formatter = f,
            config = NumberInputConfig(significantDigits = 0, locale = "vi-VN"),
        )
        val gs = f.groupingSeparator("vi-VN")
        state.onFocusChanged(true)

        var fieldText = ""
        for (d in "9500") {
            fieldText += d                                   // user keystroke
            val ungrouped = if (gs.isEmpty()) fieldText else fieldText.replace(gs, "")
            state.onTextChange(ungrouped)
            fieldText = f.formatLive(state.rawText, "vi-VN") // update { } writes this back
            println("key=$d rawText='${state.rawText}' fieldText='$fieldText'")
        }
        assertEquals("9.500", fieldText)
    }
}
