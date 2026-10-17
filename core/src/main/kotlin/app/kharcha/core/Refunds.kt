package app.kharcha.core

/**
 * Turns a refund credit into a reduction of the spend it reverses: it takes the category of the most
 * recent matching purchase from the same merchant (or the merchant's usual category), so it nets
 * against that category instead of counting as income.
 */
object Refunds {
    const val LOOKBACK_MILLIS = 45L * 24 * 60 * 60 * 1000

    /** [history] is the user's earlier transactions. Returns the refund with its new category, or unchanged. */
    fun categorise(refund: Transaction, history: List<Transaction>): Transaction {
        if (refund.direction != Direction.CREDIT || refund.category.id != Category.REFUNDS.id) return refund
        val purchase = history
            .filter {
                it.direction == Direction.DEBIT && it.category.countsAsSpend && it.merchantKey == refund.merchantKey &&
                    it.amountPaise >= refund.amountPaise &&
                    it.timestampMillis <= refund.timestampMillis && refund.timestampMillis - it.timestampMillis <= LOOKBACK_MILLIS
            }
            .maxByOrNull { it.timestampMillis }
        val category = purchase?.category
            ?: Merchants.brandOf(refund)?.category?.takeIf { it.countsAsSpend }
            ?: return refund
        return refund.copy(category = category)
    }
}
