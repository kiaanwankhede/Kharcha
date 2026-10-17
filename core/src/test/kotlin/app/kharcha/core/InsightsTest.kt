package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class InsightsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val engine = InsightsEngine(zone)
    private val today = LocalDate.of(2026, 10, 17)

    private fun spend(date: LocalDate, rupees: Long, merchant: String, category: Category) = Transaction(
        amountPaise = rupees * 100,
        direction = Direction.DEBIT,
        timestampMillis = date.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
        merchant = merchant,
        merchantKey = Merchants.keyOf(merchant),
        category = category,
        source = Source.SMS,
        rawText = "",
    )

    private val data = buildList {
        // Steady background spend for three months.
        var d = LocalDate.of(2026, 7, 1)
        while (d <= today) {
            add(spend(d, 120, "Chai Point", Category.FOOD))
            d = d.plusDays(1)
        }
        // Netflix every month on the 21st.
        listOf(7, 8, 9).forEach { add(spend(LocalDate.of(2026, it, 21), 649, "Netflix", Category.SUBSCRIPTIONS)) }
        // October weekend Zomato orders push Food up.
        listOf(3, 4, 10, 11).forEach { add(spend(LocalDate.of(2026, 10, it), 700, "Zomato", Category.FOOD)) }
        // One big purchase.
        add(spend(LocalDate.of(2026, 10, 12), 8999, "Croma", Category.SHOPPING))
        // Own transfer must not count as spend.
        add(spend(LocalDate.of(2026, 10, 5), 18420, "Credit card bill", Category.TRANSFER))
    }

    @Test
    fun `summary compares with the same point last month`() {
        val s = engine.summary(data, today, monthlyLimitPaise = 30_000_00)
        assertEquals((17 * 120 + 4 * 700 + 8999) * 100L, s.spentPaise)
        assertEquals(17 * 120 * 100L, s.previousSamePointPaise)
        assertEquals(Category.SHOPPING, s.byCategory.first().category)
        // ₹30,000 limit minus spent, over the 15 days left including today.
        assertEquals((30_000_00 - s.spentPaise) / 15, s.safeToSpendTodayPaise)
    }

    @Test
    fun `finds the monthly subscription`() {
        val r = engine.recurring(data, today)
        assertEquals(listOf("Netflix"), r.map { it.merchant })
        assertEquals(LocalDate.of(2026, 10, 21), r.first().nextExpected)
    }

    @Test
    fun `explains the spike and flags the unusual purchase`() {
        val insights = engine.insights(data, today)
        val spike = insights.firstOrNull { it.kind == InsightKind.SPIKE && it.title.startsWith("Food") }
        assertNotNull(spike)
        assertTrue(spike!!.body.contains("mostly Zomato on weekends"), spike.body)
        val unusual = insights.first { it.kind == InsightKind.UNUSUAL }
        assertTrue(unusual.body.startsWith("Croma on 12 October"), unusual.body)
        assertTrue(insights.any { it.kind == InsightKind.RECURRING })
    }
}

class MonthlyTotalsTest {
    @Test
    fun `six months oldest first, empty months are zero`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val t = Transaction(
            amountPaise = 50000, direction = Direction.DEBIT,
            timestampMillis = LocalDate.of(2026, 9, 3).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            merchant = "A", merchantKey = "a", category = Category.FOOD, source = Source.SMS, rawText = "",
        )
        val totals = InsightsEngine(zone).monthlyTotals(listOf(t, t.copy(category = Category.TRANSFER)), LocalDate.of(2026, 10, 17))
        assertEquals(6, totals.size)
        assertEquals(java.time.YearMonth.of(2026, 5), totals.first().first)
        assertEquals(50000L, totals[4].second)
        assertEquals(0L, totals[5].second)
    }

    @Test
    fun `custom categories survive a backup round trip`() {
        val mine = Category.custom("Pet care", "🐶")
        val payload = BackupPayload(createdAtMillis = 1, transactions = emptyList(), customCategories = listOf(mine), merchantRules = mapOf("vet" to mine))
        val decoded = BackupCodec.decode(BackupCodec.encode(payload, "passphrase1".toCharArray()), "passphrase1".toCharArray())
        assertEquals(payload, decoded)
        assertEquals(mine, CategoryCatalog(decoded.customCategories).byId(mine.id))
        assertEquals(Category.OTHER, CategoryCatalog().byId("custom_gone"))
    }
}
