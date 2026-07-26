package dev.viethung.numberinput

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Renders thousands grouping for display only. The text field stores the raw, ungrouped value, so
 * editing never reflows the buffer and the caret stays put; grouping separators are inserted purely
 * visually, with an [OffsetMapping] that keeps the cursor mapped correctly across them.
 *
 * Android path only. The iOS path uses a native `UITextField` whose buffer is already grouped by
 * [LocaleNumberFormatter.formatLive].
 */
internal class NumberGroupingVisualTransformation(
    private val groupingSeparator: String,
    private val decimalSeparator: String,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val (display, originalToTransformed, transformedToOriginal) =
            groupForDisplay(text.text, groupingSeparator, decimalSeparator)
        return TransformedText(
            AnnotatedString(display),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    originalToTransformed[offset.coerceIn(0, originalToTransformed.lastIndex)]

                override fun transformedToOriginal(offset: Int): Int =
                    transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
            },
        )
    }
}

/**
 * Inserts grouping separators into the integer part of [raw] and returns the display string together
 * with the two offset maps (raw→display and display→raw). The maps are built by construction — each
 * raw character records the display offset it lands at — so they are always in bounds and monotonic.
 */
internal fun groupForDisplay(
    raw: String,
    groupingSeparator: String,
    decimalSeparator: String,
): Triple<String, IntArray, IntArray> {
    val sb = StringBuilder()
    val o2t = IntArray(raw.length + 1)

    var idx = 0
    if (raw.startsWith("-")) {
        o2t[0] = sb.length
        sb.append('-')
        idx = 1
    }

    val decPos = if (decimalSeparator.isEmpty()) -1 else raw.indexOf(decimalSeparator, idx)
    val intEnd = if (decPos >= 0) decPos else raw.length
    val intLen = intEnd - idx

    var i = idx
    while (i < intEnd) {
        o2t[i] = sb.length
        sb.append(raw[i])
        val remaining = intLen - 1 - (i - idx)
        if (remaining > 0 && remaining % 3 == 0) sb.append(groupingSeparator)
        i++
    }
    while (i < raw.length) {
        o2t[i] = sb.length
        sb.append(raw[i])
        i++
    }
    o2t[raw.length] = sb.length

    val display = sb.toString()
    val t2o = IntArray(display.length + 1)
    var ti = 0
    var lastRaw = 0
    for (ri in 0..raw.length) {
        val tpos = o2t[ri]
        while (ti < tpos) {
            t2o[ti] = lastRaw
            ti++
        }
        t2o[tpos] = ri
        ti = tpos + 1
        lastRaw = ri
    }
    while (ti <= display.length) {
        t2o[ti] = raw.length
        ti++
    }

    return Triple(display, o2t, t2o)
}
