package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClassifierTest {
    private val classifier = Classifier()

    private fun sms(body: String, sender: String = "AX-HDFCBK") =
        classifier.classify(SmsParser.parse(sender, body, 0L)!!)

    @Test
    fun `known brand from payee name`() {
        val t = sms("Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY LIMITED On 16/10/26 Ref 628941075321")
        assertEquals("Swiggy", t.merchant)
        assertEquals(Category.FOOD, t.category)
    }

    @Test
    fun `known brand from VPA`() {
        val t = sms("Sent Rs.299.00 from Kotak Bank AC X1234 to blinkit.payu@hdfcbank on 16-10-26.UPI Ref 628941075399.", "VM-KOTAKB")
        assertEquals("Blinkit", t.merchant)
        assertEquals(Category.GROCERIES, t.category)
    }

    @Test
    fun `phone number VPA is a person`() {
        val t = sms("Sent Rs.200.00 From HDFC Bank A/C *1234 To 9876543210@ybl On 16/10/26 Ref 628941075300")
        assertEquals(Category.PEOPLE, t.category)
        assertEquals("UPI ··3210", t.merchant)
    }

    @Test
    fun `named person on a personal handle`() {
        val t = sms("Rs.150.00 debited from HDFC Bank A/c XX1234 to VPA ravi.kumar@okaxis on 16-10-26 Ref 628941075301")
        assertEquals(Category.PEOPLE, t.category)
        assertEquals("Ravi Kumar", t.merchant)
    }

    @Test
    fun `merchant QR without a name`() {
        val t = sms("Sent Rs.40.00 From HDFC Bank A/C *1234 To paytmqr28100505010@paytm On 16/10/26 Ref 628941075302")
        assertEquals("UPI merchant", t.merchant)
        assertEquals(Category.OTHER, t.category)
    }

    @Test
    fun `credits are income and card bill payments are transfers`() {
        assertEquals(Category.INCOME, sms("Rs.1200.00 credited to HDFC Bank A/c XX1234 on 16-10-26 from VPA ravi@okaxis (UPI 628941075322)").category)
        assertEquals(
            Category.TRANSFER,
            sms("Payment of Rs.18,420.00 received towards your HDFC Bank Credit Card ending 5678 on 16-10-26.").category,
        )
    }

    @Test
    fun `user rule wins`() {
        val parsed = SmsParser.parse("AX-HDFCBK", "Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 16/10/26 Ref 628941075321", 0L)!!
        val t = Classifier(mapOf("swiggy" to Category.ENTERTAINMENT)).classify(parsed)
        assertEquals(Category.ENTERTAINMENT, t.category)
    }

    @Test
    fun `tidy names`() {
        assertEquals("Swiggy", Merchants.tidy("SWIGGY LIMITED"))
        assertEquals("Ravi Kumar", Merchants.tidy("ravi kumar"))
        assertEquals("Chai Point", Merchants.tidy("CHAI POINT PVT LTD"))
    }
}
