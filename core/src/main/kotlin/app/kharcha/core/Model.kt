package app.kharcha.core

import kotlinx.serialization.Serializable

enum class Direction { DEBIT, CREDIT }

enum class Source { SMS, NOTIFICATION, MANUAL }

/**
 * Captured payments wait in PENDING until the user has looked at them; only REVIEWED ones count in
 * totals, charts and insights. IGNORED ones (not really a spend, a duplicate, not mine) never count.
 */
enum class ReviewStatus { PENDING, REVIEWED, IGNORED }

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
) {
    val isSpend: Boolean get() = direction == Direction.DEBIT && category.countsAsSpend

    val isCashWithdrawal: Boolean get() = direction == Direction.DEBIT && category.id == Category.CASH.id

    val isLoggedCash: Boolean get() = source == Source.MANUAL && isSpend

    val isWalletTopUp: Boolean get() = direction == Direction.DEBIT && category.id == Category.WALLET.id

    /**
     * A payment seen only as an app notification, with no bank SMS behind it: it was paid from a wallet
     * or UPI Lite balance, so it is spending of money that was topped up earlier.
     */
    val isWalletSpend: Boolean get() = source == Source.NOTIFICATION && isSpend
}
