package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ControlTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 15)
    private fun at(day: Int, month: Int = 10) = LocalDate.of(2026, month, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun txn(rupees: Long, category: Category, merchant: String = "Swiggy", day: Int = 5, direction: Direction = Direction.DEBIT) =
        Transaction(
            amountPaise = rupees * 100, direction = direction, timestampMillis = at(day), merchant = merchant,
            merchantKey = Merchants.keyOf(merchant), category = category, source = Source.SMS, rawText = "",
        )

    @Test
    fun `budgets track groups, subcategories and merchants, with refunds taken off`() {
        val data = listOf(
            txn(2400, Category.FOOD_DELIVERY),
            txn(400, Category.FOOD_DELIVERY, direction = Direction.CREDIT, day = 6),
            txn(900, Category.RESTAURANTS, merchant = "Barbeque Nation"),
            txn(500, Category.QUICK_COMMERCE, merchant = "Swiggy", day = 7),
        )
        val budgets = listOf(
            Budget(1, BudgetKind.GROUP, Category.G_FOOD.id, "Food & drinks", "🍔", 300000),
            Budget(2, BudgetKind.CATEGORY, Category.FOOD_DELIVERY.id, "Food delivery", "🛵", 150000),
            Budget(3, BudgetKind.MERCHANT, "swiggy", "Swiggy", "🛵", 300000),
        )
        val status = Budgets.status(budgets, data, today).associateBy { it.budget.id }
        assertEquals(290000L, status.getValue(1).spentPaise) // 2400 - 400 + 900
        assertEquals(BudgetLevel.NEAR, status.getValue(1).level)
        assertEquals(200000L, status.getValue(2).spentPaise)
        assertEquals(BudgetLevel.OVER, status.getValue(2).level)
        assertEquals(250000L, status.getValue(3).spentPaise) // food delivery + Instamart from Swiggy
        assertEquals(200000L * 31 / 15, status.getValue(2).projectedPaise)
    }

    @Test
    fun `accounts show the latest balance and this month's spend`() {
        val a = txn(450, Category.FOOD_DELIVERY).copy(bank = "HDFC", accountLast4 = "1234", balancePaise = 4521000, timestampMillis = at(3))
        val b = txn(200, Category.CABS).copy(bank = "HDFC", accountLast4 = "1234", balancePaise = 4501000, timestampMillis = at(4))
        val card = txn(649, Category.OTT).copy(bank = "HDFC", accountLast4 = "5678", rawText = "Spent Rs.649 On HDFC Bank Credit Card 5678", balancePaise = 9000000)
        val accounts = Accounts.summarize(listOf(a, b, card), YearMonth.of(2026, 10))
        val savings = accounts.single { it.last4 == "1234" }
        assertEquals(4501000L, savings.latestBalancePaise)
        assertEquals(65000L, savings.spentThisMonthPaise)
        assertTrue(accounts.single { it.last4 == "5678" }.isCreditCard)
    }

    @Test
    fun `credit card statements and due reminders are read`() {
        val hdfc = CardBills.parse(
            "AX-HDFCBK",
            "Your HDFC Bank Credit Card XX5678 statement is generated. Total Amount Due: Rs.18,420.00, Minimum Amount Due: Rs.920.00, Payment Due Date: 05-11-2026.",
            at(15),
        )!!
        assertEquals(1842000L, hdfc.totalDuePaise)
        assertEquals(92000L, hdfc.minDuePaise)
        assertEquals(LocalDate.of(2026, 11, 5), hdfc.dueDate)
        assertEquals("5678", hdfc.last4)
        assertEquals("HDFC", hdfc.bank)

        val sbi = CardBills.parse(
            "VM-SBICRD",
            "E-statement of SBI Credit Card ending 4321 dated 15/10/2026 has been sent. Total Amt Due Rs.12,300; Min Amt Due Rs.615; Payable by 04/11/2026",
            at(15),
        )!!
        assertEquals(1230000L, sbi.totalDuePaise)
        assertEquals(LocalDate.of(2026, 11, 4), sbi.dueDate)
        assertEquals("4321", sbi.last4)

        val icici = CardBills.parse(
            "AX-ICICIB",
            "ICICI Bank Credit Card XX9876 Statement: Total Due: INR 7,450.00, Min Due: INR 375.00, Due Date: 05-Nov-26.",
            at(15),
        )!!
        assertEquals(LocalDate.of(2026, 11, 5), icici.dueDate)

        val reminder = CardBills.parse("AX-HDFCBK", "Payment of Rs.18,420 for your HDFC Bank Credit Card XX5678 is due on 05-11-2026. Please pay on time.", at(2, 11))!!
        assertEquals(1842000L, reminder.totalDuePaise)

        // An ordinary card spend is not a bill.
        assertNull(CardBills.parse("AD-HDFCBK", "Spent Rs.649 On HDFC Bank Card 5678 At NETFLIX On 2026-10-16:09:12:01 Bal Rs.45,000.00", at(16)))
    }

    @Test
    fun `bills turn paid when the card payment arrives, and upcoming lists what is due`() {
        val bill = CardBill(id = 7, bank = "HDFC", last4 = "5678", totalDuePaise = 1842000, minDuePaise = 92000, dueDate = LocalDate.of(2026, 11, 5), receivedAtMillis = at(15), rawText = "")
        val nov = LocalDate.of(2026, 11, 1)
        assertEquals(BillState.DUE, CardBills.state(bill, emptyList(), nov))
        assertEquals(BillState.OVERDUE, CardBills.state(bill, emptyList(), LocalDate.of(2026, 11, 6)))
        val paid = txn(18420, Category.CARD_BILL, merchant = "Credit card bill", day = 20).copy(accountLast4 = "5678")
        assertEquals(BillState.PAID, CardBills.state(bill, listOf(paid), nov))
        assertEquals(BillState.PARTLY_PAID, CardBills.state(bill, listOf(paid.copy(amountPaise = 92000)), nov))

        val netflix = RecurringPayment("Netflix", "netflix", 64900, LocalDate.of(2026, 10, 21), LocalDate.of(2026, 11, 21))
        val upcoming = Upcoming.next(listOf(bill), listOf(netflix), emptyList(), nov)
        assertEquals(listOf("HDFC ··5678 card bill", "Netflix"), upcoming.map { it.label })
        assertTrue(Upcoming.next(listOf(bill), listOf(netflix), listOf(paid), nov).none { it.kind == UpcomingKind.CARD_BILL })
    }
}
