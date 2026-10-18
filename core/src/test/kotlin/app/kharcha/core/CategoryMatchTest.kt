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
