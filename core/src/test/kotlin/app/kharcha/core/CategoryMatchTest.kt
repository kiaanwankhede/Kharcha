package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CategoryMatchTest {
    private val food: Category = Category.mine("Food", "🍔")
    private val groceries: Category = Category.mine("Groceries", "🛒")
    private val petrol: Category = Category.mine("Petrol", "⛽")
    private val kids: Category = Category.mine("Kids school", "🎓")
    private val categories: List<Category> = listOf(food, groceries, petrol, kids)

    @Test
    fun `exact names match whatever the case or spacing`() {
        assertEquals(petrol, CategoryMatch.find("  PETROL ", categories).exact)
        assertEquals(kids, CategoryMatch.find("kids-school", categories).exact)
    }

    @Test
    fun `close names are suggested`() {
        assertEquals(listOf(groceries), CategoryMatch.find("grocery", categories).close) // singular
        assertEquals(listOf(groceries), CategoryMatch.find("grocries", categories).close) // typo
        assertEquals(listOf(petrol), CategoryMatch.find("pet", categories).close) // start of a name
        assertEquals(listOf(kids), CategoryMatch.find("school", categories).close) // a word in the name
        assertEquals(listOf(food), CategoryMatch.find("khana", categories).close) // same meaning
        assertEquals(listOf(petrol), CategoryMatch.find("diesel", categories).close)
    }

    @Test
    fun `nothing close means a new category`() {
        val result = CategoryMatch.find("Gym", categories)
        assertNull(result.exact)
        assertTrue(result.close.isEmpty())
        assertNull(CategoryMatch.find("", categories).exact)
    }

    @Test
    fun `best match for an old built-in name, and an emoji for new names`() {
        assertEquals(food, CategoryMatch.best("Food delivery", categories))
        assertEquals(null, CategoryMatch.best("Mobile recharge", categories))
        assertEquals("⛽", CategoryMatch.emojiFor("Petrol"))
        assertEquals("🏠", CategoryMatch.emojiFor("Ghar rent"))
        assertEquals("🏷️", CategoryMatch.emojiFor("Xyz"))
    }
}

class TagCategoryTest {
    @Test
    fun `a tag is a category keyed by its cleaned name, and tag budgets count every tagged payment`() {
        val food = Category.forTag(" Food ")
        assertEquals("tag:food", food.id)
        assertEquals("food", food.tagName)
        assertEquals("", food.emoji) // tags have no icon
        assertTrue(food.countsAsSpend)
        val catalog = CategoryCatalog(listOf(food.copy(emoji = "🍛")))
        assertEquals("", catalog.tag("FOOD").emoji)
        assertEquals(food, CategoryCatalog().tag("food"))

        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val day = java.time.LocalDate.of(2026, 10, 5).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        fun txn(rupees: Long, tags: List<String>) = Transaction(
            amountPaise = rupees * 100, direction = Direction.DEBIT, timestampMillis = day, merchant = "x", merchantKey = "x",
            category = catalog.tag(tags.first()), source = Source.SMS, rawText = "", tags = tags,
        )
        val budget = Budget(1, BudgetKind.CATEGORY, "tag:kids", "kids", "🧸", 500_000)
        val status = Budgets.status(listOf(budget), listOf(txn(1000, listOf("school", "kids")), txn(500, listOf("kids")), txn(300, listOf("food"))), java.time.LocalDate.of(2026, 10, 15))
        assertEquals(150_000L, status.single().spentPaise)
    }
}
