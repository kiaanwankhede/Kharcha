package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClassifierTest {
    private val classifier = Classifier()

    private fun sms(body: String, sender: String = "AX-HDFCBK") =
        classifier.classify(SmsParser.parse(sender, body, 0L)!!)

    @Test
    fun `known brand from payee name`() {
        val t = sms("Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY LIMITED On 16/10/26 Ref 628941075321")
        assertEquals("Swiggy", t.merchant)
        assertEquals(Category.FOOD_DELIVERY, t.category)
    }

    @Test
    fun `known brand from VPA`() {
        val t = sms("Sent Rs.299.00 from Kotak Bank AC X1234 to blinkit.payu@hdfcbank on 16-10-26.UPI Ref 628941075399.", "VM-KOTAKB")
        assertEquals("Blinkit", t.merchant)
        assertEquals(Category.QUICK_COMMERCE, t.category)
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
        assertEquals(Category.RECEIVED, sms("Rs.1200.00 credited to HDFC Bank A/c XX1234 on 16-10-26 from VPA ravi@okaxis (UPI 628941075322)").category)
        assertEquals(
            Category.CARD_BILL,
            sms("Payment of Rs.18,420.00 received towards your HDFC Bank Credit Card ending 5678 on 16-10-26.").category,
        )
    }

    @Test
    fun `user rule wins`() {
        val parsed = SmsParser.parse("AX-HDFCBK", "Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 16/10/26 Ref 628941075321", 0L)!!
        val t = Classifier(mapOf("swiggy" to Category.MOVIES)).classify(parsed)
        assertEquals(Category.MOVIES, t.category)
    }

    @Test
    fun `tidy names`() {
        assertEquals("Swiggy", Merchants.tidy("SWIGGY LIMITED"))
        assertEquals("Ravi Kumar", Merchants.tidy("ravi kumar"))
        assertEquals("Chai Point", Merchants.tidy("CHAI POINT PVT LTD"))
    }

    @Test
    fun `credits are sorted into salary, refunds and interest`() {
        assertEquals(Category.SALARY, sms("Rs.85,000.00 credited to A/c XX1234 on 01-10-26 by NEFT SALARY OCT ACME TECH").category)
        assertEquals(Category.REFUNDS, sms("Rs.450.00 credited to A/c XX1234 on 02-10-26 towards refund from SWIGGY").category)
        assertEquals(Category.INTEREST, sms("Rs.312.00 credited to A/c XX1234 on 30-09-26 as interest paid for the quarter").category)
    }

    @Test
    fun `everyday Indian payees land in the right subcategory`() {
        assertEquals(Category.MILK, sms("Sent Rs.1,240.00 From HDFC Bank A/C *1234 To COUNTRY DELIGHT On 01/10/26 Ref 628941075500").category)
        assertEquals(Category.HOME_SERVICES, sms("Sent Rs.499.00 From HDFC Bank A/C *1234 To URBAN COMPANY On 01/10/26 Ref 628941075501").category)
        assertEquals(Category.PUJA, sms("Sent Rs.501.00 From HDFC Bank A/C *1234 To SHRI SAI MANDIR TRUST On 01/10/26 Ref 628941075502").category)
        assertEquals(Category.STOCKS, sms("Sent Rs.10,000.00 From HDFC Bank A/C *1234 To ZERODHA BROKING On 01/10/26 Ref 628941075503").category)
        assertEquals(Category.G_FOOD, Category.groupOf(Category.FOOD_DELIVERY))
    }

    @Test
    fun `old flat categories move into the tree, by merchant where possible`() {
        val old = sms("Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 16/10/26 Ref 628941075321")
        assertEquals(Category.FOOD_DELIVERY, Classifier.recategorise(old, "FOOD").category)
        assertEquals(Category.RESTAURANTS, Classifier.recategorise(old.copy(merchant = "Hotel Annapurna", vpa = null), "FOOD").category)
        assertEquals(Category.CASH, Classifier.recategorise(old, "CASH").category)
        assertEquals(Category.RESTAURANTS, CategoryCatalog().byId("FOOD"))
    }

    @Test
    fun `Swiggy can be food or Instamart, and Instamart is recognised when the SMS says so`() {
        val food = sms("Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 16/10/26 Ref 628941075321")
        assertEquals(Category.FOOD_DELIVERY, food.category)
        val brand = Merchants.brandOf(food)!!
        assertTrue(brand.isMultiBusiness)
        assertTrue(Category.QUICK_COMMERCE in brand.choices)

        val instamart = sms("Sent Rs.612.00 from Kotak Bank AC X1234 to swiggyinstamart@icici on 16-10-26.UPI Ref 628941075322.", "VM-KOTAKB")
        assertEquals(Category.QUICK_COMMERCE, instamart.category)
        assertEquals("Swiggy Instamart", instamart.merchant)
    }
}
