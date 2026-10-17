package app.kharcha.core

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    /** "1,234.50" -> 123450. Returns null for anything that is not a positive amount. */
    fun parsePaise(text: String): Long? {
        val cleaned = text.replace(",", "").trim().trimEnd('.')
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0) return null
        return value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
    }

    /** 4238050 -> "₹42,380.50"; whole rupees drop the decimals: "₹42,380". Uses Indian digit grouping. */
    fun format(paise: Long, showPaise: Boolean = false): String {
        val negative = paise < 0
        val abs = BigDecimal.valueOf(kotlin.math.abs(paise)).movePointLeft(2)
        val rupees = abs.toBigInteger().toString()
        val fraction = abs.remainder(BigDecimal.ONE).movePointRight(2).toInt()
        val grouped = groupIndian(rupees)
        val decimals = if (showPaise || fraction != 0) ".%02d".format(fraction) else ""
        return (if (negative) "−₹" else "₹") + grouped + decimals
    }

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val restGrouped = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$restGrouped,$last3"
    }
}
