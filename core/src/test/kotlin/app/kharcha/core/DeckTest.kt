package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class DeckTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 15)
    private fun at(day: Int, hour: Int = 12) = LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun txn(rupees: Long, category: Category, day: Int, status: ReviewStatus = ReviewStatus.REVIEWED, merchant: String = "Swiggy", hour: Int = 12) =
        Transaction(
            amountPaise = rupees * 100, direction = Direction.DEBIT, timestampMillis = at(day, hour), merchant = merchant,
            merchantKey = Merchants.keyOf(merchant), category = category, source = Source.SMS, rawText = "", status = status,
        )

    @Test
    fun `basic cards are always there, the rest only when they have something to show`() {
        val cards = Deck.build(emptyList(), emptyList(), emptyList(), monthlyLimitPaise = null, streak = 0, today = today)
        assertEquals(listOf(DeckCard.Review::class, DeckCard.Today::class, DeckCard.Month::class), cards.map { it::class })

        val withLimit = Deck.build(emptyList(), emptyList(), emptyList(), monthlyLimitPaise = 3_000_000, streak = 0, today = today)
        assertTrue(withLimit[1] is DeckCard.Safe)
    }

    @Test
    fun `review shows the newest waiting payments, today counts waiting ones, month counts only reviewed`() {
        val data = listOf(
            txn(300, Category.FOOD_DELIVERY, day = 15, status = ReviewStatus.PENDING, hour = 9),
            txn(120, Category.CABS, day = 15, status = ReviewStatus.PENDING, merchant = "Uber", hour = 11),
            txn(80, Category.CABS, day = 14, status = ReviewStatus.PENDING, merchant = "Rapido"),
            txn(500, Category.KIRANA, day = 15, merchant = "DMart"),
            txn(999, Category.ONLINE_SHOPPING, day = 15, status = ReviewStatus.IGNORED, merchant = "Amazon"),
            txn(200, Category.FOOD_DELIVERY, day = 9),
        )
        val cards = Deck.build(data, emptyList(), emptyList(), monthlyLimitPaise = null, streak = 3, today = today)

        val review = cards.filterIsInstance<DeckCard.Review>().single()
        assertEquals(3, review.count)
        assertEquals(listOf("Uber", "Swiggy"), review.top.map { it.merchant })

        val todayCard = cards.filterIsInstance<DeckCard.Today>().single()
        assertEquals(DeckCard.Today(spentPaise = 92_000, payments = 3, waiting = 2), todayCard)

        val month = cards.filterIsInstance<DeckCard.Month>().single()
        assertEquals(70_000L, month.spentPaise)
        assertEquals(listOf(20_000L, 0L, 0L, 0L, 0L, 0L, 50_000L), month.lastSevenDays)
    }

    @Test
    fun `budgets come fullest first and safe to spend uses reviewed spend`() {
        val data = listOf(txn(2900, Category.FOOD_DELIVERY, day = 5), txn(1000, Category.ONLINE_SHOPPING, day = 6, merchant = "Myntra"))
        val budgets = listOf(
            Budget(1, BudgetKind.GROUP, Category.G_SHOPPING.id, "Shopping", "🛍️", 800_000),
            Budget(2, BudgetKind.GROUP, Category.G_FOOD.id, "Food & drinks", "🍔", 300_000),
        )
        val cards = Deck.build(data, budgets, emptyList(), monthlyLimitPaise = 1_000_000, streak = 0, today = today)

        assertEquals(listOf(2L, 1L), cards.filterIsInstance<DeckCard.Budgets>().single().items.map { it.budget.id })
        val safe = cards.filterIsInstance<DeckCard.Safe>().single()
        assertEquals(390_000L, safe.spentPaise)
        assertEquals((1_000_000L - 390_000L) / 17, safe.safeTodayPaise) // 17 days left including the 15th
        assertNull(cards.filterIsInstance<DeckCard.Upcoming>().firstOrNull())
    }
}
