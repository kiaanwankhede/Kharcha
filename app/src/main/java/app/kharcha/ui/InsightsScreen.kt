package app.kharcha.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kharcha.core.Category
import app.kharcha.core.InsightKind
import app.kharcha.core.InsightsEngine
import app.kharcha.core.Money
import app.kharcha.core.Transaction
import java.time.LocalDate


@Composable
fun InsightsScreen(
    transactions: List<Transaction>,
    monthlyLimitPaise: Long?,
    modifier: Modifier = Modifier,
    groupOf: (Category) -> Category = Category::groupOf,
    onOpenRoute: (String) -> Unit = {},
) {
    val today = LocalDate.now(Zone)
    val engine = remember { InsightsEngine(Zone) }
    val insights = remember(transactions, monthlyLimitPaise) { engine.insights(transactions, today, monthlyLimitPaise) }
    val recurring = remember(transactions) { engine.recurring(transactions, today) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Insights", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { HeatmapPanel(transactions, today) }
        item { MonthReportPanel(transactions, today, groupOf) }
        item {
            Panel(Modifier.clickable { onOpenRoute("tax") }, color = CoolTint) {
                Row {
                    Text("🏛️  Tax view", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("80C, 80D, 80G ›", color = Blue)
                }
            }
        }
        if (insights.isEmpty()) {
            item {
                EmptyState(
                    "Not enough data yet",
                    "Insights appear once there are a few weeks of transactions. Re-scan your inbox in Settings to load history.",
                )
            }
        }
        items(insights) { insight ->
            val tint = when (insight.kind) {
                InsightKind.SPIKE, InsightKind.UNUSUAL -> WarmTint
                InsightKind.RECURRING, InsightKind.DROP -> CoolTint
                else -> Sand
            }
            Panel(color = tint) {
                Text(insight.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(insight.body, color = Muted)
            }
        }
        if (recurring.isNotEmpty()) {
            item { SectionLabel("Recurring payments") }
            items(recurring) { r ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${r.merchant} · next ~${r.nextExpected.dayOfMonth} ${r.nextExpected.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}", Modifier.weight(1f))
                    Text(Money.format(r.typicalAmountPaise), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
