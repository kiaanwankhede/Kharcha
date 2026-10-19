package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class ReviewFixesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 10, 5).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun txn(rupees: Long, category: Category, tags: List<String> = emptyList(), source: Source = Source.SMS, direction: Direction = Direction.DEBIT) = Transaction(
        amountPaise = rupees * 100, direction = direction, timestampMillis = day, merchant = "x", merchantKey = "x",
        category = category, source = source, rawText = "", tags = tags,
    )

    @Test
    fun `split parts carry only their own tag, so a tag budget counts the part, not the whole`() {
        val food = Category.forTag("food")
        val gadgets = Category.forTag("gadgets")
        val paid = txn(1000, food, tags = listOf("food")).copy(splits = listOf(SplitPart(30000, food), SplitPart(70000, gadgets)))
        val status = Budgets.status(listOf(Budget(1, BudgetKind.CATEGORY, food.id, "food", "", 500_000)), listOf(paid), LocalDate.of(2026, 10, 15))
        assertEquals(30_000L, status.single().spentPaise)
    }

    @Test
    fun `merging an SMS with its notification keeps tags from both`() {
        val sms = txn(250, Category.OTHER)
        val notification = txn(250, Category.OTHER, tags = listOf("food"), source = Source.NOTIFICATION)
        assertEquals(listOf("food"), Deduplicator.merge(sms, notification).tags)
    }

    @Test
    fun `a remembered type only applies the way its money moves`() {
        assertEquals(Direction.DEBIT, Category.LENT.onlyFor)
        assertEquals(Direction.CREDIT, Category.SALARY.onlyFor)
        assertNull(Category.TRANSFER.onlyFor)
        assertNull(Category.forTag("food").onlyFor)
        val classifier = Classifier(mapOf(Merchants.keyOf("Rahul Sharma") to Category.LENT))
        val repaid = ParsedTransaction(
            amountPaise = 50000, direction = Direction.CREDIT, timestampMillis = day, source = Source.SMS, rawText = "",
            sender = "AX-HDFCBK", counterparty = "Rahul Sharma",
        )
        assert(classifier.classify(repaid).category != Category.LENT)
        assertEquals(Category.LENT, classifier.classify(repaid.copy(direction = Direction.DEBIT)).category)
    }
}
