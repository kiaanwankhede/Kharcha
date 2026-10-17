package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SpeedTest {
    private fun classify(body: String, rules: Map<String, Category> = emptyMap()) =
        Classifier(rules).classify(SmsParser.parse("AX-HDFCBK", body, 0L)!!)

    @Test
    fun `confidence reflects how the category was chosen`() {
        assertEquals(Confidence.SURE, classify("Sent Rs.299.00 From HDFC Bank A/C *1234 To NETFLIX On 16/10/26 Ref 628941075321").confidence)
        assertEquals(Confidence.GUESS, classify("Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 16/10/26 Ref 628941075322").confidence)
        assertEquals(Confidence.LIKELY, classify("Sent Rs.80.00 From HDFC Bank A/C *1234 To SHARMA TEA STALL On 16/10/26 Ref 628941075323").confidence)
        assertEquals(Confidence.GUESS, classify("Sent Rs.120.00 From HDFC Bank A/C *1234 To 9876543210@ybl On 16/10/26 Ref 628941075324").confidence)
        assertEquals(Confidence.GUESS, classify("Sent Rs.640.00 From HDFC Bank A/C *1234 To QWERTY ENTERPRISES On 16/10/26 Ref 628941075325").confidence)
        val ruled = classify("Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 16/10/26 Ref 628941075326", mapOf("swiggy" to Category.QUICK_COMMERCE))
        assertEquals(Category.QUICK_COMMERCE, ruled.category)
        assertEquals(Confidence.SURE, ruled.confidence)
    }
}
