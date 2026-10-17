package app.kharcha.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Everything about one merchant or payee. */
data class MerchantPage(
    val key: String,
    val name: String,
    val category: Category,
    val totalPaise: Long,
    val thisMonthPaise: Long,
    val payments: Int,
    val averagePaise: Long,
    /** Spend in each of the last six months, oldest first. */
    val monthly: List<Pair<YearMonth, Long>>,
    val first: LocalDate?,
    val last: LocalDate?,
    /** Reviewed payments, newest first. */
    val history: List<Transaction>,
)

/** One line of the tax view: what you paid this financial year that may be deductible. */
data class TaxLine(val section: String, val label: String, val paidPaise: Long, val capPaise: Long?, val note: String)

data class MonthReport(
    val month: YearMonth,
    val spentPaise: Long,
    val changePercent: Int?,
    val receivedPaise: Long,
    val savedPaise: Long,
    val topGroups: List<GroupTotal>,
    val topMerchants: List<MerchantTotal>,
    val biggest: Transaction?,
    val noSpendDays: Int,
    val days: Int,
)

object Reports {
    private fun dateOf(t: Transaction, zone: ZoneId) = Instant.ofEpochMilli(t.timestampMillis).atZone(zone).toLocalDate()

    fun merchant(key: String, transactions: List<Transaction>, today: LocalDate, zone: ZoneId = ZoneId.of("Asia/Kolkata")): MerchantPage? {
        val history = transactions.filter { it.status == ReviewStatus.REVIEWED && it.merchantKey == key }.sortedByDescending { it.timestampMillis }
        if (history.isEmpty()) return null
        val net = { list: List<Transaction> -> netSpendByCategory(expand(list)).values.sum() }
        val month = YearMonth.from(today)
        val spends = history.filter { it.isSpend }
        return MerchantPage(
            key = key,
            name = history.first().merchant,
            category = history.groupingBy { it.category }.eachCount().maxByOrNull { it.value }!!.key,
            totalPaise = net(history),
            thisMonthPaise = net(history.filter { YearMonth.from(dateOf(it, zone)) == month }),
            payments = spends.size,
            averagePaise = if (spends.isEmpty()) 0 else spends.sumOf { it.amountPaise } / spends.size,
            monthly = (5 downTo 0).map { back ->
                val m = month.minusMonths(back.toLong())
                m to net(history.filter { YearMonth.from(dateOf(it, zone)) == m })
            },
            first = history.lastOrNull()?.let { dateOf(it, zone) },
            last = history.firstOrNull()?.let { dateOf(it, zone) },
            history = history,
        )
    }

    /** The Indian financial year (April to March) that [date] falls in, as its first day. */
    fun financialYearStart(date: LocalDate): LocalDate =
        if (date.monthValue >= 4) LocalDate.of(date.year, 4, 1) else LocalDate.of(date.year - 1, 4, 1)

    /**
     * Payments this financial year in categories that often qualify for old-regime deductions. A
     * starting point to check with your documents, not tax advice: the app can't tell ELSS from other
     * funds or health from life insurance.
     */
    fun tax(transactions: List<Transaction>, today: LocalDate, zone: ZoneId = ZoneId.of("Asia/Kolkata")): List<TaxLine> {
        val start = financialYearStart(today)
        val year = expand(transactions.filter { it.status == ReviewStatus.REVIEWED && it.direction == Direction.DEBIT })
            .filter { !dateOf(it, zone).isBefore(start) }
        fun paid(vararg categories: Category) = year.filter { t -> categories.any { it.id == t.category.id } }.sumOf { it.amountPaise }
        val lakh = 1_00_000_00L
        return listOf(
            TaxLine("80C", "PPF, NPS & EPF", paid(Category.RETIREMENT), 1_50_000_00L, "Shares the ₹1.5 lakh 80C limit"),
            TaxLine("80C", "School & college fees", paid(Category.SCHOOL_FEES), 1_50_000_00L, "Tuition fees for up to two children count under 80C"),
            TaxLine("80C / 80D", "Insurance premiums", paid(Category.INSURANCE), null, "Life cover is 80C; health cover is 80D (₹25,000, more for parents)"),
            TaxLine("80C", "Mutual fund SIPs", paid(Category.MUTUAL_FUNDS), 1_50_000_00L, "Only ELSS (tax-saver) funds count"),
            TaxLine("80G", "Donations", paid(Category.CHARITY), null, "Keep receipts; 50% or 100% deductible depending on the cause"),
            TaxLine("24(b) / 80C", "Loan EMIs", paid(Category.EMI), 2 * lakh, "Home loan interest up to ₹2 lakh, principal under 80C"),
        ).filter { it.paidPaise > 0 }
    }

    fun month(transactions: List<Transaction>, month: YearMonth, today: LocalDate, groupOf: (Category) -> Category = Category::groupOf, zone: ZoneId = ZoneId.of("Asia/Kolkata")): MonthReport {
        val engine = InsightsEngine(zone)
        val reviewed = transactions.filter { it.status == ReviewStatus.REVIEWED }
        val endOfMonth = if (YearMonth.from(today) == month) today else month.atEndOfMonth()
        val summary = engine.summary(reviewed, endOfMonth)
        val daily = engine.dailyTotals(reviewed, month, endOfMonth)
        return MonthReport(
            month = month,
            spentPaise = summary.spentPaise,
            changePercent = summary.changePercent,
            receivedPaise = summary.receivedPaise,
            savedPaise = summary.savedPaise,
            topGroups = summary.byGroup(groupOf).take(3),
            topMerchants = summary.topMerchants.take(3),
            biggest = reviewed.filter { it.isSpend && YearMonth.from(dateOf(it, zone)) == month }.maxByOrNull { it.amountPaise },
            noSpendDays = daily.count { it.second == 0L },
            days = daily.size,
        )
    }

    /** The month as a short message to share. */
    fun monthText(r: MonthReport): String = buildString {
        val name = r.month.month.name.lowercase().replaceFirstChar { it.uppercase() }
        appendLine("$name ${r.month.year} on Kharcha")
        append("Spent ${Money.format(r.spentPaise)}")
        r.changePercent?.takeIf { it != 0 }?.let { append(if (it > 0) " ($it% more than last month)" else " (${-it}% less than last month)") }
        appendLine()
        if (r.topGroups.isNotEmpty()) appendLine("Most on: " + r.topGroups.joinToString(", ") { "${it.group.label} ${Money.format(it.spentPaise)}" })
        r.biggest?.let { appendLine("Biggest: ${Money.format(it.amountPaise)} at ${it.merchant}") }
        appendLine("No-spend days: ${r.noSpendDays} of ${r.days}")
        if (r.savedPaise > 0) appendLine("Saved & invested: ${Money.format(r.savedPaise)}")
    }.trim()

    /** 0 (nothing) to 4 (heaviest) for each day, by quartile of the days that had spending. */
    fun heatLevels(values: List<Long>): List<Int> {
        val positive = values.filter { it > 0 }.sorted()
        if (positive.isEmpty()) return values.map { 0 }
        fun q(f: Double) = positive[((positive.size - 1) * f).toInt()]
        val (q1, q2, q3) = Triple(q(0.25), q(0.5), q(0.75))
        return values.map { v ->
            when {
                v <= 0 -> 0
                v <= q1 -> 1
                v <= q2 -> 2
                v <= q3 -> 3
                else -> 4
            }
        }
    }
}
