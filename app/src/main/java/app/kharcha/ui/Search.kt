package app.kharcha.ui

import app.kharcha.core.Money
import app.kharcha.core.Transaction

/**
 * Free-text match over everything a payment carries: payee, category, note, amount ("450", "1,200"),
 * UPI ID, reference, bank, account and the original message.
 */
fun Transaction.matches(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val amountQuery = q.removePrefix("₹").replace(",", "")
    if (amountQuery.isNotEmpty() && amountQuery.all { it.isDigit() || it == '.' }) {
        val amount = Money.format(amountPaise, showPaise = true).removePrefix("₹").replace(",", "")
        if (amount.startsWith(amountQuery) || amount.removeSuffix(".00") == amountQuery) return true
    }
    return listOfNotNull(merchant, category.label, note, vpa, reference, bank, accountLast4, rawText)
        .any { it.lowercase().contains(q) }
}
