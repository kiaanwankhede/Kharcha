package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageRulesTest {
    private val promo = "Dear Customer, you are eligible for a pre-approved Personal Loan of Rs.5,00,000 at 10.5% p.a. Apply at https://hdfc.bank/pl123 T&C"
    private val promoAgain = "Dear Customer, you are eligible for a pre-approved Personal Loan of Rs.3,25,000 at 11% p.a. Apply at https://hdfc.bank/pl987 T&C"
    private val other = "Your HDFC Bank credit card statement is ready. Login to NetBanking to view it."
    private val payment = "Rs.1200.00 credited to HDFC Bank A/c XX1234 on 16-10-26 from VPA ravi.k@okaxis (UPI 628941075322)"
    private val payment2 = "Rs.86000.00 credited to HDFC Bank A/c XX1234 on 01-10-26 from VPA amit@oksbi (UPI 628941075999)"

    @Test
    fun `a learned offer hides the next offer like it, not other messages`() {
        val rule = MessageRules.create("AX-HDFCBK", promo, coversPayments = false, nowMillis = 0)
        assertTrue(MessageRules.matches(rule, "VM-HDFCBK-S", promoAgain, paymentLike = false))
        assertFalse(MessageRules.matches(rule, "AX-HDFCBK", other, paymentLike = false))
        assertFalse(MessageRules.matches(rule, "AX-ICICIB", promoAgain, paymentLike = false))
    }

    @Test
    fun `rules from non-payments never hide payments`() {
        val fromPromo = MessageRules.create("AX-HDFCBK", payment, coversPayments = false, nowMillis = 0)
        assertFalse(MessageRules.matches(fromPromo, "AX-HDFCBK", payment2, paymentLike = true))
        val fromPayment = MessageRules.create("AX-HDFCBK", payment, coversPayments = true, nowMillis = 0)
        assertTrue(MessageRules.matches(fromPayment, "AX-HDFCBK", payment2, paymentLike = true))
    }

    @Test
    fun `OTPs are recognised so they are never stored`() {
        assertTrue(SmsParser.isOtp("123456 is your OTP for txn of Rs.450"))
        assertFalse(SmsParser.isOtp(promo))
    }

    @Test
    fun `rule words round trip`() {
        val words = MessageRules.words(promo)
        assertTrue(MessageRules.decode(MessageRules.encode(words)) == words)
        assertFalse("hdfc" in words && words.any { it.any(Char::isDigit) })
    }
}
