package app.kharcha.core

import kotlin.math.abs

/**
 * The same payment often arrives twice: a bank SMS and a Google Pay notification, or the same SMS
 * read again during a re-scan. This decides when two records are one payment, and how to merge them.
 */
object Deduplicator {

    /** How far apart a bank SMS and an app notification for the same payment can arrive. */
    const val MATCH_WINDOW_MILLIS = 10 * 60 * 1000L

    fun isSamePayment(a: Transaction, b: Transaction): Boolean {
        if (a.reference != null && b.reference != null) return a.reference == b.reference
        if (a.amountPaise != b.amountPaise || a.direction != b.direction) return false
        val gap = abs(a.timestampMillis - b.timestampMillis)
        if (a.source != b.source) return gap <= MATCH_WINDOW_MILLIS
        return a.rawText == b.rawText && gap <= MATCH_WINDOW_MILLIS
    }

    /**
     * Keeps the bank's facts (account, reference, balance) and the nicer name, whichever record has it.
     * [existing]'s id, note and any category the user set are preserved.
     */
    fun merge(existing: Transaction, incoming: Transaction): Transaction {
        val sms = listOf(existing, incoming).firstOrNull { it.source == Source.SMS } ?: existing
        val notification = listOf(existing, incoming).firstOrNull { it.source == Source.NOTIFICATION }
        val betterName = when {
            notification != null && sms.category == Category.OTHER -> notification
            notification != null && (sms.merchant.startsWith("UPI ") || sms.merchant == "Unknown") -> notification
            else -> sms
        }
        return existing.copy(
            timestampMillis = minOf(existing.timestampMillis, incoming.timestampMillis),
            merchant = betterName.merchant,
            merchantKey = betterName.merchantKey,
            category = if (existing.category != Category.OTHER) existing.category else betterName.category,
            source = sms.source,
            rawText = sms.rawText,
            accountLast4 = existing.accountLast4 ?: incoming.accountLast4,
            bank = existing.bank ?: incoming.bank,
            vpa = existing.vpa ?: incoming.vpa,
            reference = existing.reference ?: incoming.reference,
            balancePaise = existing.balancePaise ?: incoming.balancePaise,
        )
    }
}
