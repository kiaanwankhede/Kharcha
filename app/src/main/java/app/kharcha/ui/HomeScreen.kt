package app.kharcha.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kharcha.core.BudgetStatus
import app.kharcha.core.Category
import app.kharcha.core.UpcomingItem
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.InsightsEngine
import app.kharcha.core.dailyTotals
import app.kharcha.core.monthlyTotals
import app.kharcha.core.Money
import app.kharcha.core.Transaction
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    transactions: List<Transaction>,
    catalog: CategoryCatalog,
    pendingCount: Int,
    pendingPaise: Long,
    monthlyLimitPaise: Long?,
    onOpenReview: () -> Unit,
    budgets: List<BudgetStatus> = emptyList(),
    upcoming: List<UpcomingItem> = emptyList(),
    accountCount: Int = 0,
    onOpenRoute: (String) -> Unit = {},
    onCategory: (Category) -> Unit,
    onTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now(Zone)
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    val isCurrent = month == YearMonth.from(today)
    val day = if (isCurrent) today else month.atEndOfMonth()
    val summary = remember(transactions, month, monthlyLimitPaise) {
        InsightsEngine(Zone).summary(transactions, day, if (isCurrent) monthlyLimitPaise else null)
    }
    val trend = remember(transactions) { InsightsEngine(Zone).monthlyTotals(transactions, today) }
    var selectedDay by rememberSaveable(month) { mutableStateOf(day) }
    var expandedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    val daily = remember(transactions, month) { InsightsEngine(Zone).dailyTotals(transactions, month, today) }
    val dayTransactions = remember(transactions, selectedDay) { transactions.filter { it.localDate() == selectedDay } }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹", fontSize = 24.sp) }
                Text(month.title(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Faint)
                TextButton(onClick = { month = month.plusMonths(1) }, enabled = !isCurrent) { Text("›", fontSize = 24.sp) }
            }
        }
        if (pendingCount > 0) {
            item {
                Panel(Modifier.clickable(onClick = onOpenReview), color = Rust) {
                    Text(
                        "🕐  $pendingCount payment${if (pendingCount == 1) "" else "s"} to review",
                        color = Paper,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("Not in your totals yet. Tap to go through them.", color = Paper)
                }
            }
        }
        item {
            Column {
                Text(Money.format(summary.spentPaise), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                val change = summary.changePercent
                val changeText = when {
                    change == null -> "spent"
                    change > 0 -> "spent · $change% more than last month at this point"
                    change < 0 -> "spent · ${-change}% less than last month at this point"
                    else -> "spent · same as last month at this point"
                }
                Text(changeText, color = if ((change ?: 0) > 0) Rust else Muted, fontWeight = FontWeight.SemiBold)
                if (isCurrent && pendingPaise > 0) {
                    Text(
                        "+ ${Money.format(pendingPaise)} waiting for review, not included yet",
                        Modifier.clickable(onClick = onOpenReview),
                        color = Blue,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            Panel {
                SectionLabel("Last 6 months")
                TrendChart(trend, selected = month, onSelect = { month = it })
            }
        }
        item {
            Panel {
                val activeDays = daily.size.coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Day by day", Modifier.weight(1f))
                    Text(
                        "avg ${Money.format(daily.sumOf { it.second } / activeDays)}/day",
                        style = MaterialTheme.typography.labelMedium,
                        color = Faint,
                    )
                }
                DailyChart(daily, selected = selectedDay, onSelect = { selectedDay = it })
                val daySpent = dayTransactions.filter { it.isSpend }.sumOf { it.amountPaise }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedDay.dayHeader(today), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(Money.format(daySpent) + " spent", fontWeight = FontWeight.Bold)
                }
                if (dayTransactions.isEmpty()) {
                    Text("No payments this day.", color = Muted)
                } else {
                    dayTransactions.forEach { t -> TransactionRow(t) { onTransaction(t.id) } }
                }
            }
        }
        if (summary.cashWithdrawnPaise > 0 || summary.cashLoggedPaise > 0) {
            item {
                Panel(color = Sand) {
                    Text("💵 Cash this month", fontWeight = FontWeight.Bold)
                    Row {
                        Text("Withdrawn from ATM", Modifier.weight(1f), color = Muted)
                        Text(Money.format(summary.cashWithdrawnPaise))
                    }
                    Row {
                        Text("Spends you logged", Modifier.weight(1f), color = Muted)
                        Text(Money.format(summary.cashLoggedPaise))
                    }
                    val unlogged = maxOf(0L, summary.cashWithdrawnPaise - summary.cashLoggedPaise)
                    if (unlogged > 0) {
                        Text(
                            "${Money.format(unlogged)} not logged yet. It still counts as spending; log cash spends with ＋ to see where it went.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
            }
        }
        if (summary.walletToppedUpPaise > 0) {
            item {
                Panel(color = Sand) {
                    Text("👛 Wallet & UPI Lite this month", fontWeight = FontWeight.Bold)
                    Row {
                        Text("Moved from bank to wallet", Modifier.weight(1f), color = Muted)
                        Text(Money.format(summary.walletToppedUpPaise))
                    }
                    Row {
                        Text("Spent from wallet (tracked)", Modifier.weight(1f), color = Muted)
                        Text(Money.format(summary.walletSpentPaise))
                    }
                    Text(
                        "Top-ups aren’t counted as spending, only what you spend from the wallet, so nothing is counted twice." +
                            if (summary.walletToppedUpPaise > summary.walletSpentPaise) {
                                " ${Money.format(summary.walletToppedUpPaise - summary.walletSpentPaise)} has no tracked wallet payments yet and counts as “Wallet (spends not tracked)”."
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
        }
        if (summary.savedPaise > 0) {
            item {
                Panel(color = CoolTint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📈  Saved & invested", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(Money.format(summary.savedPaise), fontWeight = FontWeight.Bold, color = Blue)
                    }
                    Text("SIPs, stocks, FDs and other savings this month. Not counted as spending.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        if (isCurrent) {
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Budgets", Modifier.weight(1f))
                        TextButton(onClick = { onOpenRoute("budgets") }) { Text(if (budgets.isEmpty()) "Set up ›" else "All ›") }
                    }
                    if (budgets.isEmpty()) {
                        Text("Set monthly limits for a tag or a merchant like Swiggy.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        budgets.take(3).forEach { BudgetRow(it) { onOpenRoute("budgets") } }
                    }
                }
            }
            item {
                Panel(Modifier.clickable { onOpenRoute("upcoming") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Upcoming · 30 days", Modifier.weight(1f))
                        Text(Money.format(upcoming.sumOf { it.amountPaise }), fontWeight = FontWeight.Bold)
                    }
                    if (upcoming.isEmpty()) {
                        Text("Card bills and monthly payments will show here.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    upcoming.take(3).forEach { item ->
                        Row {
                            Text("${item.emoji}  ${item.label}".trim(), Modifier.weight(1f), maxLines = 1)
                            Text(
                                "${item.date.dayOfMonth} ${item.date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} · ${Money.format(item.amountPaise)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.date.isBefore(today)) Rust else Muted,
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("🤝" to "people" to "Lent", "🏖️" to "trips" to "Trips", "🧾" to "reimburse" to "Office", "🎯" to "goals" to "Goals")
                        .forEach { (pair, label) ->
                            Panel(
                                Modifier
                                    .weight(1f)
                                    .clickable { onOpenRoute(pair.second) },
                                color = Sand,
                            ) {
                                Text(pair.first, style = MaterialTheme.typography.titleLarge)
                                Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                }
            }
            if (accountCount > 0) {
                item {
                    Panel(Modifier.clickable { onOpenRoute("accounts") }, color = Sand) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏦  $accountCount accounts & cards", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text("Balances ›", color = Blue)
                        }
                    }
                }
            }
        }
        summary.safeToSpendTodayPaise?.let { safe ->
            item {
                Panel(color = Accent) {
                    Text("Safe to spend today", color = Paper.copy(alpha = 0.8f))
                    Text(Money.format(safe), color = Paper, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (summary.byCategory.isEmpty()) {
            item {
                EmptyState(
                    "Nothing spent yet this month",
                    "New bank and UPI payments appear here a few seconds after they happen.",
                )
            }
        } else {
            item { SectionLabel("Where it went") }
            val byGroup = summary.byGroup(catalog::groupOf)
            val top = byGroup.first().spentPaise.toFloat()
            items(byGroup, key = { it.group.id }) { group ->
                val share = if (summary.spentPaise > 0) (group.spentPaise * 100 / summary.spentPaise) else 0L
                val expanded = group.group.id in expandedGroups
                val single = group.children.size == 1 && group.children.first().category.id == group.group.id
                val summaryOnly = group.group == Category.UNLOGGED_CASH || group.group == Category.UNTRACKED_WALLET
                Panel(
                    Modifier.clickable {
                        when {
                            summaryOnly -> Unit
                            single -> onCategory(group.group)
                            else -> expandedGroups = if (expanded) expandedGroups - group.group.id else expandedGroups + group.group.id
                        }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EmojiBadge(group.group.emoji, size = 36)
                        Column(Modifier.weight(1f)) {
                            Text(group.group.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                "$share% of spending" + when {
                                    summaryOnly -> ""
                                    single -> " ›"
                                    expanded -> " ▴"
                                    else -> " ▾"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Faint,
                            )
                        }
                        Text(Money.format(group.spentPaise), fontWeight = FontWeight.SemiBold)
                    }
                    Bar(group.spentPaise / top)
                    if (expanded && !single && !summaryOnly) {
                        group.children.forEach { child ->
                            val clickable = child.category != Category.UNLOGGED_CASH && child.category != Category.UNTRACKED_WALLET
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .then(if (clickable) Modifier.clickable { onCategory(child.category) } else Modifier)
                                    .padding(start = 48.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${child.category.emoji}  ${child.category.label}".trim(), Modifier.weight(1f), color = Muted)
                                Text(Money.format(child.spentPaise), color = Muted)
                            }
                        }
                        Text(
                            "See all ${group.group.label} payments ›",
                            Modifier
                                .clickable { onCategory(group.group) }
                                .padding(start = 48.dp, top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Blue,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}
