package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ReportsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 15)
    private fun at(day: Int, month: Int = 10, year: Int = 2026) = LocalDate.of(year, month, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun txn(rupees: Long, category: Category, merchant: String = "Swiggy", day: Int = 5, month: Int = 10, year: Int = 2026, direction: Direction = Direction.DEBIT) =
        Transaction(
            amountPaise = rupees * 100, direction = direction, timestampMillis = at(day, month, year), merchant = merchant,
            merchantKey = Merchants.keyOf(merchant), category = category, source = Source.SMS, rawText = "",
        )

    @Test
    fun `merchant page totals, averages and months`() {
        val data = listOf(
            txn(400, Category.FOOD_DELIVERY, day = 2),
            txn(600, Category.FOOD_DELIVERY, day = 9),
            txn(100, Category.FOOD_DELIVERY, day = 10, direction = Direction.CREDIT),
            txn(300, Category.QUICK_COMMERCE, day = 3, month = 9),
            txn(999, Category.CABS, merchant = "Uber"),
        )
        val page = Reports.merchant(Merchants.keyOf("Swiggy"), data, today)!!
        assertEquals(Category.FOOD_DELIVERY, page.category)
        assertEquals(120_000L, page.totalPaise)
        assertEquals(90_000L, page.thisMonthPaise)
        assertEquals(3, page.payments)
        assertEquals(43_333L, page.averagePaise)
        assertEquals(listOf(0L, 0L, 0L, 0L, 30_000L, 90_000L), page.monthly.map { it.second })
        assertEquals(LocalDate.of(2026, 9, 3), page.first)
    }

    @Test
    fun `tax view counts the current financial year only`() {
        val data = listOf(
            txn(50000, Category.RETIREMENT, merchant = "PPF", day = 10, month = 4),
            txn(20000, Category.RETIREMENT, merchant = "PPF", day = 10, month = 3), // last financial year
            txn(12000, Category.INSURANCE, merchant = "Star Health", day = 1, month = 6),
            txn(500, Category.FOOD_DELIVERY),
        )
        val lines = Reports.tax(data, today)
        assertEquals(listOf("PPF, NPS & EPF", "Insurance premiums"), lines.map { it.label })
        assertEquals(50_000_00L, lines[0].paidPaise)
        assertEquals(LocalDate.of(2025, 4, 1), Reports.financialYearStart(LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun `month report and its share text`() {
        val data = listOf(
            txn(2000, Category.FOOD_DELIVERY, day = 2),
            txn(500, Category.CABS, merchant = "Uber", day = 2),
            txn(5000, Category.MUTUAL_FUNDS, merchant = "Zerodha", day = 5),
        )
        val report = Reports.month(data, YearMonth.of(2026, 10), today)
        assertEquals(250_000L, report.spentPaise)
        assertEquals(14, report.noSpendDays)
        assertEquals(15, report.days)
        assertEquals("Swiggy", report.biggest?.merchant)
        val text = Reports.monthText(report)
        assertTrue(text.startsWith("October 2026 on Kharcha\nSpent ₹2,500"), text)
        assertTrue("No-spend days: 14 of 15" in text)
    }

    @Test
    fun `heat levels split spending days into quartiles`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 0), Reports.heatLevels(listOf(0, 100, 200, 300, 400, 0)))
        assertEquals(listOf(0, 0), Reports.heatLevels(listOf(0, 0)))
    }
}
