package app.kharcha.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Something to save up for. Progress is what you add by hand ([addedPaise]) plus, when
 * [linkedCategoryId] is set (say Mutual fund SIPs), payments into that category since [createdOn].
 */
data class Goal(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val targetPaise: Long,
    val deadline: LocalDate? = null,
    val addedPaise: Long = 0,
    val linkedCategoryId: String? = null,
    val createdOn: LocalDate,
)

data class GoalProgress(
    val goal: Goal,
    val savedPaise: Long,
    /** What to put away each month from now to reach the target by the deadline; null without a deadline or once reached. */
    val perMonthNeededPaise: Long?,
) {
    val fraction: Float get() = if (goal.targetPaise <= 0) 0f else (savedPaise.toFloat() / goal.targetPaise).coerceAtMost(1f)
    val leftPaise: Long get() = maxOf(0L, goal.targetPaise - savedPaise)
    val reached: Boolean get() = savedPaise >= goal.targetPaise
}

object Goals {
    fun progress(goal: Goal, transactions: List<Transaction>, today: LocalDate, zone: ZoneId = ZoneId.of("Asia/Kolkata")): GoalProgress {
        val linked = goal.linkedCategoryId?.let { id ->
            expand(transactions.filter { it.status == ReviewStatus.REVIEWED && it.direction == Direction.DEBIT })
                .filter { it.category.id == id && !Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate().isBefore(goal.createdOn) }
                .sumOf { it.amountPaise }
        } ?: 0L
        val saved = goal.addedPaise + linked
        val left = maxOf(0L, goal.targetPaise - saved)
        val perMonth = goal.deadline?.takeIf { left > 0 }?.let { deadline ->
            // Count the current month, so a deadline later this month still needs one month of saving.
            val months = maxOf(1L, ChronoUnit.MONTHS.between(today.withDayOfMonth(1), deadline.withDayOfMonth(1)) + 1)
            (left + months - 1) / months
        }
        return GoalProgress(goal, saved, perMonth)
    }
}
