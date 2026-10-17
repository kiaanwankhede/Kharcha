package app.kharcha.core

/**
 * Turns a parsed message into a stored [Transaction]: cleans up the payee name and picks a category.
 *
 * [rules] maps a merchant key to the category the user chose for it; a user's choice always wins.
 */
class Classifier(private val rules: Map<String, Category> = emptyMap()) {

    private val keywordCategories = listOf(
        Regex("""\brecharge|prepaid|postpaid|dth\b""", RegexOption.IGNORE_CASE) to Category.RECHARGE,
        Regex("""electricity|water bill|gas bill|broadband|bill ?pay|billdesk|bbps""", RegexOption.IGNORE_CASE) to Category.BILLS,
        Regex("""\bemi\b|\bloan\b|\bnach\b|ecs""", RegexOption.IGNORE_CASE) to Category.EMI,
        Regex("""\bsip\b|mutual fund|\bmf\b""", RegexOption.IGNORE_CASE) to Category.INVESTMENT,
        Regex("""petrol|fuel|filling station|service station""", RegexOption.IGNORE_CASE) to Category.FUEL,
        Regex("""restaurant|cafe|hotel|dhaba|bakery|sweets|food""", RegexOption.IGNORE_CASE) to Category.FOOD,
        Regex("""pharma|medical|chemist|hospital|clinic|diagnostic""", RegexOption.IGNORE_CASE) to Category.HEALTH,
        Regex("""kirana|grocery|supermarket|mart\b|provision""", RegexOption.IGNORE_CASE) to Category.GROCERIES,
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
        )
    }

    private fun defaultCategory(parsed: ParsedTransaction, resolved: Merchants.Resolved): Category = when {
        parsed.isOwnTransfer -> Category.TRANSFER
        parsed.direction == Direction.CREDIT -> Category.INCOME
        parsed.isCashWithdrawal -> Category.CASH
        resolved.brand != null -> resolved.brand.category
        else -> keywordCategories.firstOrNull { (pattern, _) ->
            pattern.containsMatchIn(listOfNotNull(parsed.counterparty, parsed.vpa).joinToString(" "))
        }?.second ?: if (resolved.isPerson) Category.PEOPLE else Category.OTHER
    }
}
