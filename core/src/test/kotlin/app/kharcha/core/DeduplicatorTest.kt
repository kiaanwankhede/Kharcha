package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeduplicatorTest {
    private val classifier = Classifier()
    private val t0 = 1_792_000_000_000L

    private val sms = classifier.classify(
        SmsParser.parse("AX-HDFCBK", "Sent Rs.60.00 From HDFC Bank A/C *1234 To 9876543210@ybl On 16/10/26 Ref 628941075321", t0)!!,
    )
    private val notification = classifier.classify(
        NotificationParser.parse("com.phonepe.app", "Paid ₹60 to Chai Point", null, t0 + 40_000)!!,
    )

    @Test
    fun `bank SMS and app notification for one payment merge`() {
        assertTrue(Deduplicator.isSamePayment(sms, notification))
        val merged = Deduplicator.merge(sms, notification)
        assertEquals("Chai Point", merged.merchant)
        assertEquals("628941075321", merged.reference)
        assertEquals("1234", merged.accountLast4)
        assertEquals(Source.SMS, merged.source)
    }

    @Test
    fun `different references are different payments`() {
        val other = sms.copy(reference = "628941075999")
        assertFalse(Deduplicator.isSamePayment(sms, other))
    }

    @Test
    fun `same reference is the same payment`() {
        assertTrue(Deduplicator.isSamePayment(sms, sms.copy(timestampMillis = t0 + 86_400_000)))
    }

    @Test
    fun `two notifications of the same amount are kept apart`() {
        val second = notification.copy(timestampMillis = notification.timestampMillis + 60_000, rawText = "Paid ₹60 to Chai Point.")
        assertFalse(Deduplicator.isSamePayment(notification, second))
    }

    @Test
    fun `notification too long after the SMS is a new payment`() {
        val late = notification.copy(timestampMillis = t0 + 30 * 60_000)
        assertFalse(Deduplicator.isSamePayment(sms, late))
    }
}
