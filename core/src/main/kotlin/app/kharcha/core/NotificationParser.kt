package app.kharcha.core

/**
 * Reads payment confirmations posted as notifications by UPI apps. These catch payments where the
 * bank sends no SMS, and usually carry a nicer payee name than the bank does.
 */
object NotificationParser {

    val SUPPORTED_APPS = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
    )

    private const val AMOUNT = """(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private const val END = """(?=\s+(?:using|via|from|on|with)\b|[.!|]|$)"""

    private val DEBIT = listOf(
        Regex("""(?:you\s+)?(?:paid|sent)\s+$AMOUNT\s+(?:to|at)\s+(.+?)$END""", RegexOption.IGNORE_CASE),
        Regex("""$AMOUNT\s+(?:paid|sent)\s+(?:successfully\s+)?(?:to|at)\s+(.+?)$END""", RegexOption.IGNORE_CASE),
    )
    private val DEBIT_NO_PAYEE = Regex("""(?:you\s+)?(?:paid|sent)\s+$AMOUNT|$AMOUNT\s+(?:paid|sent)""", RegexOption.IGNORE_CASE)

    private val CREDIT = listOf(
        Regex("""received\s+$AMOUNT\s+from\s+(.+?)$END""", RegexOption.IGNORE_CASE),
        Regex("""$AMOUNT\s+received\s+from\s+(.+?)$END""", RegexOption.IGNORE_CASE),
    )
    private val CREDIT_SENDER_FIRST = Regex("""^(.+?)\s+(?:sent|paid)\s+you\s+$AMOUNT""", RegexOption.IGNORE_CASE)
    private val CREDIT_NO_PAYER = Regex("""received\s+$AMOUNT|$AMOUNT\s+received""", RegexOption.IGNORE_CASE)

    private val REJECT = Regex(
        """request|requested|failed|pending|declined|reminder|cashback|reward|offer|\bwon\b|scratch card|due\b|bill is""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(packageName: String, title: String?, text: String?, postedAtMillis: Long): ParsedTransaction? {
        val app = SUPPORTED_APPS[packageName] ?: return null
        val titleText = title?.trim().orEmpty()
        val bodyText = text?.trim().orEmpty()
        val combined = listOf(titleText, bodyText).filter { it.isNotEmpty() }.joinToString(". ")
            .replace(Regex("""\s+"""), " ")
        if (combined.isEmpty() || REJECT.containsMatchIn(combined)) return null

        fun build(direction: Direction, amount: String, counterparty: String?): ParsedTransaction? {
            val paise = Money.parsePaise(amount) ?: return null
            return ParsedTransaction(
                amountPaise = paise,
                direction = direction,
                timestampMillis = postedAtMillis,
                source = Source.NOTIFICATION,
                rawText = combined,
                sender = app,
                counterparty = counterparty?.trim()?.trim('.', '!', ',')?.takeIf { it.isNotEmpty() },
            )
        }

        for (pattern in DEBIT) {
            pattern.find(combined)?.let { return build(Direction.DEBIT, it.groupValues[1], it.groupValues[2]) }
        }
        for (pattern in CREDIT) {
            pattern.find(combined)?.let { return build(Direction.CREDIT, it.groupValues[1], it.groupValues[2]) }
        }
        CREDIT_SENDER_FIRST.find(combined)?.let {
            return build(Direction.CREDIT, it.groupValues[2], it.groupValues[1])
        }
        // Formats like title "Swiggy", text "You paid ₹450": the title is the other party.
        DEBIT_NO_PAYEE.find(bodyText)?.let {
            val amount = it.groupValues[1].ifEmpty { it.groupValues[2] }
            return build(Direction.DEBIT, amount, titleText.ifEmpty { null })
        }
        CREDIT_NO_PAYER.find(bodyText)?.let {
            val amount = it.groupValues[1].ifEmpty { it.groupValues[2] }
            return build(Direction.CREDIT, amount, titleText.ifEmpty { null })
        }
        return null
    }
}
