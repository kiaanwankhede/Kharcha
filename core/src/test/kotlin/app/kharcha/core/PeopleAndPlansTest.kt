package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class PeopleAndPlansTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 15)
    private fun at(day: Int, month: Int = 10) = LocalDate.of(2026, month, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun txn(
        rupees: Long,
        category: Category,
        merchant: String = "Rahul Sharma",
        day: Int = 5,
        direction: Direction = Direction.DEBIT,
        vpa: String? = null,
        tags: List<String> = emptyList(),
        reimburse: Reimburse? = null,
        month: Int = 10,
    ) = Transaction(
        amountPaise = rupees * 100, direction = direction, timestampMillis = at(day, month), merchant = merchant,
        merchantKey = Merchants.keyOf(merchant), category = category, source = Source.SMS, rawText = "", vpa = vpa,
        tags = tags, reimburse = reimburse,
    )

    @Test
    fun `ledger nets lending, borrowing and settling per person`() {
        val data = listOf(
            txn(2000, Category.LENT, vpa = "rahul@okaxis", day = 2),
            txn(500, Category.PAID_BACK, direction = Direction.CREDIT, day = 6),
            txn(800, Category.BORROWED, merchant = "Priya", direction = Direction.CREDIT, vpa = "priya@ybl"),
            txn(800, Category.REPAYING, merchant = "Asha", day = 3),
            txn(800, Category.BORROWED, merchant = "Asha", direction = Direction.CREDIT, day = 1),
            txn(300, Category.FOOD_DELIVERY, merchant = "Swiggy"),
        )
        val ledger = People.ledger(data)
        assertEquals(listOf("Rahul Sharma", "Priya", "Asha"), ledger.map { it.name })
        assertEquals(150_000L, ledger[0].owedToMePaise)
        assertEquals("rahul@okaxis", ledger[0].vpa)
        assertEquals(-80_000L, ledger[1].owedToMePaise)
        assertTrue(ledger[2].isSettled)
    }

    @Test
    fun `money from someone who owes you is suggested as paid back`() {
        val ledger = People.ledger(listOf(txn(2000, Category.LENT), txn(800, Category.BORROWED, merchant = "Priya", direction = Direction.CREDIT)))
        assertEquals(Category.PAID_BACK, People.suggest(txn(500, Category.RECEIVED, direction = Direction.CREDIT), ledger))
        assertEquals(Category.REPAYING, People.suggest(txn(800, Category.PEOPLE, merchant = "Priya"), ledger))
        assertNull(People.suggest(txn(500, Category.PEOPLE), ledger)) // paying Rahul more is not repaying
        assertNull(People.suggest(txn(500, Category.RECEIVED, merchant = "Someone new", direction = Direction.CREDIT), ledger))
    }

    @Test
    fun `upi link is encoded for any UPI app`() {
        assertEquals(
            "upi://pay?pa=me%40okhdfc&pn=Kiaan%20W&am=1500.50&cu=INR&tn=Settling%20up",
            People.upiLink("me@okhdfc", "Kiaan W", 150_050, "Settling up"),
        )
    }

    @Test
    fun `reimbursable payments are not spending, but logged cash still counts against withdrawals`() {
        val office = txn(1200, Category.CABS, merchant = "Uber", reimburse = Reimburse.PENDING)
        assertFalse(office.isSpend)
        val summary = InsightsEngine(zone).summary(listOf(office, txn(300, Category.FOOD_DELIVERY, merchant = "Swiggy")), today)
        assertEquals(30_000L, summary.spentPaise)

        val cash = office.copy(source = Source.MANUAL)
        assertTrue(cash.isLoggedCash)
    }

    @Test
    fun `trip totals come from its tag, and payments in its dates can be tagged`() {
        val goa = Event(1, "Goa Trip", "🏖️", start = LocalDate.of(2026, 10, 10), end = LocalDate.of(2026, 10, 12))
        val data = listOf(
            txn(4000, Category.FLIGHTS, merchant = "IndiGo", day = 1, tags = listOf("goa trip")),
            txn(900, Category.RESTAURANTS, merchant = "Britto's", day = 10, tags = listOf("goa trip")),
            txn(600, Category.CABS, merchant = "Goa Miles", day = 11),
            txn(300, Category.FOOD_DELIVERY, merchant = "Swiggy", day = 14),
        )
        val summary = Events.summary(goa, data, today)
        assertEquals(490_000L, summary.spentPaise)
        assertEquals(3, summary.perDay.size)
        assertEquals(90_000L, summary.perDay.first().second)
        assertEquals(listOf("Goa Miles"), Events.untaggedInRange(goa, data, today).map { it.merchant })
        assertEquals(listOf("goa trip"), Events.autoTags(listOf(goa), LocalDate.of(2026, 10, 12)))
        assertEquals(emptyList<String>(), Events.autoTags(listOf(goa), LocalDate.of(2026, 10, 13)))
        assertEquals("goa trip,office", Tags.encode(listOf(" Goa  Trip", "office", "goa trip", "")))
        assertEquals(listOf("goa trip", "office"), Tags.decode("goa trip,office"))
    }

    @Test
    fun `goal progress adds linked savings since it was created and spreads the rest over the months left`() {
        val goal = Goal(
            1, "Emergency fund", "🛟", targetPaise = 1_00_000_00, deadline = LocalDate.of(2027, 3, 31),
            addedPaise = 20_000_00, linkedCategoryId = Category.MUTUAL_FUNDS.id, createdOn = LocalDate.of(2026, 9, 1),
        )
        val data = listOf(
            txn(5000, Category.MUTUAL_FUNDS, merchant = "Zerodha", day = 5, month = 8), // before the goal
            txn(5000, Category.MUTUAL_FUNDS, merchant = "Zerodha", day = 5, month = 9),
            txn(5000, Category.MUTUAL_FUNDS, merchant = "Zerodha", day = 5),
        )
        val progress = Goals.progress(goal, data, today)
        assertEquals(30_000_00L, progress.savedPaise)
        assertEquals(70_000_00L / 6 + 1, progress.perMonthNeededPaise) // Oct..Mar is 6 months, rounded up
        assertNull(Goals.progress(goal.copy(addedPaise = 2_00_000_00), data, today).perMonthNeededPaise)
    }
}
