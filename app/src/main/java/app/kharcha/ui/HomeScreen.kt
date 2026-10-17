package app.kharcha.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.kharcha.core.Category
import app.kharcha.core.InsightsEngine
import app.kharcha.core.monthlyTotals
import app.kharcha.core.Money
import app.kharcha.core.Transaction
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    transactions: List<Transaction>,
    monthlyLimitPaise: Long?,
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
    val recent = remember(transactions, month) {
        transactions.filter { YearMonth.from(it.localDate()) == month }.take(5)
    }

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
            }
        }
        item {
            Panel {
                SectionLabel("Last 6 months")
                TrendChart(trend, selected = month, onSelect = { month = it })
            }
        }
        summary.safeToSpendTodayPaise?.let { safe ->
            item {
                Panel(color = Navy) {
                    Text("Safe to spend today", color = Sand)
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
            val top = summary.byCategory.first().spentPaise.toFloat()
            items(summary.byCategory, key = { it.category.id }) { total ->
                val share = if (summary.spentPaise > 0) (total.spentPaise * 100 / summary.spentPaise) else 0L
                Panel(Modifier.clickable { onCategory(total.category) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EmojiBadge(total.category.emoji, size = 36)
                        Column(Modifier.weight(1f)) {
                            Text(total.category.label, fontWeight = FontWeight.SemiBold)
                            Text("$share% of spending", style = MaterialTheme.typography.bodySmall, color = Faint)
                        }
                        Text(Money.format(total.spentPaise), fontWeight = FontWeight.SemiBold)
                    }
                    Bar(total.spentPaise / top)
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionLabel("Recent") }
            items(recent, key = { it.id }) { t -> TransactionRow(t) { onTransaction(t.id) } }
        }
    }
}
