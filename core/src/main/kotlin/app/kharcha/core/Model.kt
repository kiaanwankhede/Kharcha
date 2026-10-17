package app.kharcha.core

import kotlinx.serialization.Serializable

enum class Direction { DEBIT, CREDIT }

enum class Source { SMS, NOTIFICATION, MANUAL }

/**
 * A spending category. The built-in ones are constants below; users can add their own, which get an
 * id starting with "custom_". [countsAsSpend] is false for money that only moves between your own
 * accounts or comes in.
 */
@Serializable
data class Category(
    val id: String,
    val label: String,
    val emoji: String,
    val countsAsSpend: Boolean = true,
    val builtIn: Boolean = true,
) {
    companion object {
        val FOOD = Category("FOOD", "Food", "🍔")
        val GROCERIES = Category("GROCERIES", "Groceries", "🛒")
        val SHOPPING = Category("SHOPPING", "Shopping", "🛍️")
        val TRAVEL = Category("TRAVEL", "Travel", "🚕")
        val FUEL = Category("FUEL", "Fuel", "⛽")
        val BILLS = Category("BILLS", "Bills & utilities", "💡")
        val RECHARGE = Category("RECHARGE", "Mobile & DTH", "📱")
        val ENTERTAINMENT = Category("ENTERTAINMENT", "Entertainment", "🎬")
        val SUBSCRIPTIONS = Category("SUBSCRIPTIONS", "Subscriptions", "🔁")
        val HEALTH = Category("HEALTH", "Health", "💊")
        val EDUCATION = Category("EDUCATION", "Education", "🎓")
        val RENT = Category("RENT", "Rent", "🏠")
        val INVESTMENT = Category("INVESTMENT", "Investments", "📈")
        val EMI = Category("EMI", "Loans & EMI", "🏦")
        val INSURANCE = Category("INSURANCE", "Insurance", "🛡️")
        val PEOPLE = Category("PEOPLE", "Sent to people", "🧑‍🤝‍🧑")
        val CASH = Category("CASH", "Cash withdrawal", "💵")
        val TRANSFER = Category("TRANSFER", "Own transfers", "🔄", countsAsSpend = false)
        val INCOME = Category("INCOME", "Money in", "💰", countsAsSpend = false)
        val OTHER = Category("OTHER", "Other", "🏷️")

        val BUILT_IN: List<Category> = listOf(
            FOOD, GROCERIES, SHOPPING, TRAVEL, FUEL, BILLS, RECHARGE, ENTERTAINMENT, SUBSCRIPTIONS, HEALTH,
            EDUCATION, RENT, INVESTMENT, EMI, INSURANCE, PEOPLE, CASH, TRANSFER, INCOME, OTHER,
        )

        fun custom(label: String, emoji: String, countsAsSpend: Boolean = true): Category = Category(
            id = "custom_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12),
            label = label.trim(),
            emoji = emoji.trim().ifEmpty { "🏷️" },
            countsAsSpend = countsAsSpend,
            builtIn = false,
        )
    }
}

/** All categories the user can pick from: the built-in ones plus their own. */
class CategoryCatalog(custom: List<Category> = emptyList()) {
    val all: List<Category> = Category.BUILT_IN + custom
    private val byId = all.associateBy { it.id }

    /** Unknown ids (for example a deleted custom category) fall back to Other. */
    fun byId(id: String): Category = byId[id] ?: Category.OTHER
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
