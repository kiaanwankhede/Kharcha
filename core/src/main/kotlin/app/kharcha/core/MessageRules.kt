package app.kharcha.core

/**
 * Patterns the user taught: "messages like this aren't money". A rule remembers the sender and the
 * words of the message (numbers, amounts, UPI IDs and account numbers removed), and hides later
 * messages from the same sender whose words are at least [SIMILARITY] alike.
 *
 * A rule made from a message that wasn't a payment (an offer, a reminder) never hides a message that
 * reads as a payment; only a rule made from a payment-looking message ([coversPayments]) can.
 */
data class MessageRule(
    val id: Long = 0,
    /** Sender code like "HDFCBK", or "*" for any sender. */
    val senderCode: String,
    val words: Set<String>,
    val coversPayments: Boolean,
    val sample: String,
    val createdAtMillis: Long,
)

object MessageRules {
    const val SIMILARITY = 0.8
    private const val MIN_WORDS = 4

    private val AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*[0-9][0-9,]*(?:\.[0-9]+)?""")
    private val VPA = Regex("""[a-z0-9._\-]+@[a-z][a-z0-9]+""")
    private val URL = Regex("""https?://\S+|www\.\S+""")
    private val MASKED = Regex("""[x*]+\d+""")

    fun senderCode(sender: String): String = SmsParser.senderCodeOf(sender) ?: sender.trim().uppercase()

    /** The message's stable words: lower case, with amounts, numbers, links, UPI IDs and accounts removed. */
    fun words(body: String): Set<String> = body.lowercase()
        .replace(URL, " ")
        .replace(AMOUNT, " ")
        .replace(VPA, " ")
        .replace(MASKED, " ")
        .replace(Regex("""\d+"""), " ")
        .replace(Regex("""[^\p{L} ]"""), " ")
        .split(' ')
        .filter { it.length > 1 }
        .toSet()

    fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }

    fun create(sender: String, body: String, coversPayments: Boolean, nowMillis: Long, anySender: Boolean = false) = MessageRule(
        senderCode = if (anySender) "*" else senderCode(sender),
        words = words(body),
        coversPayments = coversPayments,
        sample = body.replace(Regex("""\s+"""), " ").take(120),
        createdAtMillis = nowMillis,
    )

    /** [paymentLike] is whether the message reads as a payment. */
    fun matches(rule: MessageRule, sender: String, body: String, paymentLike: Boolean): Boolean {
        if (paymentLike && !rule.coversPayments) return false
        if (rule.senderCode != "*" && rule.senderCode != senderCode(sender)) return false
        val words = words(body)
        if (rule.words.size < MIN_WORDS || words.size < MIN_WORDS) return rule.words == words && words.isNotEmpty()
        return similarity(rule.words, words) >= SIMILARITY
    }

    fun encode(words: Set<String>): String = words.sorted().joinToString(" ")

    fun decode(text: String): Set<String> = text.split(' ').filter { it.isNotEmpty() }.toSet()
}
