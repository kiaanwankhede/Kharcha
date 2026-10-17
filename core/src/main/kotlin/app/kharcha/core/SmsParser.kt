package app.kharcha.core

/**
 * Reads bank and UPI transaction alerts sent as SMS by Indian banks.
 *
 * Rather than one template per bank, it looks for the pieces every alert carries (an amount,
 * a debit/credit verb, a masked account, a UPI reference, a payee) so that small wording changes
 * by a bank do not break it. Bank-specific quirks are handled as extra patterns.
 */
object SmsParser {

    private const val AMOUNT_NUMBER = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val CURRENCY_AMOUNT = Regex("""(?:\brs\.?|\binr|₹)\s*$AMOUNT_NUMBER""", RegexOption.IGNORE_CASE)
    // SBI writes "debited by 450.0" with no currency marker.
    private val VERB_AMOUNT = Regex(
        """(?:debited|credited)\s+(?:by|with|for)\s+(?:rs\.?|inr|₹)?\s*$AMOUNT_NUMBER""",
        RegexOption.IGNORE_CASE,
    )
    private val BALANCE_HINT = Regex("""(?:bal|balance|avl|available|limit|amt due|amount due)\W{0,12}$""", RegexOption.IGNORE_CASE)

    private val DEBIT_WORDS = Regex(
        """\b(?:debited|spent|paid|sent|withdrawn|deducted|purchase|txn of|payment of|transferred|dr)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val CREDIT_WORDS = Regex("""\b(?:credited|received|deposited|refunded|refund|cr)\b""", RegexOption.IGNORE_CASE)

    private val REJECT = Regex(
        listOf(
            """\botp\b""", "one.time.password", "verification code", """\bfailed\b""", """\bdeclined\b""",
            """\bunsuccessful\b""", "has requested", "collect request", "requested money", "will be debited",
            "will be deducted", "is due", "due date", "min(?:imum)? amount due", "pre-approved", "apply now",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )

    private val MASKED_ACCOUNT = Regex("""[x*]+\s?(\d{3,4})(?!\d)""", RegexOption.IGNORE_CASE)
    private val PLAIN_ACCOUNT = Regex("""\b(?:a/?c|acct|account|card)\b\s*(?:no\.?)?\s*(?:ending(?: with)?)?\s*[:\-]?\s*(\d{4})(?!\d)""", RegexOption.IGNORE_CASE)

    private val VPA = Regex("""(?<![\w.\-])([a-z0-9][a-z0-9._\-]*@[a-z][a-z0-9]+)(?![\w.])""", RegexOption.IGNORE_CASE)

    private val REFERENCES = listOf(
        Regex("""upi/p2[am]/(\d{9,})""", RegexOption.IGNORE_CASE),
        Regex(
            """(?:upi\s*ref(?:erence)?(?:\s*no)?|ref(?:erence)?\s*no|refno|ref|utr(?:\s*no)?|upi(?:\s*id)?)[\s:#.\-]*(\d{9,})""",
            RegexOption.IGNORE_CASE,
        ),
    )

    private val DEBIT_PAYEE = listOf(
        Regex("""upi/p2[am]/\d+/([^/]+?)(?:/|\s+not you|\s*$)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+vpa\s+([\w.\-]+@\w+)""", RegexOption.IGNORE_CASE),
        Regex(""";\s*([^;]+?)\s+credited\b""", RegexOption.IGNORE_CASE),
        Regex("""\btrf to\s+(.+?)(?=\s+ref|\s+on\b|\.|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+(.+?)(?=\s+on\b|\s+via\b|\.\s|\s+bal\b|\s+avl\b|\s+not you|$)""", RegexOption.IGNORE_CASE),
        Regex("""\btowards\s+(.+?)(?=\s+on\b|\s+ref|\.\s|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bto\s+(.+?)(?=\s+on\b|\s+ref|\s*\(|\.\s|\.$|\s+upi\b|\s+not you|$)""", RegexOption.IGNORE_CASE),
    )

    private val CREDIT_PAYER = listOf(
        Regex("""upi/p2[am]/\d+/([^/]+?)(?:/|\s+not you|\s*$)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+vpa\s+([\w.\-]+@\w+)""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s+(.+?)(?=\s+on\b|\s*\(|\s+ref|\.\s|\.$|\s+upi\b|$)""", RegexOption.IGNORE_CASE),
        Regex("""\bby\s+(.+?)(?=\s+on\b|\s*\(|\s+ref|\.\s|\.$|$)""", RegexOption.IGNORE_CASE),
    )

    /** Words that mean a candidate payee is really our own account or bank, not the other party. */
    private val NOT_A_PAYEE = Regex(
        """\b(?:a/?c|acct|account|your|bank card|credit card|debit card|card no|atm)\b|^[x*]*\d+$""",
        RegexOption.IGNORE_CASE,
    )

    private val OWN_TRANSFER = Regex(
        listOf(
            """payment\b.{0,40}\b(?:received|credited)\b.{0,40}\bcard""",
            """towards your\b.{0,30}\bcard""",
            """\bcc payment\b""",
            """credit card bill""",
            """received\b.{0,30}\bpayment\b.{0,40}\bcard""",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )
    private val WALLET_TOP_UP = Regex(
        listOf(
            """add(?:ed)?\s*money""", """load(?:ed)?\s*money""", """\bwallet\b""", """upi\s*lite""", """\blite\s*(?:top|load)""",
            """amazon\s*pay\s*balance""", """mobikwik""", """freecharge""", """ola\s*money""", """paytm\s*(?:wallet|balance)""",
            """add-?money@""", """addmoney""", """paytmwallet""", """phonepewallet""", """amazonpaywallet""",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )
    // "Paid from your Paytm wallet" is spending out of the wallet, not money going into it.
    private val PAID_FROM_WALLET = Regex(
        """(?:from|using|via)\s+(?:your\s+)?(?:paytm\s+|amazon\s*pay\s+|phonepe\s+|mobikwik\s+)?(?:wallet|upi\s*lite)""",
        RegexOption.IGNORE_CASE,
    )
    private val CASH = Regex("""\batm\b|cash withdrawal|withdrawn at""", RegexOption.IGNORE_CASE)

    private val SENDER_PREFIX = Regex("""^[A-Z]{2}-""")
    private val SENDER_SUFFIX = Regex("""-[A-Z]$""")
    private val PHONE_SENDER = Regex("""^\+?\d{6,}$""")

    /** DLT sender codes (the part after "AX-") mapped to bank names. */
    private val BANKS = linkedMapOf(
        "HDFC" to "HDFC", "SBI" to "SBI", "ICICI" to "ICICI", "AXIS" to "Axis", "KOTAK" to "Kotak",
        "PNB" to "PNB", "BOB" to "Bank of Baroda", "CANBNK" to "Canara", "UNIONB" to "Union Bank",
        "IDFC" to "IDFC First", "INDUS" to "IndusInd", "YESB" to "Yes Bank", "FEDBNK" to "Federal",
        "AUBANK" to "AU Bank", "PAYTMB" to "Paytm Payments Bank", "AIRBNK" to "Airtel Payments Bank",
        "SCBANK" to "Standard Chartered", "CITI" to "Citi", "HSBC" to "HSBC", "RBL" to "RBL",
        "IDBI" to "IDBI", "BOI" to "Bank of India", "CBSSBI" to "SBI", "JUPITER" to "Jupiter",
        "FIMONY" to "Fi", "ONECRD" to "OneCard", "SLICE" to "slice",
    )
    private val BANKISH_SENDER = Regex("""BK|BNK|BANK|UPI|TXN|CARD|CRD""")

    /** Cheap check on the sender alone, so the app never reads personal messages in full. */
    fun isLikelyBankSender(sender: String): Boolean {
        val code = senderCode(sender) ?: return false
        return bankFor(code) != null || BANKISH_SENDER.containsMatchIn(code)
    }

    /** What happened to one message: read, deliberately skipped, or a bank message we could not read. */
    sealed interface Reading {
        data class Read(val parsed: ParsedTransaction) : Reading
        data object Skipped : Reading
        /** From a bank, mentions money, but the format is not understood. [amountPaise] is a best guess. */
        data class Unreadable(val amountPaise: Long?) : Reading
    }

    fun inspect(sender: String, body: String, receivedAtMillis: Long): Reading {
        if (!isLikelyBankSender(sender)) return Reading.Skipped
        val text = body.replace(Regex("""\s+"""), " ").trim()
        if (REJECT.containsMatchIn(text)) return Reading.Skipped
        parse(sender, body, receivedAtMillis)?.let { return Reading.Read(it) }
        val amount = CURRENCY_AMOUNT.find(text)?.let { Money.parsePaise(it.groupValues[1]) } ?: return Reading.Skipped
        return Reading.Unreadable(amount)
    }

    fun parse(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        if (!isLikelyBankSender(sender)) return null
        val text = body.replace(Regex("""\s+"""), " ").trim()
        if (REJECT.containsMatchIn(text)) return null

        val direction = direction(text) ?: return null
        val amount = amount(text) ?: return null
        val vpa = VPA.find(text)?.groupValues?.get(1)?.lowercase()
        val isOwnTransfer = OWN_TRANSFER.containsMatchIn(text)
        val isCash = direction == Direction.DEBIT && CASH.containsMatchIn(text)
        val isWalletTopUp = direction == Direction.DEBIT && !isCash && WALLET_TOP_UP.containsMatchIn(text) && !PAID_FROM_WALLET.containsMatchIn(text)

        return ParsedTransaction(
            amountPaise = amount,
            direction = direction,
            timestampMillis = receivedAtMillis,
            source = Source.SMS,
            rawText = body,
            sender = sender,
            counterparty = if (isCash) "ATM" else counterparty(text, direction),
            vpa = vpa,
            accountLast4 = account(text),
            bank = senderCode(sender)?.let(::bankFor),
            reference = reference(text),
            balancePaise = balance(text),
            isOwnTransfer = isOwnTransfer,
            isCashWithdrawal = isCash,
            isWalletTopUp = isWalletTopUp,
        )
    }

    /** The bank name for a sender like "AX-HDFCBK-S", if it is a known bank. */
    fun bankOf(sender: String): String? = senderCode(sender)?.let(::bankFor)

    private fun senderCode(sender: String): String? {
        val trimmed = sender.trim().uppercase()
        if (trimmed.isEmpty() || PHONE_SENDER.matches(trimmed)) return null
        return trimmed.replace(SENDER_PREFIX, "").replace(SENDER_SUFFIX, "")
    }

    private fun bankFor(code: String): String? = BANKS.entries.firstOrNull { code.contains(it.key) }?.value

    private fun direction(text: String): Direction? {
        val debit = DEBIT_WORDS.find(text)?.range?.first
        val credit = CREDIT_WORDS.find(text)?.range?.first
        return when {
            debit == null && credit == null -> null
            credit == null -> Direction.DEBIT
            debit == null -> Direction.CREDIT
            debit < credit -> Direction.DEBIT
            else -> Direction.CREDIT
        }
    }

    private fun amount(text: String): Long? {
        CURRENCY_AMOUNT.findAll(text)
            .firstOrNull { !BALANCE_HINT.containsMatchIn(text.substring(0, it.range.first)) }
            ?.let { return Money.parsePaise(it.groupValues[1]) }
        return VERB_AMOUNT.find(text)?.let { Money.parsePaise(it.groupValues[1]) }
    }

    private fun balance(text: String): Long? = CURRENCY_AMOUNT.findAll(text)
        .firstOrNull { BALANCE_HINT.containsMatchIn(text.substring(0, it.range.first)) }
        ?.let { Money.parsePaise(it.groupValues[1]) }

    private fun account(text: String): String? =
        MASKED_ACCOUNT.find(text)?.groupValues?.get(1) ?: PLAIN_ACCOUNT.find(text)?.groupValues?.get(1)

    private fun reference(text: String): String? =
        REFERENCES.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }

    private fun counterparty(text: String, direction: Direction): String? {
        val patterns = if (direction == Direction.DEBIT) DEBIT_PAYEE else CREDIT_PAYER
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                val candidate = clean(match.groupValues[1]) ?: continue
                if (!NOT_A_PAYEE.containsMatchIn(candidate)) return candidate
            }
        }
        return null
    }

    private fun clean(raw: String): String? {
        val trimmed = raw.trim().trim('.', ',', ';', ':', '-', '(', ')').trim()
            .removePrefix("VPA ").removePrefix("vpa ").trim()
        if (trimmed.length < 2 || trimmed.length > 60) return null
        if (trimmed.none { it.isLetter() }) return null
        return trimmed
    }
}
