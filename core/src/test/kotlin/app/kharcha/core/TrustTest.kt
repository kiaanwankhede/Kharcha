package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class TrustTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 17)
    private fun at(day: Int, hour: Int = 12) = LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun txn(
        rupees: Long,
        category: Category,
        direction: Direction = Direction.DEBIT,
        day: Int = 5,
        merchant: String = "Swiggy",
        source: Source = Source.SMS,
    ) = Transaction(
        amountPaise = rupees * 100, direction = direction, timestampMillis = at(day), merchant = merchant,
        merchantKey = Merchants.keyOf(merchant), category = category, source = source, rawText = "",
    )

    @Test
    fun `a refund under a spending category lowers that category instead of counting as income`() {
        val engine = InsightsEngine(zone)
        val order = txn(800, Category.FOOD_DELIVERY)
        val refund = txn(300, Category.FOOD_DELIVERY, Direction.CREDIT, day = 6)
        val s = engine.summary(listOf(order, refund), today)
        assertEquals(50000L, s.spentPaise)
        assertEquals(0L, s.receivedPaise)
        assertEquals(50000L, s.byCategory.single { it.category == Category.FOOD_DELIVERY }.spentPaise)
        // A refund bigger than the month's spend never makes a category negative.
        assertEquals(0L, engine.summary(listOf(refund), today).spentPaise)
    }

    @Test
    fun `split payments count as their parts`() {
        val amazon = txn(2000, Category.ONLINE_SHOPPING, merchant = "Amazon").copy(
            splits = listOf(SplitPart(60000, Category.QUICK_COMMERCE), SplitPart(140000, Category.ELECTRONICS, "Earbuds")),
        )
        val s = InsightsEngine(zone).summary(listOf(amazon), today)
        assertEquals(200000L, s.spentPaise)
        assertEquals(60000L, s.byCategory.single { it.category == Category.QUICK_COMMERCE }.spentPaise)
        assertTrue(s.byCategory.none { it.category == Category.ONLINE_SHOPPING })

        val stored = SplitCodec.encode(amazon.splits)
        val byId = Category.BUILT_IN.associateBy { it.id }
        assertEquals(amazon.splits, SplitCodec.decode(stored) { byId.getValue(it) })
        assertEquals(emptyList<SplitPart>(), SplitCodec.decode(null, CategoryCatalog()::byId))
    }

    @Test
    fun `late bank SMS for an app payment is flagged as a possible duplicate, distinct references are not`() {
        val notification = txn(450, Category.FOOD_DELIVERY, source = Source.NOTIFICATION).copy(id = 1, timestampMillis = at(5, 12))
        val lateSms = txn(450, Category.FOOD_DELIVERY).copy(timestampMillis = at(5, 13), reference = "628941075321")
        assertFalse(Deduplicator.isSamePayment(notification, lateSms))
        assertTrue(Deduplicator.isPossibleDuplicate(notification, lateSms))

        val a = lateSms.copy(id = 2)
        val b = lateSms.copy(reference = "628941075999")
        assertFalse(Deduplicator.isPossibleDuplicate(a, b))
        assertFalse(Deduplicator.isPossibleDuplicate(notification, lateSms.copy(timestampMillis = at(5, 16))))
    }

    @Test
    fun `bank messages we cannot read are reported, OTPs and promos are not`() {
        val odd = SmsParser.inspect("AX-HDFCBK", "Txn alert: INR 1,250.00 at QUIRKYSHOP via POS 4021 XX1234", 0L)
        assertTrue(odd is SmsParser.Reading.Unreadable)
        assertEquals(125000L, (odd as SmsParser.Reading.Unreadable).amountPaise)
        assertTrue(SmsParser.inspect("AX-HDFCBK", "123456 is your OTP for Rs.450 at SWIGGY", 0L) is SmsParser.Reading.Skipped)
        assertTrue(SmsParser.inspect("AX-SWIGGY", "Flat Rs.100 off today!", 0L) is SmsParser.Reading.Skipped)
        assertTrue(
            SmsParser.inspect("AX-HDFCBK", "Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 16/10/26 Ref 628941075321", 0L)
                is SmsParser.Reading.Read,
        )
    }

    @Test
    fun `renamed payees keep their name and rules for future payments`() {
        val parsed = SmsParser.parse("AX-HDFCBK", "Sent Rs.200.00 From HDFC Bank A/C *1234 To 9876543210@ybl On 16/10/26 Ref 628941075300", 0L)!!
        val default = Classifier().classify(parsed)
        assertEquals("UPI ··3210", default.merchant)
        val named = Classifier(rules = mapOf("mom" to Category.FAMILY), payeeNames = mapOf(default.merchantKey to "Mom")).classify(parsed)
        assertEquals("Mom", named.merchant)
        assertEquals(Category.FAMILY, named.category)
    }

    @Test
    fun `a refund SMS finds the order it reverses`() {
        val order = Classifier().classify(SmsParser.parse("AX-HDFCBK", "Sent Rs.612.00 From HDFC Bank A/C *1234 To SWIGGY On 03/10/26 Ref 628941075321", at(3))!!)
            .copy(category = Category.QUICK_COMMERCE)
        val refund = Classifier().classify(SmsParser.parse("AX-HDFCBK", "Rs.212.00 credited to A/c XX1234 on 04-10-26 towards refund from SWIGGY", at(4))!!)
        assertEquals(Category.REFUNDS, refund.category)
        val matched = Refunds.categorise(refund, listOf(order))
        assertEquals(Category.QUICK_COMMERCE, matched.category)
        assertTrue(matched.isRefund)
        // No earlier order: fall back to the merchant's usual category.
        assertEquals(Category.FOOD_DELIVERY, Refunds.categorise(refund, emptyList()).category)
    }
}
