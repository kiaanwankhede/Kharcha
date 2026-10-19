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

    /** How far apart two records can be and still look like the same payment to a person. */
    const val SUSPECT_WINDOW_MILLIS = 2 * 60 * 60 * 1000L

    /**
     * Not certain enough to merge, but worth asking: same amount and direction within two hours, either
     * from different sources (a late bank SMS after an app notification) or to the same payee.
     */
    fun isPossibleDuplicate(a: Transaction, b: Transaction): Boolean {
        if (a.id != 0L && a.id == b.id) return false
        if (a.amountPaise != b.amountPaise || a.direction != b.direction) return false
        if (a.reference != null && b.reference != null && a.reference != b.reference) return false
        if (abs(a.timestampMillis - b.timestampMillis) > SUSPECT_WINDOW_MILLIS) return false
        return a.source != b.source || a.merchantKey == b.merchantKey
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
            // Tags from either copy survive (say the notification brought "always tag Swiggy food").
            tags = (existing.tags + incoming.tags).distinct(),
        )
    }
}
