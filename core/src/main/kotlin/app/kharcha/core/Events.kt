package app.kharcha.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Tags as stored in one database column. */
object Tags {
    /** "Goa Trip, " -> "goa trip". Tags are lower-case words with no commas. */
    fun clean(tag: String): String = tag.replace(",", " ").trim().replace(Regex("\\s+"), " ").lowercase()

    fun encode(tags: List<String>): String? = tags.map(::clean).filter { it.isNotEmpty() }.distinct().joinToString(",").ifEmpty { null }

    fun decode(text: String?): List<String> = text?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    /** Every tag in use, most used first. */
    fun inUse(transactions: List<Transaction>): List<String> =
        transactions.flatMap { it.tags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
}

/**
 * A trip or occasion, like "Goa trip". Its payments are the ones carrying its [tag]; while it is on,
 * new payments get the tag by themselves. [end] is null while it is still going.
 */
data class Event(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val start: LocalDate,
    val end: LocalDate? = null,
) {
    val tag: String get() = Tags.clean(name)

    fun covers(date: LocalDate): Boolean = !date.isBefore(start) && (end == null || !date.isAfter(end))
}

data class EventSummary(
    val event: Event,
    val spentPaise: Long,
    val payments: Int,
    val byCategory: List<CategoryTotal>,
    /** Spend per day of the trip, first day first (up to [today] while it is going). */
    val perDay: List<Pair<LocalDate, Long>>,
)

object Events {
    fun summary(event: Event, transactions: List<Transaction>, today: LocalDate, zone: ZoneId = ZoneId.of("Asia/Kolkata")): EventSummary {
        fun dateOf(t: Transaction) = Instant.ofEpochMilli(t.timestampMillis).atZone(zone).toLocalDate()
        val tagged = transactions.filter { it.status == ReviewStatus.REVIEWED && event.tag in it.tags }
        val expanded = expand(tagged)
        val net = netSpendByCategory(expanded)
        val last = minOf(event.end ?: today, maxOf(today, event.start))
        val byDay = expanded.groupBy(::dateOf).mapValues { (_, v) -> netSpendByCategory(v).values.sum() }
        val days = generateSequence(event.start) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.take(366).toList()
        return EventSummary(
            event = event,
            spentPaise = net.values.sum(),
            payments = tagged.count { it.isSpend },
            byCategory = net.map { (category, total) -> CategoryTotal(category, total) }.sortedByDescending { it.spentPaise },
            perDay = days.map { it to (byDay[it] ?: 0L) },
        )
    }

    /** Tags of events that are on at [date], to put on a new payment. */
    fun autoTags(events: List<Event>, date: LocalDate): List<String> = events.filter { it.covers(date) }.map { it.tag }

    /** Spends in the event's dates that don't carry its tag yet, for "tag all payments in these dates". */
    fun untaggedInRange(event: Event, transactions: List<Transaction>, today: LocalDate, zone: ZoneId = ZoneId.of("Asia/Kolkata")): List<Transaction> =
        transactions.filter {
            val date = Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
            it.status != ReviewStatus.IGNORED && it.direction == Direction.DEBIT && it.category.countsAsSpend &&
                event.copy(end = event.end ?: today).covers(date) && event.tag !in it.tags
        }
}
