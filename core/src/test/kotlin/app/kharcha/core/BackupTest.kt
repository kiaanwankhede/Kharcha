package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackupTest {
    private val txn = Transaction(
        amountPaise = 45000, direction = Direction.DEBIT, timestampMillis = 1_792_000_000_000L,
        merchant = "Swiggy", merchantKey = "swiggy", category = Category.FOOD, source = Source.SMS,
        rawText = "Sent Rs.450.00 To SWIGGY", accountLast4 = "1234", reference = "628941075321",
    )
    private val payload = BackupPayload(
        createdAtMillis = 1L,
        transactions = listOf(txn),
        merchantRules = mapOf("swiggy" to Category.FOOD),
        monthlyLimitPaise = 30_000_00,
    )

    @Test
    fun `round trip`() {
        val bytes = BackupCodec.encode(payload, "correct horse".toCharArray())
        assertEquals(payload, BackupCodec.decode(bytes, "correct horse".toCharArray()))
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("SWIGGY"))
    }

    @Test
    fun `wrong passphrase and junk files are rejected`() {
        val bytes = BackupCodec.encode(payload, "correct horse".toCharArray())
        assertThrows<WrongPassphraseException> { BackupCodec.decode(bytes, "wrong".toCharArray()) }
        assertThrows<NotABackupException> { BackupCodec.decode("hello".toByteArray(), "x".toCharArray()) }
    }

    @Test
    fun `csv escapes and neutralises formulas`() {
        val csv = CsvExporter.export(listOf(txn.copy(merchant = "=HYPERLINK(\"x\")", note = "a, b")))
        val row = csv.lines()[1]
        assertEquals("2026-10-14,23:16:40,450.00,Debit,\"'=HYPERLINK(\"\"x\"\")\",Food,,1234,628941075321,\"a, b\"", row)
    }

    @Test
    fun `money formatting uses Indian grouping`() {
        assertEquals("₹42,380", Money.format(4238000))
        assertEquals("₹1,23,45,678.50", Money.format(1234567850))
        assertEquals("₹450.00", Money.format(45000, showPaise = true))
        assertEquals(123450L, Money.parsePaise("1,234.50"))
    }
}
