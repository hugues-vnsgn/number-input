package dev.viethung.numberinput

import java.text.DecimalFormat
import java.util.Locale

internal class AndroidLocaleNumberFormatter : LocaleNumberFormatter {

    private val cache = mutableMapOf<String, DecimalFormat>()

    private fun formatter(locale: String): DecimalFormat =
        cache.getOrPut(locale) {
            DecimalFormat.getInstance(Locale.forLanguageTag(locale)) as DecimalFormat
        }

    override fun format(value: Double, significantDigits: Int, locale: String): String {
        val df = formatter(locale)
        df.isGroupingUsed = true
        df.minimumFractionDigits = significantDigits
        df.maximumFractionDigits = significantDigits
        df.roundingMode = java.math.RoundingMode.HALF_EVEN
        return df.format(value)
    }

    override fun parse(rawText: String, locale: String): Double? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null
        val df = formatter(locale)
        df.isGroupingUsed = true
        df.minimumFractionDigits = 0
        df.maximumFractionDigits = 20
        return try {
            df.parse(trimmed)?.toDouble()
        } catch (_: java.text.ParseException) {
            val symbols = df.decimalFormatSymbols
            trimmed
                .replace(symbols.groupingSeparator.toString(), "")
                .replace(symbols.decimalSeparator.toString(), ".")
                .toDoubleOrNull()
        }
    }

    override fun formatLive(rawText: String, locale: String): String {
        if (rawText.isEmpty()) return ""
        val df = formatter(locale)
        df.isGroupingUsed = true
        val symbols = df.decimalFormatSymbols
        val groupSep = symbols.groupingSeparator.toString()
        val decSep = symbols.decimalSeparator.toString()
        return liveFormat(rawText, groupSep, decSep) { digits ->
            val n = digits.toLongOrNull() ?: return@liveFormat digits
            DecimalFormat("#,##0", symbols).format(n)
        }
    }

    override fun decimalSeparator(locale: String): String =
        formatter(locale).decimalFormatSymbols.decimalSeparator.toString()

    override fun groupingSeparator(locale: String): String =
        formatter(locale).decimalFormatSymbols.groupingSeparator.toString()
}

actual fun newLocaleNumberFormatter(): LocaleNumberFormatter = AndroidLocaleNumberFormatter()
