package app.kharcha.core

/**
 * Turns a parsed message into a stored [Transaction]: cleans up the payee name and picks a category.
 *
 * [rules] maps a merchant key to the category the user chose for it; a user's choice always wins.
 */
class Classifier(private val rules: Map<String, Category> = emptyMap()) {

    private val keywordCategories = listOf(
        Regex("""\brecharge|prepaid|postpaid""", RegexOption.IGNORE_CASE) to Category.MOBILE,
        Regex("""\bdth\b""", RegexOption.IGNORE_CASE) to Category.DTH,
        Regex("""electricity|power|vidyut|bijli""", RegexOption.IGNORE_CASE) to Category.ELECTRICITY,
        Regex("""broadband|fiber|fibre|internet""", RegexOption.IGNORE_CASE) to Category.INTERNET,
        Regex("""bill ?pay|billdesk|bbps""", RegexOption.IGNORE_CASE) to Category.OTHER_BILLS,
        Regex("""\bemi\b|\bloan\b|\bnach\b|ecs""", RegexOption.IGNORE_CASE) to Category.EMI,
        Regex("""\bsip\b|mutual fund|\bmf\b""", RegexOption.IGNORE_CASE) to Category.MUTUAL_FUNDS,
        Regex("""petrol|fuel|filling station|service station""", RegexOption.IGNORE_CASE) to Category.FUEL,
        Regex("""milk|dairy|doodh""", RegexOption.IGNORE_CASE) to Category.MILK,
        Regex("""sabzi|vegetable|fruit""", RegexOption.IGNORE_CASE) to Category.VEGETABLES,
        Regex("""chicken|mutton|meat|fish""", RegexOption.IGNORE_CASE) to Category.MEAT,
        Regex("""\bchai\b|\btea\b|coffee|cafe""", RegexOption.IGNORE_CASE) to Category.CAFES,
        Regex("""restaurant|hotel|dhaba|biryani|kitchen|foods?\b""", RegexOption.IGNORE_CASE) to Category.RESTAURANTS,
        Regex("""bakery|sweets|mithai""", RegexOption.IGNORE_CASE) to Category.SNACKS,
        Regex("""wine|liquor|beer|\bbar\b|pub\b""", RegexOption.IGNORE_CASE) to Category.ALCOHOL,
        Regex("""pharma|medical|chemist|medicos""", RegexOption.IGNORE_CASE) to Category.MEDICINES,
        Regex("""hospital|nursing home""", RegexOption.IGNORE_CASE) to Category.HOSPITAL,
        Regex("""clinic|dental|\bdr\.? """, RegexOption.IGNORE_CASE) to Category.DOCTOR,
        Regex("""diagnostic|patholog|\blab\b""", RegexOption.IGNORE_CASE) to Category.LAB_TESTS,
        Regex("""\bgym\b|fitness|yoga""", RegexOption.IGNORE_CASE) to Category.FITNESS,
        Regex("""kirana|grocery|supermarket|mart\b|provision|general store""", RegexOption.IGNORE_CASE) to Category.KIRANA,
        Regex("""salon|parlour|parlor|spa\b""", RegexOption.IGNORE_CASE) to Category.BEAUTY,
        Regex("""garments|fashion|textile|footwear""", RegexOption.IGNORE_CASE) to Category.CLOTHES,
        Regex("""mobile(?:s| store)|electronics""", RegexOption.IGNORE_CASE) to Category.ELECTRONICS,
        Regex("""tuition|classes|academy|coaching""", RegexOption.IGNORE_CASE) to Category.TUITION,
        Regex("""stationer|book ?(?:store|depot)""", RegexOption.IGNORE_CASE) to Category.STATIONERY,
        Regex("""\bauto\b|rickshaw|\btaxi\b|cab\b""", RegexOption.IGNORE_CASE) to Category.CABS,
        Regex("""\bvet\b|pet ?(?:shop|store|clinic)""", RegexOption.IGNORE_CASE) to Category.PETS,
    )

    fun classify(parsed: ParsedTransaction): Transaction {
        val resolved = Merchants.resolve(parsed.counterparty, parsed.vpa)
        val category = rules[resolved.key] ?: defaultCategory(parsed, resolved)
        return Transaction(
            amountPaise = parsed.amountPaise,
            direction = parsed.direction,
            timestampMillis = parsed.timestampMillis,
            merchant = resolved.name,
            merchantKey = resolved.key,
            category = category,
            source = parsed.source,
            rawText = parsed.rawText,
            accountLast4 = parsed.accountLast4,
            bank = parsed.bank,
            vpa = parsed.vpa,
            reference = parsed.reference,
            balancePaise = parsed.balancePaise,
            status = ReviewStatus.PENDING,
        )
    }

    private fun defaultCategory(parsed: ParsedTransaction, resolved: Merchants.Resolved): Category = when {
        parsed.isOwnTransfer -> if (CARD_BILL.containsMatchIn(parsed.rawText)) Category.CARD_BILL else Category.TRANSFER
        parsed.direction == Direction.CREDIT -> incomeCategory(parsed, resolved)
        parsed.isCashWithdrawal -> Category.CASH
        parsed.isWalletTopUp -> Category.WALLET
        resolved.brand != null -> resolved.brand.category
        else -> keywordCategories.firstOrNull { (pattern, _) ->
            pattern.containsMatchIn(listOfNotNull(parsed.counterparty, parsed.vpa).joinToString(" "))
        }?.second ?: if (resolved.isPerson) Category.PEOPLE else Category.OTHER
    }

    private fun incomeCategory(parsed: ParsedTransaction, resolved: Merchants.Resolved): Category {
        val text = parsed.rawText
        return when {
            SALARY.containsMatchIn(text) -> Category.SALARY
            REFUND.containsMatchIn(text) -> Category.REFUNDS
            INTEREST.containsMatchIn(text) -> Category.INTEREST
            resolved.isPerson -> Category.RECEIVED
            else -> Category.INCOME
        }
    }

    companion object {
        private val CARD_BILL = Regex("""card""", RegexOption.IGNORE_CASE)
        private val SALARY = Regex("""\bsalary\b|\bsal\b|payroll""", RegexOption.IGNORE_CASE)
        private val REFUND = Regex("""refund|reversal|reversed|cashback|cash back""", RegexOption.IGNORE_CASE)
        private val INTEREST = Regex("""\binterest\b|\bint\.? (?:pd|paid|cr)|dividend""", RegexOption.IGNORE_CASE)

        /**
         * Moves a transaction saved under the first version's flat categories into the subcategory tree:
         * the merchant decides where it can, otherwise the old category's closest match.
         */
        fun recategorise(t: Transaction, legacyId: String): Transaction {
            val fallback = Category.LEGACY_IDS[legacyId] ?: return t
            if (!fallback.countsAsSpend) return t.copy(category = fallback)
            val brand = Merchants.brandFor(listOfNotNull(t.merchant, t.vpa).joinToString(" "))
            return t.copy(category = brand?.category?.takeIf { it.countsAsSpend } ?: fallback)
        }
    }
}
