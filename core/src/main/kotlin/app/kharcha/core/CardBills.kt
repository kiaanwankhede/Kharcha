package app.kharcha.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** A credit card statement or due reminder read from an SMS. */
data class CardBill(
    val id: Long = 0,
    val bank: String?,
    val last4: String?,
    val totalDuePaise: Long,
    val minDuePaise: Long?,
    val dueDate: LocalDate,
    val receivedAtMillis: Long,
    val rawText: String,
    val paidManually: Boolean = false,
) {
    val label: String get() = listOfNotNull(bank, last4?.let { "··$it" }).joinToString(" ").ifEmpty { "Credit card" }
}

enum class BillState { PAID, PARTLY_PAID, DUE, OVERDUE }

object CardBills {
    private const val AMOUNT = """(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val CARD = Regex("""credit\s*card|\bcard\b""", RegexOption.IGNORE_CASE)
    private val DUE_WORD = Regex("""\bdue\b|payable""", RegexOption.IGNORE_CASE)
    private val TOTAL = Regex("""(?:total\s*(?:amount|amt)?\s*(?:due|outstanding)|amount\s*due|total\s*due)\s*(?:is|of)?\s*[:\-]?\s*$AMOUNT""", RegexOption.IGNORE_CASE)
    private val MINIMUM = Regex("""min(?:imum)?\.?\s*(?:amount|amt)?\s*due\s*(?:is|of)?\s*[:\-]?\s*$AMOUNT""", RegexOption.IGNORE_CASE)
    private val ANY_AMOUNT = Regex(AMOUNT, RegexOption.IGNORE_CASE)
    private val DUE_DATE = Regex(
        """(?:due\s*date|payable\s*by|due\s*on|due\s*by|pay\s*by|before)\s*(?:is|of)?\s*[:\-]?\s*([0-9]{1,2}[\-/ .]?(?:[0-9]{1,2}|[a-z]{3,9})[\-/ .,]*[0-9]{2,4}|[0-9]{4}-[0-9]{2}-[0-9]{2})""",
        RegexOption.IGNORE_CASE,
    )
    private val CARD_LAST4 = Regex("""(?:card|ending|xx|\*\*)\D{0,12}?(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)

    private val DATE_FORMATS: List<DateTimeFormatter> = listOf(
        "d-M-uuuu", "d/M/uuuu", "d.M.uuuu", "d-M-uu", "d/M/uu", "d-MMM-uuuu", "d-MMM-uu", "d MMM uuuu", "d MMM uu",
        "dMMMuu", "dMMMuuuu", "d MMMM uuuu", "d-MMMM-uuuu", "uuuu-M-d",
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }

    fun parseDate(text: String): LocalDate? {
        val cleaned = text.trim().trimEnd('.', ',').replace(Regex("""\s+"""), " ").replace(", ", " ")
        return DATE_FORMATS.firstNotNullOfOrNull { runCatching { LocalDate.parse(cleaned, it) }.getOrNull() }
    }

    /** Reads a statement or due reminder; null for anything else (including ordinary card spends). */
    fun parse(sender: String, body: String, receivedAtMillis: Long): CardBill? {
        if (!SmsParser.isLikelyBankSender(sender)) return null
        val text = body.replace(Regex("""\s+"""), " ").trim()
        if (!CARD.containsMatchIn(text) || !DUE_WORD.containsMatchIn(text)) return null
        val dueDate = DUE_DATE.find(text)?.groupValues?.get(1)?.let(::parseDate) ?: return null
        val minimum = MINIMUM.find(text)?.groupValues?.get(1)?.let(Money::parsePaise)
        val total = TOTAL.find(text)?.groupValues?.get(1)?.let(Money::parsePaise)
            ?: ANY_AMOUNT.findAll(text).mapNotNull { Money.parsePaise(it.groupValues[1]) }.filter { it != minimum }.maxOrNull()
            ?: return null
        return CardBill(
            bank = SmsParser.bankOf(sender),
            last4 = CARD_LAST4.find(text)?.groupValues?.get(1),
            totalDuePaise = total,
            minDuePaise = minimum,
            dueDate = dueDate,
            receivedAtMillis = receivedAtMillis,
            rawText = body,
        )
    }

    /**
     * Paid when a card payment for this card (or for exactly the total) shows up after the statement.
     * Pass the transactions the user has not ignored.
     */
    fun state(bill: CardBill, transactions: List<Transaction>, today: LocalDate): BillState {
        if (bill.paidManually) return BillState.PAID
        val payments = transactions.filter {
            (it.category.id == Category.CARD_BILL.id || it.category.id == Category.TRANSFER.id) &&
                it.timestampMillis >= bill.receivedAtMillis &&
                (it.accountLast4 != null && it.accountLast4 == bill.last4 || it.amountPaise == bill.totalDuePaise)
        }
        val paid = payments.sumOf { it.amountPaise }
        return when {
            paid >= bill.totalDuePaise -> BillState.PAID
            paid > 0 -> BillState.PARTLY_PAID
            today.isAfter(bill.dueDate) -> BillState.OVERDUE
            else -> BillState.DUE
        }
    }
}

enum class UpcomingKind { CARD_BILL, RECURRING }

data class UpcomingItem(val date: LocalDate, val label: String, val emoji: String, val amountPaise: Long, val kind: UpcomingKind, val billId: Long? = null)

object Upcoming {
    /** Unpaid card bills and expected recurring payments in the next [days] days (overdue bills included). */
    fun next(
        bills: List<CardBill>,
        recurring: List<RecurringPayment>,
        transactions: List<Transaction>,
        today: LocalDate,
        days: Long = 30,
    ): List<UpcomingItem> {
        val end = today.plusDays(days)
        val billItems = bills
            .groupBy { it.last4 ?: it.bank ?: "" }
            .mapNotNull { (_, forCard) -> forCard.maxByOrNull { it.receivedAtMillis } }
            .filter { CardBills.state(it, transactions, today) != BillState.PAID && !it.dueDate.isAfter(end) }
            .map { UpcomingItem(it.dueDate, "${it.label} card bill", "💳", it.totalDuePaise, UpcomingKind.CARD_BILL, it.id) }
        val recurringItems = recurring
            .filter { !it.nextExpected.isBefore(today) && !it.nextExpected.isAfter(end) }
            .map { UpcomingItem(it.nextExpected, it.merchant, "🔁", it.typicalAmountPaise, UpcomingKind.RECURRING) }
        return (billItems + recurringItems).sortedBy { it.date }
    }

    fun dateOf(millis: Long, zone: ZoneId = ZoneId.of("Asia/Kolkata")): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
