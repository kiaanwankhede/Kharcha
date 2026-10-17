package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationParserTest {
    private val gpay = "com.google.android.apps.nbu.paisa.user"
    private val phonepe = "com.phonepe.app"

    @Test
    fun `PhonePe paid`() {
        val t = NotificationParser.parse(phonepe, "Paid ₹450 to Swiggy", "Using HDFC Bank ••1234", 1L)!!
        assertEquals(45000, t.amountPaise)
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals("Swiggy", t.counterparty)
        assertEquals(Source.NOTIFICATION, t.source)
    }

    @Test
    fun `received money`() {
        val t = NotificationParser.parse(phonepe, "Received ₹1,200 from Ravi Kumar", null, 1L)!!
        assertEquals(120000, t.amountPaise)
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals("Ravi Kumar", t.counterparty)
    }

    @Test
    fun `title is payee`() {
        val t = NotificationParser.parse(gpay, "Chai Point", "You paid ₹60.00", 1L)!!
        assertEquals(6000, t.amountPaise)
        assertEquals("Chai Point", t.counterparty)
    }

    @Test
    fun `sender first credit`() {
        val t = NotificationParser.parse(gpay, "Ravi Kumar sent you ₹500", "Tap to view", 1L)!!
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals("Ravi Kumar", t.counterparty)
    }

    @Test
    fun `ignores requests, rewards and other apps`() {
        assertNull(NotificationParser.parse(gpay, "Ravi requested ₹500", "Pay now", 1L))
        assertNull(NotificationParser.parse(gpay, "You won ₹25 cashback", null, 1L))
        assertNull(NotificationParser.parse("com.whatsapp", "Paid ₹450 to Swiggy", null, 1L))
    }
}
