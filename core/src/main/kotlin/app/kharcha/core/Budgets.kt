package app.kharcha.core

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class BudgetKind { GROUP, CATEGORY, MERCHANT }

/** A monthly limit on a group, a subcategory or a merchant ([targetId] is the group/category id or merchant key). */
data class Budget(
    val id: Long = 0,
    val kind: BudgetKind,
    val targetId: String,
    val label: String,
    val emoji: String,
    val limitPaise: Long,
)

enum class BudgetLevel { OK, NEAR, OVER }

data class BudgetStatus(
    val budget: Budget,
    val spentPaise: Long,
    /** Spend so far stretched over the whole month at the current pace. */
    val projectedPaise: Long,
) {
    val fraction: Float get() = if (budget.limitPaise <= 0) 0f else spentPaise.toFloat() / budget.limitPaise
    val leftPaise: Long get() = budget.limitPaise - spentPaise
    val level: BudgetLevel
        get() = when {
            spentPaise >= budget.limitPaise -> BudgetLevel.OVER
            spentPaise >= budget.limitPaise * 8 / 10 -> BudgetLevel.NEAR
            else -> BudgetLevel.OK
        }
}

object Budgets {
    /** This month's spend against each budget, refunds taken off. [groupOf] resolves the user's own groups. */
    fun status(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        today: LocalDate,
        groupOf: (Category) -> Category = Category::groupOf,
        zone: ZoneId = ZoneId.of("Asia/Kolkata"),
    ): List<BudgetStatus> {
        val month = YearMonth.from(today)
        val thisMonth = expand(transactions).filter {
            YearMonth.from(Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()) == month
        }
        return budgets.map { budget ->
            val matching = thisMonth.filter {
                when (budget.kind) {
                    BudgetKind.GROUP -> groupOf(it.category).id == budget.targetId
                    // A tag budget counts every payment carrying the tag, not only where it's the main one.
                    BudgetKind.CATEGORY -> it.category.id == budget.targetId ||
                        (budget.targetId.startsWith(Category.TAG_PREFIX) && budget.targetId.removePrefix(Category.TAG_PREFIX) in it.tags)
                    BudgetKind.MERCHANT -> it.merchantKey == budget.targetId
                }
            }
            val spent = maxOf(
                0L,
                matching.filter { it.isSpend }.sumOf { it.amountPaise } - matching.filter { it.isRefund }.sumOf { it.amountPaise },
            )
            val projected = spent * month.lengthOfMonth() / today.dayOfMonth
            BudgetStatus(budget, spent, projected)
        }.sortedByDescending { it.fraction }
    }
}
