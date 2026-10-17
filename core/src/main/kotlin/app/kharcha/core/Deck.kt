package app.kharcha.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One face of the home-screen widget. Built here, away from Android, so the rules can be tested. */
sealed interface DeckCard {
    /** Payments waiting for review, newest first ([top] holds up to two of them). */
    data class Review(val count: Int, val top: List<Transaction>, val streak: Int) : DeckCard

    data class Safe(val safeTodayPaise: Long, val spentPaise: Long, val limitPaise: Long) : DeckCard

    /** Today's payments, reviewed or waiting. */
    data class Today(val spentPaise: Long, val payments: Int, val waiting: Int) : DeckCard

    /** [lastSevenDays] is the reviewed spend of each of the last seven days, today last. */
    data class Month(
        val month: YearMonth,
        val spentPaise: Long,
        val changePercent: Int?,
        val lastSevenDays: List<Long>,
        val topGroups: List<GroupTotal>,
    ) : DeckCard

    /** The budgets closest to their limit, up to three. */
    data class Budgets(val items: List<BudgetStatus>) : DeckCard

    /** What is due in the next 30 days; [items] holds the first two. */
    data class Upcoming(val totalPaise: Long, val count: Int, val items: List<UpcomingItem>) : DeckCard
}

object Deck {
    /**
     * The cards in widget order. Review, Today and Month are always there; Safe needs a monthly limit,
     * Budgets needs a budget and Upcoming needs something due.
     */
    fun build(
        all: List<Transaction>,
        budgets: List<Budget>,
        bills: List<CardBill>,
        monthlyLimitPaise: Long?,
        streak: Int,
        today: LocalDate,
        groupOf: (Category) -> Category = Category::groupOf,
        zone: ZoneId = ZoneId.of("Asia/Kolkata"),
    ): List<DeckCard> {
        val engine = InsightsEngine(zone)
        val reviewed = all.filter { it.status == ReviewStatus.REVIEWED }
        val pending = all.filter { it.status == ReviewStatus.PENDING }.sortedByDescending { it.timestampMillis }
        val live = all.filter { it.status != ReviewStatus.IGNORED }
        val summary = engine.summary(reviewed, today, monthlyLimitPaise)
        fun dateOf(t: Transaction) = Instant.ofEpochMilli(t.timestampMillis).atZone(zone).toLocalDate()

        val todays = live.filter { dateOf(it) == today }
        val byDay = expand(reviewed).groupBy(::dateOf).mapValues { (_, v) -> netSpendByCategory(v).values.sum() }
        val upcoming = Upcoming.next(bills, engine.recurring(reviewed, today), live, today)
        val budgetStatus = Budgets.status(budgets, reviewed, today, groupOf, zone).sortedByDescending { it.fraction }

        return listOfNotNull(
            DeckCard.Review(pending.size, pending.take(2), streak),
            monthlyLimitPaise?.let { limit -> DeckCard.Safe(summary.safeToSpendTodayPaise ?: 0L, summary.spentPaise, limit) },
            DeckCard.Today(todays.filter { it.isSpend }.sumOf { it.amountPaise }, todays.size, todays.count { it.status == ReviewStatus.PENDING }),
            DeckCard.Month(
                month = summary.month,
                spentPaise = summary.spentPaise,
                changePercent = summary.changePercent,
                lastSevenDays = (6 downTo 0).map { back -> byDay[today.minusDays(back.toLong())] ?: 0L },
                topGroups = summary.byGroup(groupOf).take(3),
            ),
            budgetStatus.takeIf { it.isNotEmpty() }?.let { DeckCard.Budgets(it.take(3)) },
            upcoming.takeIf { it.isNotEmpty() }?.let { DeckCard.Upcoming(it.sumOf { item -> item.amountPaise }, it.size, it.take(2)) },
        )
    }
}
