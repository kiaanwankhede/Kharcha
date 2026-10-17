package app.kharcha.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

data class CategoryTotal(val category: Category, val spentPaise: Long)

data class MerchantTotal(val merchant: String, val merchantKey: String, val spentPaise: Long, val count: Int)

data class RecurringPayment(
    val merchant: String,
    val merchantKey: String,
    val typicalAmountPaise: Long,
    val lastPaid: LocalDate,
    val nextExpected: LocalDate,
)

data class MonthSummary(
    val month: YearMonth,
    val spentPaise: Long,
    val receivedPaise: Long,
    /** Spend in the previous month up to the same day of the month, for a fair comparison. */
    val previousSamePointPaise: Long,
    val byCategory: List<CategoryTotal>,
    val topMerchants: List<MerchantTotal>,
    /** Whole-number percentage change vs the previous month at the same point; null when there is nothing to compare. */
    val changePercent: Int?,
    /** What is left of the monthly limit divided by the days left, including today; null without a limit. */
    val safeToSpendTodayPaise: Long?,
)

enum class InsightKind { SUMMARY, SPIKE, DROP, RECURRING, UNUSUAL, HABIT }

data class Insight(val kind: InsightKind, val title: String, val body: String)

/**
 * Plain-rule insights computed on the phone from the user's own transactions. Nothing here calls
 * a server or a model, so every number can be traced back to the transactions behind it.
 */
class InsightsEngine(private val zone: ZoneId = ZoneId.of("Asia/Kolkata")) {

    fun summary(all: List<Transaction>, today: LocalDate, monthlyLimitPaise: Long? = null): MonthSummary {
        val month = YearMonth.from(today)
        val thisMonth = all.filter { month.contains(it) }
        val spends = thisMonth.filter { it.isSpend }
        val spent = spends.sumOf { it.amountPaise }

        val previous = month.minusMonths(1)
        val lastDay = minOf(today.dayOfMonth, previous.lengthOfMonth())
        val previousSamePoint = all.filter {
            it.isSpend && previous.contains(it) && date(it).dayOfMonth <= lastDay
        }.sumOf { it.amountPaise }

        val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
        val safeToSpend = monthlyLimitPaise?.let { maxOf(0L, (it - spent) / daysLeft) }

        return MonthSummary(
            month = month,
            spentPaise = spent,
            receivedPaise = thisMonth.filter { it.direction == Direction.CREDIT && it.category == Category.INCOME }.sumOf { it.amountPaise },
            previousSamePointPaise = previousSamePoint,
            byCategory = spends.groupBy { it.category }
                .map { (category, items) -> CategoryTotal(category, items.sumOf { it.amountPaise }) }
                .sortedByDescending { it.spentPaise },
            topMerchants = merchantTotals(spends).take(5),
            changePercent = percentChange(previousSamePoint, spent),
            safeToSpendTodayPaise = safeToSpend,
        )
    }

    fun recurring(all: List<Transaction>, today: LocalDate): List<RecurringPayment> {
        val since = today.minusDays(100)
        return all.asSequence()
            .filter { it.isSpend && it.category != Category.PEOPLE && date(it) >= since && date(it) <= today }
            .groupBy { it.merchantKey }
            .mapNotNull { (_, payments) ->
                val byMonth = payments.groupBy { YearMonth.from(date(it)) }
                if (byMonth.size < 2) return@mapNotNull null
                // One payment per month, of a steady amount (within 10% of the median).
                if (byMonth.values.any { it.size > 1 }) return@mapNotNull null
                val median = median(payments.map { it.amountPaise })
                if (payments.any { kotlin.math.abs(it.amountPaise - median) > median / 10 }) return@mapNotNull null
                val last = payments.maxBy { it.timestampMillis }
                val lastDate = date(last)
                RecurringPayment(last.merchant, last.merchantKey, median, lastDate, lastDate.plusMonths(1))
            }
            .sortedBy { it.nextExpected }
    }

    fun insights(all: List<Transaction>, today: LocalDate, monthlyLimitPaise: Long? = null): List<Insight> {
        val summary = summary(all, today, monthlyLimitPaise)
        val result = mutableListOf<Insight>()
        val monthName = summary.month.month.displayName()

        summary.byCategory.firstOrNull()?.let { top ->
            val share = if (summary.spentPaise > 0) (top.spentPaise * 100.0 / summary.spentPaise).roundToInt() else 0
            val change = summary.changePercent?.let { pct ->
                when {
                    pct > 0 -> " That’s $pct% more than this point last month."
                    pct < 0 -> " That’s ${-pct}% less than this point last month."
                    else -> " Same as this point last month."
                }
            }.orEmpty()
            result += Insight(
                InsightKind.SUMMARY,
                "${Money.format(summary.spentPaise)} spent in $monthName",
                "${top.category.label} is your biggest category at ${Money.format(top.spentPaise)} ($share%).$change",
            )
        }

        result += categoryChanges(all, today)
        unusual(all, today)?.let { result += it }

        val recurring = recurring(all, today)
        if (recurring.isNotEmpty()) {
            val monthly = recurring.sumOf { it.typicalAmountPaise }
            val next = recurring.first()
            result += Insight(
                InsightKind.RECURRING,
                "${recurring.size} recurring payment${if (recurring.size == 1) "" else "s"}",
                "${Money.format(monthly)} a month. Next: ${next.merchant}, about ${Money.format(next.typicalAmountPaise)}, around ${next.nextExpected.dayOfMonth} ${next.nextExpected.month.displayName()}.",
            )
        }

        weekendHabit(all, today)?.let { result += it }
        return result
    }

    private fun categoryChanges(all: List<Transaction>, today: LocalDate): List<Insight> {
        val month = YearMonth.from(today)
        val previous = month.minusMonths(1)
        val lastDay = minOf(today.dayOfMonth, previous.lengthOfMonth())
        val now = all.filter { it.isSpend && month.contains(it) && date(it) <= today }
        val before = all.filter { it.isSpend && previous.contains(it) && date(it).dayOfMonth <= lastDay }
        val nowByCategory = now.groupBy { it.category }
        val beforeByCategory = before.groupBy { it.category }

        return (nowByCategory.keys + beforeByCategory.keys).mapNotNull { category ->
            val nowItems = nowByCategory[category].orEmpty()
            val beforeItems = beforeByCategory[category].orEmpty()
            val nowTotal = nowItems.sumOf { it.amountPaise }
            val beforeTotal = beforeItems.sumOf { it.amountPaise }
            val diff = nowTotal - beforeTotal
            val pct = percentChange(beforeTotal, nowTotal) ?: return@mapNotNull null
            if (kotlin.math.abs(diff) < MIN_CHANGE_PAISE || kotlin.math.abs(pct) < MIN_CHANGE_PERCENT) return@mapNotNull null
            if (diff > 0) {
                val driver = biggestIncrease(nowItems, beforeItems)
                val weekendShare = nowItems.filter { isWeekend(date(it)) }.sumOf { it.amountPaise }.toDouble() / nowTotal
                val whenText = if (weekendShare >= 0.6) " on weekends" else ""
                val driverText = driver?.let { ", mostly $it$whenText" }.orEmpty()
                Insight(
                    InsightKind.SPIKE,
                    "${category.label} up $pct%",
                    "${Money.format(diff)} more than this point in ${previous.month.displayName()}$driverText.",
                )
            } else {
                Insight(
                    InsightKind.DROP,
                    "${category.label} down ${-pct}%",
                    "${Money.format(-diff)} less than this point in ${previous.month.displayName()}.",
                )
            }
        }.sortedByDescending { it.kind == InsightKind.SPIKE }
    }

    private fun unusual(all: List<Transaction>, today: LocalDate): Insight? {
        val month = YearMonth.from(today)
        val history = all.filter { it.isSpend && date(it) < month.atDay(1) && date(it) >= month.atDay(1).minusDays(90) }
        if (history.size < 10) return null
        val typical = median(history.map { it.amountPaise })
        val candidate = all.filter { it.isSpend && month.contains(it) && date(it) <= today }
            .filter { it.amountPaise >= UNUSUAL_MIN_PAISE && it.amountPaise >= typical * UNUSUAL_MULTIPLE }
            .maxByOrNull { it.amountPaise } ?: return null
        val times = (candidate.amountPaise.toDouble() / typical).roundToInt()
        val day = date(candidate)
        return Insight(
            InsightKind.UNUSUAL,
            "Unusual: ${Money.format(candidate.amountPaise)}",
            "${candidate.merchant} on ${day.dayOfMonth} ${day.month.displayName()}, about $times× your usual payment.",
        )
    }

    private fun weekendHabit(all: List<Transaction>, today: LocalDate): Insight? {
        val start = today.minusWeeks(8)
        val recent = all.filter { it.isSpend && date(it) > start && date(it) <= today }
        if (recent.size < 15) return null
        val days = generateSequence(start.plusDays(1)) { it.plusDays(1) }.takeWhile { it <= today }.toList()
        val weekendDays = days.count(::isWeekend)
        val weekdayDays = days.size - weekendDays
        if (weekendDays == 0 || weekdayDays == 0) return null
        val weekendAvg = recent.filter { isWeekend(date(it)) }.sumOf { it.amountPaise }.toDouble() / weekendDays
        val weekdayAvg = recent.filter { !isWeekend(date(it)) }.sumOf { it.amountPaise }.toDouble() / weekdayDays
        if (weekdayAvg <= 0) return null
        val ratio = weekendAvg / weekdayAvg
        if (ratio < 1.5) return null
        return Insight(
            InsightKind.HABIT,
            "Weekends cost %.1f× a weekday".format(ratio),
            "Over the last 8 weeks you spent about ${Money.format(weekendAvg.toLong().roundToRupee())} on a weekend day and ${Money.format(weekdayAvg.toLong().roundToRupee())} on a weekday.",
        )
    }

    private fun merchantTotals(spends: List<Transaction>): List<MerchantTotal> =
        spends.groupBy { it.merchantKey }
            .map { (key, items) -> MerchantTotal(items.first().merchant, key, items.sumOf { it.amountPaise }, items.size) }
            .sortedByDescending { it.spentPaise }

    private fun biggestIncrease(now: List<Transaction>, before: List<Transaction>): String? {
        val beforeByMerchant = before.groupBy { it.merchantKey }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }
        return now.groupBy { it.merchantKey }
            .map { (key, items) -> items.first().merchant to items.sumOf { it.amountPaise } - (beforeByMerchant[key] ?: 0L) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first
    }

    private fun date(t: Transaction): LocalDate = dateOf(t)

    internal fun dateOf(t: Transaction): LocalDate = Instant.ofEpochMilli(t.timestampMillis).atZone(zone).toLocalDate()

    private fun YearMonth.contains(t: Transaction): Boolean = YearMonth.from(date(t)) == this

    private fun isWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY

    private fun Long.roundToRupee(): Long = (this + 50) / 100 * 100

    private fun java.time.Month.displayName(): String =
        getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)

    companion object {
        private const val MIN_CHANGE_PAISE = 500_00L
        private const val MIN_CHANGE_PERCENT = 25
        private const val UNUSUAL_MIN_PAISE = 2_000_00L
        private const val UNUSUAL_MULTIPLE = 4

        fun percentChange(before: Long, now: Long): Int? =
            if (before <= 0) null else ((now - before) * 100.0 / before).roundToInt()

        fun median(values: List<Long>): Long {
            if (values.isEmpty()) return 0
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }
    }
}

/** Total spend for each of the [months] months ending with [today]'s month, oldest first. */
fun InsightsEngine.monthlyTotals(all: List<Transaction>, today: LocalDate, months: Int = 6): List<Pair<YearMonth, Long>> {
    val end = YearMonth.from(today)
    val totals = all.filter { it.isSpend }.groupBy { YearMonth.from(dateOf(it)) }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }
    return (months - 1 downTo 0).map { back -> end.minusMonths(back.toLong()).let { it to (totals[it] ?: 0L) } }
}
