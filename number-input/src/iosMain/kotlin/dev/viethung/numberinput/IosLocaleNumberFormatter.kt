package dev.viethung.numberinput

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfEven
import platform.Foundation.numberWithDouble
import platform.Foundation.numberWithLongLong

/**
 * iOS implementation backed by `NSNumberFormatter`, mirroring `IosLocaleNumberFormatter.swift`.
 *
 * `NSNumberFormatter` is expensive to construct, so one is cached per locale. Each method sets the
 * full set of properties it depends on, so a shared instance can't leak one operation's
 * configuration into another. Not thread-safe; intended for main-thread use.
 */
internal class IosLocaleNumberFormatter : LocaleNumberFormatter {

    private val cache = mutableMapOf<String, NSNumberFormatter>()

    private fun formatter(locale: String): NSNumberFormatter =
        cache.getOrPut(locale) {
            NSNumberFormatter().apply {
                setLocale(NSLocale(localeIdentifier = locale))
                setNumberStyle(NSNumberFormatterDecimalStyle)
            }
        }

    override fun format(value: Double, significantDigits: Int, locale: String): String {
        val nf = formatter(locale)
        nf.setUsesGroupingSeparator(true)
        nf.setMinimumFractionDigits(significantDigits.toULong())
        nf.setMaximumFractionDigits(significantDigits.toULong())
        nf.setRoundingMode(NSNumberFormatterRoundHalfEven)
        return nf.stringFromNumber(NSNumber.numberWithDouble(value)) ?: ""
    }

    override fun parse(rawText: String, locale: String): Double? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        val nf = formatter(locale)
        nf.setUsesGroupingSeparator(true)
        nf.setMinimumFractionDigits(0uL)
        nf.setMaximumFractionDigits(20uL)

        // Pass 1: NSNumberFormatter handles grouping separators natively.
        nf.numberFromString(trimmed)?.let { return it.doubleValue }

        // Pass 2: strip grouping, normalise the decimal separator, parse as Double.
        val groupSep = nf.groupingSeparator ?: ","
        val decSep = nf.decimalSeparator ?: "."
        return trimmed
            .replace(groupSep, "")
            .replace(decSep, ".")
            .toDoubleOrNull()
    }

    override fun formatLive(rawText: String, locale: String): String {
        if (rawText.isEmpty()) return ""

        val nf = formatter(locale)
        nf.setUsesGroupingSeparator(true)
        // The grouping closure formats integer digits only — suppress any fraction digits a prior
        // format() call may have left on the shared instance.
        nf.setMinimumFractionDigits(0uL)
        nf.setMaximumFractionDigits(0uL)

        val groupSep = nf.groupingSeparator ?: ","
        val decSep = nf.decimalSeparator ?: "."

        return liveFormat(rawText, groupSep, decSep) { digits ->
            val n = digits.toLongOrNull() ?: return@liveFormat digits
            nf.stringFromNumber(NSNumber.numberWithLongLong(n)) ?: digits
        }
    }

    override fun decimalSeparator(locale: String): String =
        formatter(locale).decimalSeparator ?: "."

    override fun groupingSeparator(locale: String): String =
        formatter(locale).groupingSeparator ?: ","
}

actual fun newLocaleNumberFormatter(): LocaleNumberFormatter = IosLocaleNumberFormatter()
