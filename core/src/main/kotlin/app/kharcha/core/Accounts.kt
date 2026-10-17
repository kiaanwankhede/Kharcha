package app.kharcha.core

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** One bank account or card, as seen in its SMS. */
data class AccountSummary(
    val key: String,
    val bank: String?,
    val last4: String,
    val isCreditCard: Boolean,
    /** Balance for an account, available limit for a credit card; from the latest SMS that had one. */
    val latestBalancePaise: Long?,
    val balanceAtMillis: Long?,
    val spentThisMonthPaise: Long,
    val paymentsThisMonth: Int,
    val lastUsedMillis: Long,
) {
    val label: String get() = listOfNotNull(bank, "··$last4").joinToString(" ")
}

object Accounts {
    private val CREDIT_CARD = Regex("""credit card|\bcc\b""", RegexOption.IGNORE_CASE)

    fun summarize(transactions: List<Transaction>, month: YearMonth, zone: ZoneId = ZoneId.of("Asia/Kolkata")): List<AccountSummary> =
        transactions
            .filter { it.accountLast4 != null && it.source != Source.MANUAL }
            .groupBy { (it.bank.orEmpty() + it.accountLast4).lowercase() }
            .map { (key, items) ->
                val withBalance = items.filter { it.balancePaise != null }.maxByOrNull { it.timestampMillis }
                val inMonth = items.filter {
                    YearMonth.from(Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()) == month
                }
                AccountSummary(
                    key = key,
                    bank = items.firstNotNullOfOrNull { it.bank },
                    last4 = items.first().accountLast4!!,
                    isCreditCard = items.count { CREDIT_CARD.containsMatchIn(it.rawText) } * 2 >= items.size,
                    latestBalancePaise = withBalance?.balancePaise,
                    balanceAtMillis = withBalance?.timestampMillis,
                    spentThisMonthPaise = inMonth.filter { it.isSpend }.sumOf { it.amountPaise },
                    paymentsThisMonth = inMonth.size,
                    lastUsedMillis = items.maxOf { it.timestampMillis },
                )
            }
            .sortedByDescending { it.lastUsedMillis }
}
