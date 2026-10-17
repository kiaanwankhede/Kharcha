package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Representative alert formats for the major banks, with account numbers and references made up. */
class SmsParserTest {
    private val now = 1_792_000_000_000L

    private fun parse(sender: String, body: String) = SmsParser.parse(sender, body, now)

    @Test
    fun `HDFC UPI sent`() {
        val t = parse(
            "AX-HDFCBK-S",
            "Sent Rs.450.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 16/10/26\nRef 628941075321\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
        )!!
        assertEquals(45000, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("1234", t.accountLast4)
        assertEquals("HDFC", t.bank)
        assertEquals("628941075321", t.reference)
        assertEquals("SWIGGY", t.counterparty)
    }

    @Test
    fun `HDFC credited from VPA`() {
        val t = parse(
            "VM-HDFCBK",
            "Rs.1200.00 credited to HDFC Bank A/c XX1234 on 16-10-26 from VPA ravi.k@okaxis (UPI 628941075322)",
        )!!
        assertEquals(120000, t.amountPaise)
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals("ravi.k@okaxis", t.vpa)
        assertEquals("ravi.k@okaxis", t.counterparty)
        assertEquals("628941075322", t.reference)
    }

    @Test
    fun `HDFC credit card spend with balance`() {
        val t = parse(
            "AD-HDFCBK-T",
            "Spent Rs.649 On HDFC Bank Card 5678 At NETFLIX On 2026-10-16:09:12:01 Bal Rs.45,000.00 Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 5678 to 7308080808",
        )!!
        assertEquals(64900, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("5678", t.accountLast4)
        assertEquals("NETFLIX", t.counterparty)
        assertEquals(4500000, t.balancePaise)
    }

    @Test
    fun `SBI debit without currency marker`() {
        val t = parse(
            "JD-SBIUPI-S",
            "Dear UPI user A/C X1234 debited by 450.0 on date 16Oct26 trf to SWIGGY Refno 628941075321. If not u? call 1800111109. -SBI",
        )!!
        assertEquals(45000, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("1234", t.accountLast4)
        assertEquals("SBI", t.bank)
        assertEquals("SWIGGY", t.counterparty)
        assertEquals("628941075321", t.reference)
    }

    @Test
    fun `SBI credit`() {
        val t = parse(
            "VM-SBIUPI",
            "Dear SBI UPI User, ur A/cX1234 credited by Rs1200 on 16Oct26 by (Ref no 628941075322)",
        )!!
        assertEquals(120000, t.amountPaise)
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals("628941075322", t.reference)
    }

    @Test
    fun `ICICI payee credited`() {
        val t = parse(
            "AX-ICICIT-S",
            "ICICI Bank Acct XX234 debited for Rs 450.00 on 16-Oct-26; SWIGGY credited. UPI:628941075321. Call 18002662 for dispute. SMS BLOCK 234 to 9215676766.",
        )!!
        assertEquals(45000, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("234", t.accountLast4)
        assertEquals("ICICI", t.bank)
        assertEquals("SWIGGY", t.counterparty)
        assertEquals("628941075321", t.reference)
    }

    @Test
    fun `Axis P2M`() {
        val t = parse(
            "AX-AXISBK-S",
            "INR 450.00 debited\nA/c no. XX1234\n16-10-26, 20:42:11\nUPI/P2M/628941075321/SWIGGY\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
        )!!
        assertEquals(45000, t.amountPaise)
        assertEquals("Axis", t.bank)
        assertEquals("SWIGGY", t.counterparty)
        assertEquals("628941075321", t.reference)
    }

    @Test
    fun `Kotak sent to VPA`() {
        val t = parse(
            "VM-KOTAKB-S",
            "Sent Rs.450.00 from Kotak Bank AC X1234 to swiggy@icici on 16-10-26.UPI Ref 628941075321. Not you, https://kotak.com/KBANKT/Fraud",
        )!!
        assertEquals(45000, t.amountPaise)
        assertEquals("Kotak", t.bank)
        assertEquals("swiggy@icici", t.vpa)
        assertEquals("628941075321", t.reference)
    }

    @Test
    fun `ATM withdrawal is cash`() {
        val t = parse("AX-SBIINB", "Rs.2000 withdrawn at SBI ATM S1NW000123 from A/cX1234 on 16Oct26. Avl Bal Rs.15,234.50")!!
        assertTrue(t.isCashWithdrawal)
        assertEquals(200000, t.amountPaise)
        assertEquals(1523450, t.balancePaise)
    }

    @Test
    fun `credit card bill payment is own transfer`() {
        val t = parse("AX-HDFCBK", "Payment of Rs.18,420.00 received towards your HDFC Bank Credit Card ending 5678 on 16-10-26. Thank you.")!!
        assertTrue(t.isOwnTransfer)
    }

    @Test
    fun `ignores OTPs, requests, failures, reminders and personal senders`() {
        assertNull(parse("AX-HDFCBK", "123456 is your OTP for txn of Rs.450.00 at SWIGGY. Do not share."))
        assertNull(parse("AX-HDFCBK", "Ravi has requested money of Rs.500 from you on HDFC Bank UPI."))
        assertNull(parse("AX-ICICIT", "Your UPI txn of Rs 450.00 to SWIGGY has failed."))
        assertNull(parse("AX-HDFCBK", "Rs.649 will be debited on 21-10-26 towards NETFLIX mandate."))
        assertNull(parse("+919876543210", "Sent Rs.450 to you"))
        assertNull(parse("AX-SWIGGY", "Get 50% off up to Rs.100 on your next order!"))
    }

    @Test
    fun `bank sender check`() {
        assertTrue(SmsParser.isLikelyBankSender("AX-HDFCBK-S"))
        assertTrue(SmsParser.isLikelyBankSender("JD-SBIUPI"))
        assertTrue(!SmsParser.isLikelyBankSender("AX-SWIGGY"))
        assertTrue(!SmsParser.isLikelyBankSender("+919876543210"))
    }
}
