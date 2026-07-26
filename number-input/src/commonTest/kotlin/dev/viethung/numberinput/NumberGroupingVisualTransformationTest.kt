package dev.viethung.numberinput

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NumberGroupingVisualTransformationTest {

    // en-US: "," grouping, "." decimal. vi-VN/de-DE: "." grouping, "," decimal.
    private val enUs = "," to "."
    private val viVn = "." to ","

    private fun display(raw: String, seps: Pair<String, String>): String =
        groupForDisplay(raw, seps.first, seps.second).first

    @Test
    fun groups_integer_en_US() {
        assertEquals("1", display("1", enUs))
        assertEquals("12", display("12", enUs))
        assertEquals("123", display("123", enUs))
        assertEquals("1,234", display("1234", enUs))
        assertEquals("12,345", display("12345", enUs))
        assertEquals("1,234,567", display("1234567", enUs))
    }

    @Test
    fun groups_only_integer_part_keeps_fraction_en_US() {
        assertEquals("1,234.5", display("1234.5", enUs))
        assertEquals("12,345.67", display("12345.67", enUs))
        assertEquals("1,000.", display("1000.", enUs)) // trailing separator preserved mid-edit
    }

    @Test
    fun groups_with_comma_decimal_vi_VN() {
        assertEquals("1.234", display("1234", viVn))
        assertEquals("1.234,5", display("1234,5", viVn))
        assertEquals("20.000,33", display("20000,33", viVn))
    }

    @Test
    fun preserves_sign() {
        assertEquals("-1,234", display("-1234", enUs))
        assertEquals("-1.234,5", display("-1234,5", viVn))
        assertEquals("-", display("-", enUs))
    }

    @Test
    fun empty_input() {
        val (d, o2t, t2o) = groupForDisplay("", ",", ".")
        assertEquals("", d)
        assertEquals(1, o2t.size)
        assertEquals(1, t2o.size)
        assertEquals(0, o2t[0])
        assertEquals(0, t2o[0])
    }

    @Test
    fun originalToTransformed_lands_after_grouping() {
        // raw "1234.5" -> display "1,234.5"; caret after the 4 (raw 4) -> display 5 (after "1,234")
        val (_, o2t, _) = groupForDisplay("1234.5", ",", ".")
        assertEquals(0, o2t[0])
        assertEquals(5, o2t[4])
        assertEquals(7, o2t[6])
    }

    @Test
    fun roundtrip_and_bounds_across_many_inputs() {
        val cases = listOf(
            "", "-", "1", "12", "123", "1234", "12345", "1234567",
            "1234.5", "12345.67", "-1234", "-1234567,89", "1000.", "0,5",
        )
        for (decGroup in listOf(enUs, viVn)) {
            for (raw in cases) {
                val (display, o2t, t2o) = groupForDisplay(raw, decGroup.first, decGroup.second)
                // bounds: every raw offset maps inside the display, and back
                for (o in 0..raw.length) {
                    assertTrue(o2t[o] in 0..display.length, "o2t out of bounds for '$raw'@$o")
                }
                for (t in 0..display.length) {
                    assertTrue(t2o[t] in 0..raw.length, "t2o out of bounds for '$raw'@$t")
                }
                // round-trip: a raw caret survives the trip to display and back unchanged
                for (o in 0..raw.length) {
                    assertEquals(o, t2o[o2t[o]], "roundtrip failed for '$raw'@$o")
                }
                // monotonic non-decreasing
                for (o in 1..raw.length) {
                    assertTrue(o2t[o] >= o2t[o - 1], "o2t not monotonic for '$raw'")
                }
            }
        }
    }
}
