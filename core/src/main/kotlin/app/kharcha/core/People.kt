package app.kharcha.core

import java.net.URLEncoder

/**
 * Where you stand with one person. [owedToMePaise] is positive when they owe you and negative when
 * you owe them.
 */
data class PersonBalance(
    val key: String,
    val name: String,
    /** Their UPI ID, from the most recent payment that had one; needed to pay them back from the app. */
    val vpa: String?,
    val owedToMePaise: Long,
    /** Lending, borrowing and settling payments with this person, newest first. */
    val history: List<Transaction>,
) {
    val isSettled: Boolean get() = owedToMePaise == 0L
}

/** Money lent and borrowed, worked out from payments filed as Lent, Borrowed, Paid back and Paying back. */
object People {
    private val LEDGER_IDS = setOf(Category.LENT.id, Category.BORROWED.id, Category.PAID_BACK.id, Category.REPAYING.id)

    /** Signed effect of one payment on what the other person owes you. */
    private fun effect(t: Transaction): Long = when (t.category.id) {
        Category.LENT.id, Category.REPAYING.id -> t.amountPaise
        Category.BORROWED.id, Category.PAID_BACK.id -> -t.amountPaise
        else -> 0L
    }

    /** Everyone you have lent to or borrowed from, unsettled first and biggest first. Reviewed payments only. */
    fun ledger(transactions: List<Transaction>): List<PersonBalance> =
        transactions.asSequence()
            .filter { it.status == ReviewStatus.REVIEWED }
            .flatMap { it.parts() }
            .filter { it.category.id in LEDGER_IDS }
            .groupBy { it.merchantKey }
            .map { (key, items) ->
                val newestFirst = items.sortedByDescending { it.timestampMillis }
                PersonBalance(
                    key = key,
                    name = newestFirst.first().merchant,
                    vpa = newestFirst.firstNotNullOfOrNull { it.vpa },
                    owedToMePaise = items.sumOf(::effect),
                    history = newestFirst,
                )
            }
            .sortedWith(compareBy<PersonBalance> { it.isSettled }.thenByDescending { kotlin.math.abs(it.owedToMePaise) })

    /**
     * A better guess for money to or from someone you have an open balance with: money in from someone
     * who owes you is them paying back, money out to someone you owe is you paying back.
     */
    fun suggest(t: Transaction, ledger: List<PersonBalance>): Category? {
        val balance = ledger.firstOrNull { it.key == t.merchantKey }?.owedToMePaise ?: return null
        return when {
            t.direction == Direction.CREDIT && balance > 0 && t.category.id in setOf(Category.RECEIVED.id, Category.INCOME.id, Category.OTHER.id) -> Category.PAID_BACK
            t.direction == Direction.DEBIT && balance < 0 && t.category.id in setOf(Category.PEOPLE.id, Category.OTHER.id) -> Category.REPAYING
            else -> null
        }
    }

    /** A UPI payment link (opens any UPI app) for paying [amountPaise] to [vpa]. */
    fun upiLink(vpa: String, name: String, amountPaise: Long, note: String): String {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val amount = "%d.%02d".format(amountPaise / 100, amountPaise % 100)
        return "upi://pay?pa=${enc(vpa)}&pn=${enc(name)}&am=$amount&cu=INR&tn=${enc(note)}"
    }

    /** A friendly message asking for money back, with your UPI ID and a payment link. */
    fun reminder(person: PersonBalance, myVpa: String, myName: String): String {
        val amount = Money.format(person.owedToMePaise, showPaise = person.owedToMePaise % 100 != 0L)
        val link = upiLink(myVpa, myName, person.owedToMePaise, "Settling up")
        return "Hi ${person.name.substringBefore(' ')}, just a reminder about the $amount from earlier. " +
            "You can pay to my UPI ID $myVpa or use this link: $link"
    }
}
