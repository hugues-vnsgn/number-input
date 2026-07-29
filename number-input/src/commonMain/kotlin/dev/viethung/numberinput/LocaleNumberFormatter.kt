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
 * The characters a numeric keyboard may offer as its decimal key.
 *
 * Latin-only by design, covering the "." / "," disagreement that Latin-script regions produce. A
 * device region whose decimal key is neither — Arabic-Indic "٫" (U+066B), for instance — is *not*
 * translated: a field configured for such a locale still works, because the emitted key already
 * equals its own decimal separator and needs no translation, but a field configured en-US on such a
 * device drops the fraction. That is the same shape as the defect this set fixes, unaddressed for
 * non-Latin keys, and it predates this translation rather than being introduced by it.
 */
private val DECIMAL_KEY_CANDIDATES = setOf(".", ",")

/**
 * Diff [newText] against the [previousText] it replaced to isolate the characters just inserted; if
 * that insertion is a lone decimal-key character, return [newText] with it swapped for
 * [decimalSeparator]. Any other edit — a digit, a deletion, a multi-character paste — is returned
 * untouched.
 *
 * The keyboard's decimal key follows the *device* region, not the field's locale, and the system
 * decimal pad offers no per-field override. So a field may be handed either of
 * [DECIMAL_KEY_CANDIDATES] as the decimal keystroke however it is configured, and has to translate
 * whichever arrived before it can be parsed. Both callers hold a buffer and the buffer it replaced,
 * which makes the diff unambiguous:
 *
 * - [NumberInputState.substituteTypedDecimal] runs it over the ungrouped edit buffer.
 * - iOS's [ungroupTypedText] runs it over the *grouped* native buffer, where an inserted key is
 *   otherwise indistinguishable from the grouping separators the field inserted for display.
 *
 * Reading a character that is the field's own *grouping* separator as decimal intent is safe: no
 * locale uses one character for both roles, and the user never types grouping separators — the
 * field inserts those itself, so they land in [previousText] rather than in the insertion.
 */
internal fun substituteInsertedDecimalKey(
    newText: String,
    previousText: String,
    decimalSeparator: String,
): String {
    val prefix = newText.commonPrefixWith(previousText).length
    val maxSuffix = (minOf(newText.length, previousText.length) - prefix).coerceAtLeast(0)
    val suffix = newText.commonSuffixWith(previousText).length.coerceAtMost(maxSuffix)
    val insertedEnd = newText.length - suffix
    if (insertedEnd <= prefix) return newText
    val inserted = newText.substring(prefix, insertedEnd)
    if (inserted !in DECIMAL_KEY_CANDIDATES) return newText
    if (inserted == decimalSeparator) return newText
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
