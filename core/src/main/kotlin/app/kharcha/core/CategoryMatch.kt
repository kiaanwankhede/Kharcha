package app.kharcha.core

/** What a typed category name matches among the user's categories. */
data class CategoryMatches(
    /** Same name, ignoring case, spaces and punctuation. */
    val exact: Category?,
    /** Close names worth suggesting ("Grocery" for "groceries", "Food" for "khana"), best first. */
    val close: List<Category>,
)

/**
 * Finds the user's categories that match what they type, so "petrol" picks "Petrol", "grocery"
 * suggests "Groceries", and "khana" suggests "Food". Nothing here is a fixed category list: it only
 * compares names.
 */
object CategoryMatch {
    /** Words that mean the same thing for spending, in English and Hindi. Each set is one meaning. */
    private val SYNONYMS: List<Set<String>> = listOf(
        setOf("food", "khana", "khaana", "eating", "eatingout", "restaurant", "restaurants", "dining", "meal", "meals", "swiggy", "zomato"),
        setOf("grocery", "groceries", "kirana", "ration", "provisions", "supermarket"),
        setOf("vegetables", "veggies", "sabzi", "sabji", "fruits"),
        setOf("rent", "kiraya", "houserent"),
        setOf("home", "house", "ghar", "household"),
        setOf("fuel", "petrol", "diesel", "cng"),
        setOf("travel", "trip", "trips", "holiday", "holidays", "vacation", "safar"),
        setOf("cab", "cabs", "taxi", "auto", "uber", "ola", "rapido", "commute", "transport"),
        setOf("medicine", "medicines", "dawai", "pharmacy", "medical", "health", "doctor"),
        setOf("kids", "children", "child", "baby", "bachche"),
        setOf("school", "fees", "schoolfees", "tuition", "education"),
        setOf("parents", "family", "mummy", "papa", "mom", "dad", "ghar wale"),
        setOf("shopping", "clothes", "kapde", "amazon", "flipkart", "myntra"),
        setOf("bills", "utilities", "electricity", "bijli", "recharge", "mobile", "internet", "wifi"),
        setOf("maid", "bai", "househelp", "cook", "driver", "servant"),
        setOf("entertainment", "movies", "movie", "ott", "netflix", "fun"),
        setOf("gifts", "gift", "shagun"),
        setOf("puja", "pooja", "temple", "mandir", "donation", "charity"),
        setOf("sip", "investment", "investments", "mutualfund", "mutualfunds", "savings"),
        setOf("emi", "loan", "loans"),
        setOf("office", "work", "business"),
        setOf("snacks", "chai", "tea", "coffee", "cafe"),
        setOf("alcohol", "drinks", "daaru", "beer", "liquor"),
    )

    fun normalise(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun meanings(text: String): Set<Int> {
        val keys = words(text) + normalise(text)
        return SYNONYMS.indices.filter { i -> keys.any { it in SYNONYMS[i] } }.toSet()
    }

    /** Edit distance, for typos and singular/plural ("grocery" vs "groceries" is 3). */
    internal fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }

    /** How close [label] is to [query]: lower is closer, null is not close at all. */
    private fun score(query: String, label: String): Int? {
        val q = normalise(query)
        val l = normalise(label)
        if (q.isEmpty() || l.isEmpty()) return null
        if (q.length >= 2 && (l.startsWith(q) || q.startsWith(l))) return 1
        if (q.length >= 3 && (l.contains(q) || q.contains(l))) return 2
        if (q.length >= 3 && words(label).any { it.startsWith(q) }) return 2
        val allowed = maxOf(1, minOf(q.length, l.length) / 3)
        val d = distance(q, l)
        if (q.length >= 3 && d <= allowed) return 3 + d
        if (meanings(query).intersect(meanings(label)).isNotEmpty()) return 6
        return null
    }

    fun find(query: String, categories: List<Category>, limit: Int = 4): CategoryMatches {
        val q = normalise(query)
        if (q.isEmpty()) return CategoryMatches(null, emptyList())
        val exact = categories.firstOrNull { normalise(it.label) == q }
        val close = categories.asSequence()
            .filter { it.id != exact?.id }
            .mapNotNull { c -> score(query, c.label)?.let { c to it } }
            .sortedWith(compareBy({ it.second }, { it.first.label.length }))
            .map { it.first }
            .take(limit)
            .toList()
        return CategoryMatches(exact, close)
    }

    /** The single best category for a name (an exact match, else the closest), or null. */
    fun best(name: String, categories: List<Category>): Category? = find(name, categories, limit = 1).let { it.exact ?: it.close.firstOrNull() }

    private val EMOJI: List<Pair<Set<String>, String>> = listOf(
        setOf("food", "khana", "khaana", "restaurant", "dining", "meal", "swiggy", "zomato", "eating") to "🍔",
        setOf("grocery", "groceries", "kirana", "ration", "supermarket") to "🛒",
        setOf("vegetables", "veggies", "sabzi", "sabji", "fruits") to "🥦",
        setOf("milk", "dairy", "doodh") to "🥛",
        setOf("rent", "kiraya", "home", "house", "ghar") to "🏠",
        setOf("fuel", "petrol", "diesel", "cng") to "⛽",
        setOf("travel", "trip", "holiday", "vacation", "flight", "flights") to "✈️",
        setOf("cab", "cabs", "taxi", "auto", "uber", "ola", "commute", "transport", "metro") to "🚕",
        setOf("medicine", "medicines", "dawai", "pharmacy", "medical", "health", "doctor", "hospital") to "💊",
        setOf("kids", "children", "child", "baby", "toys") to "🧸",
        setOf("school", "fees", "tuition", "education", "books", "course") to "🎓",
        setOf("parents", "family", "mummy", "papa", "mom", "dad") to "👪",
        setOf("shopping", "clothes", "kapde", "amazon", "flipkart", "myntra") to "🛍️",
        setOf("electricity", "bijli", "bills", "utilities") to "💡",
        setOf("recharge", "mobile", "phone", "internet", "wifi") to "📱",
        setOf("maid", "bai", "cook", "driver", "help") to "🧹",
        setOf("movies", "movie", "ott", "netflix", "entertainment", "fun") to "🎬",
        setOf("gifts", "gift", "shagun") to "🎁",
        setOf("puja", "pooja", "temple", "mandir") to "🪔",
        setOf("donation", "charity") to "💝",
        setOf("sip", "investment", "investments", "savings", "mutual") to "📈",
        setOf("emi", "loan", "loans") to "🏦",
        setOf("office", "work", "business") to "💼",
        setOf("snacks", "chai", "tea", "coffee", "cafe") to "☕",
        setOf("alcohol", "drinks", "daaru", "beer") to "🍺",
        setOf("gym", "fitness", "sports", "yoga") to "🏋️",
        setOf("pet", "pets", "dog", "cat") to "🐶",
        setOf("salon", "haircut", "beauty", "grooming") to "💇",
        setOf("insurance") to "🛡️",
        setOf("car", "bike", "vehicle", "service", "repair") to "🔧",
        setOf("subscription", "subscriptions") to "🔁",
    )

    /** A fitting emoji for a new category's name, or a plain tag. */
    fun emojiFor(name: String): String {
        val keys = words(name) + normalise(name)
        return EMOJI.firstOrNull { (set, _) -> keys.any { it in set } }?.second ?: "🏷️"
    }
}
