package app.kharcha.core

import kotlinx.serialization.Serializable

enum class Direction { DEBIT, CREDIT }

enum class Source { SMS, NOTIFICATION, MANUAL }

/**
 * Captured payments wait in PENDING until the user has looked at them; only REVIEWED ones count in
 * totals, charts and insights. IGNORED ones (not really a spend, a duplicate, not mine) never count.
 */
enum class ReviewStatus { PENDING, REVIEWED, IGNORED }

/**
 * How sure the app is about a payment's category. SURE: your own rule, a single-business merchant,
 * or an unambiguous message (ATM, card bill). LIKELY: matched by keywords. GUESS: worth a look.
 */
enum class Confidence { SURE, LIKELY, GUESS }

/** What a parser could read out of one message, before merchant cleanup and categorisation. */
data class ParsedTransaction(
    val amountPaise: Long,
    val direction: Direction,
    val timestampMillis: Long,
    val source: Source,
    val rawText: String,
    val sender: String,
    val counterparty: String? = null,
    val vpa: String? = null,
    val accountLast4: String? = null,
    val bank: String? = null,
    val reference: String? = null,
    val balancePaise: Long? = null,
    val isOwnTransfer: Boolean = false,
    val isCashWithdrawal: Boolean = false,
    val isWalletTopUp: Boolean = false,
)

/** One slice of a payment that was split across categories (an Amazon order: groceries + electronics). */
@Serializable
data class SplitPart(val amountPaise: Long, val category: Category, val note: String? = null)

@Serializable
data class Transaction(
    val id: Long = 0,
    val amountPaise: Long,
    val direction: Direction,
    val timestampMillis: Long,
    val merchant: String,
    val merchantKey: String,
    val category: Category,
    val source: Source,
    val rawText: String,
    val accountLast4: String? = null,
    val bank: String? = null,
    val vpa: String? = null,
    val reference: String? = null,
    val balancePaise: Long? = null,
    val note: String? = null,
    val status: ReviewStatus = ReviewStatus.REVIEWED,
    /** Another payment this one looks like a copy of; the user decides during review. */
    val possibleDuplicateOf: Long? = null,
    /** When set, the payment counts as these parts instead of as one amount in [category]. */
    val splits: List<SplitPart> = emptyList(),
    val confidence: Confidence = Confidence.SURE,
) {
    val isSpend: Boolean get() = direction == Direction.DEBIT && category.countsAsSpend

    /** Money back from a merchant, filed under a spending category: it reduces that category's spend. */
    val isRefund: Boolean get() = direction == Direction.CREDIT && category.kind == Kind.SPENDING

    val isSplit: Boolean get() = splits.isNotEmpty()

    /** The payment as it counts in totals: itself, or one entry per split part. */
    fun parts(): List<Transaction> =
        if (splits.isEmpty()) {
            listOf(this)
        } else {
            splits.map { copy(amountPaise = it.amountPaise, category = it.category, note = it.note ?: note, splits = emptyList()) }
        }

    val isCashWithdrawal: Boolean get() = direction == Direction.DEBIT && category.id == Category.CASH.id

    val isLoggedCash: Boolean get() = source == Source.MANUAL && isSpend

    val isWalletTopUp: Boolean get() = direction == Direction.DEBIT && category.id == Category.WALLET.id

    /**
     * A payment seen only as an app notification, with no bank SMS behind it: it was paid from a wallet
     * or UPI Lite balance, so it is spending of money that was topped up earlier.
     */
    val isWalletSpend: Boolean get() = source == Source.NOTIFICATION && isSpend
}
