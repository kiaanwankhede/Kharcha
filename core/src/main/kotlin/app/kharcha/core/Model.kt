package app.kharcha.core

import kotlinx.serialization.Serializable

enum class Direction { DEBIT, CREDIT }

enum class Source { SMS, NOTIFICATION, MANUAL }

/** Spending categories. [countsAsSpend] is false for money that only moves between your own accounts. */
enum class Category(val label: String, val countsAsSpend: Boolean = true) {
    FOOD("Food"),
    GROCERIES("Groceries"),
    SHOPPING("Shopping"),
    TRAVEL("Travel"),
    FUEL("Fuel"),
    BILLS("Bills & utilities"),
    RECHARGE("Mobile & DTH"),
    ENTERTAINMENT("Entertainment"),
    SUBSCRIPTIONS("Subscriptions"),
    HEALTH("Health"),
    EDUCATION("Education"),
    RENT("Rent"),
    INVESTMENT("Investments"),
    EMI("Loans & EMI"),
    INSURANCE("Insurance"),
    PEOPLE("Sent to people"),
    CASH("Cash withdrawal"),
    TRANSFER("Own transfers", countsAsSpend = false),
    INCOME("Money in", countsAsSpend = false),
    OTHER("Other"),
}

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
) {
    val isSpend: Boolean get() = direction == Direction.DEBIT && category.countsAsSpend
}
