package dev.viethung.numberinput

/**
 * Locale-aware number formatting seam.
 *
 * Public and injectable on purpose: consumers with an existing locale strategy can supply their
 * own implementation rather than inheriting this library's platform formatters. That matters when
 * a codebase already renders numbers its own way — mixing two strategies in one app produces
 * visibly inconsistent separators.
 *
 * [formatLive] is used by the iOS path, which keeps a *grouped* edit buffer in `UITextField`.
 * The Android path keeps an *ungrouped* buffer and groups at render time via
 * [NumberGroupingVisualTransformation], so it never calls [formatLive].
 */
interface LocaleNumberFormatter {
    fun format(value: Double, significantDigits: Int, locale: String): String
    fun parse(rawText: String, locale: String): Double?
    fun formatLive(rawText: String, locale: String): String
    fun decimalSeparator(locale: String): String
    fun groupingSeparator(locale: String): String
}

/** The platform formatter: `DecimalFormat` on Android, `NSNumberFormatter` on iOS. */
expect fun newLocaleNumberFormatter(): LocaleNumberFormatter

/**
 * Diff [newText] against the [previousText] it replaced to isolate the characters just inserted; if
 * that insertion is a lone ".", return [newText] with the "." swapped for [decimalSeparator]. Any
 * other edit — a digit, a deletion, a multi-character paste — is returned untouched.
 *
 * The decimal keypad emits "." on every locale, so on a locale whose decimal separator is not "."
 * the typed character has to be translated before it can be parsed. Both callers hold a buffer and
 * the buffer it replaced, which makes the diff unambiguous:
 *
 * - [NumberInputState.substituteTypedDecimal] runs it over the ungrouped edit buffer.
 * - iOS's [ungroupTypedText] runs it over the *grouped* native buffer, where a typed "." would
 *   otherwise be indistinguishable from the grouping separators the field inserted for display.
 */
internal fun substituteInsertedDot(
    newText: String,
    previousText: String,
    decimalSeparator: String,
): String {
    val prefix = newText.commonPrefixWith(previousText).length
    val maxSuffix = (minOf(newText.length, previousText.length) - prefix).coerceAtLeast(0)
    val suffix = newText.commonSuffixWith(previousText).length.coerceAtMost(maxSuffix)
    val insertedEnd = newText.length - suffix
    if (insertedEnd <= prefix) return newText
    if (newText.substring(prefix, insertedEnd) != ".") return newText
    return newText.substring(0, prefix) + decimalSeparator + newText.substring(insertedEnd)
}

/**
 * Shared live-grouping algorithm. Each platform supplies its locale's separators and an
 * integer-grouping function; the digit/sign/decimal splitting is identical everywhere.
 */
internal inline fun liveFormat(
    rawText: String,
    groupingSeparator: String,
    decimalSeparator: String,
    groupIntegerDigits: (String) -> String,
): String {
    if (rawText.isEmpty()) return ""
    val negative = rawText.startsWith("-")
    val unsigned = if (negative) rawText.drop(1) else rawText
    val sign = if (negative) "-" else ""

    val stripped = if (groupingSeparator.isEmpty()) unsigned else unsigned.replace(groupingSeparator, "")
    val decIdx = if (decimalSeparator.isEmpty()) -1 else stripped.indexOf(decimalSeparator)

    val (intPart, decPart) = if (decIdx >= 0) {
        stripped.substring(0, decIdx) to stripped.substring(decIdx)
    } else {
        stripped to ""
    }

    val intDigits = intPart.filter { it in '0'..'9' }
    val groupedInt = if (intDigits.isEmpty()) "" else groupIntegerDigits(intDigits)
    return sign + groupedInt + decPart
}
