package app.kharcha.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kharcha.AppContainer
import app.kharcha.core.Category
import app.kharcha.core.InsightsEngine
import app.kharcha.core.Money
import app.kharcha.core.Reports
import app.kharcha.core.Transaction
import app.kharcha.core.dailyTotals
import java.time.LocalDate
import java.time.YearMonth

/** A calendar for one month, each day shaded by how much was spent. Tap a day to see its total. */
@Composable
fun HeatmapPanel(transactions: List<Transaction>, today: LocalDate) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val days = remember(transactions, month) { InsightsEngine(Zone).dailyTotals(transactions, month, today) }
    val levels = remember(days) { Reports.heatLevels(days.map { it.second }) }
    val shades = listOf(Line, ChartSoft.copy(alpha = 0.45f), ChartSoft, ChartStrong.copy(alpha = 0.75f), ChartStrong)
    val leading = month.atDay(1).dayOfWeek.value - 1 // Monday first

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                month = month.minusMonths(1)
                picked = null
            }) { Text("‹") }
            Text(month.title(), Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            TextButton(enabled = month < YearMonth.from(today), onClick = {
                month = month.plusMonths(1)
                picked = null
            }) { Text("›") }
        }
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = Faint)
            }
        }
        val cells = List(leading) { null } + days.indices.toList() + List((7 - (leading + month.lengthOfMonth()) % 7) % 7 + (month.lengthOfMonth() - days.size)) { null }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (index == null) Color.Transparent else shades[levels[index]])
                            .then(
                                if (index == null) {
                                    Modifier
                                } else {
                                    Modifier
                                        .clickable { picked = days[index].first }
                                        .semantics { contentDescription = "${days[index].first.dayOfMonth}: ${Money.format(days[index].second)}" }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index != null) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (levels[index] >= 3) Paper else Muted,
                                fontWeight = if (days[index].first == picked) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        val pickedDay = picked?.let { d -> days.firstOrNull { it.first == d } }
        Text(
            pickedDay?.let { "${it.first.dayHeader(today)}: ${Money.format(it.second)}" }
                ?: "${days.count { it.second == 0L }} no-spend days · tap a day",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
    }
}

/** Last month (or this month so far) in a few lines, with a Share button. */
@Composable
fun MonthReportPanel(transactions: List<Transaction>, today: LocalDate, groupOf: (Category) -> Category) {
    val context = LocalContext.current
    var month by remember { mutableStateOf(YearMonth.from(today).let { if (today.dayOfMonth <= 7) it.minusMonths(1) else it }) }
    val report = remember(transactions, month) { Reports.month(transactions, month, today, groupOf) }
    Panel(color = Sand) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${month.title()} report", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            TextButton(enabled = month < YearMonth.from(today), onClick = { month = month.plusMonths(1) }) { Text("›") }
        }
        Text(Money.format(report.spentPaise), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        report.changePercent?.takeIf { it != 0 }?.let {
            Text(if (it > 0) "$it% more than the month before" else "${-it}% less than the month before", color = if (it > 0) Rust else Blue)
        }
        report.topGroups.forEach { g ->
            Row {
                Text("${g.group.emoji}  ${g.group.label}".trim(), Modifier.weight(1f), color = Muted)
                Text(Money.format(g.spentPaise), color = Muted)
            }
        }
        report.biggest?.let { Text("Biggest: ${Money.format(it.amountPaise)} at ${it.merchant}", style = MaterialTheme.typography.bodySmall) }
        Text("No-spend days: ${report.noSpendDays} of ${report.days}", style = MaterialTheme.typography.bodySmall)
        if (report.savedPaise > 0) Text("Saved & invested: ${Money.format(report.savedPaise)}", style = MaterialTheme.typography.bodySmall, color = Blue)
        OutlinedButton(onClick = {
            val text = Reports.monthText(report)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share report"))
        }) { Text("Share") }
    }
}

/** Everything about one payee: totals, months and every payment. */
@Composable
fun MerchantScreen(container: AppContainer, key: String, onBack: () -> Unit, onTransaction: (Long) -> Unit) {
    val all by container.repository.reviewed.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = LocalDate.now(Zone)
    val page = remember(all, key) { Reports.merchant(key, all, today) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader(page?.name ?: "Payee", onBack, page?.let { "${it.category.emoji} ${it.category.label}".trim() }) }
        if (page == null) {
            item { EmptyState("No reviewed payments", "Payments show up here once they are reviewed.") }
            return@LazyColumn
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f), color = Sand) {
                    Text("This month", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(Money.format(page.thisMonthPaise), fontWeight = FontWeight.Bold)
                }
                Panel(Modifier.weight(1f), color = Sand) {
                    Text("All time", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(Money.format(page.totalPaise), fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Panel {
                Text(
                    "${page.payments} payments · about ${Money.format(page.averagePaise)} each" +
                        (page.first?.let { " · since ${it.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }.take(3)} ${it.year}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                val max = (page.monthly.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
                page.monthly.forEach { (m, paise) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.title().take(3), Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall, color = Faint)
                        Bar(paise.toFloat() / max, modifier = Modifier.weight(1f))
                        Text(Money.format(paise), Modifier.width(84.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { SectionLabel("Payments") }
        items(page.history, key = { it.id }) { t -> TransactionRow(t) { onTransaction(t.id) } }
    }
}

/** This financial year's payments that may count as tax deductions. */
@Composable
fun TaxScreen(container: AppContainer, onBack: () -> Unit) {
    val all by container.repository.reviewed.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = LocalDate.now(Zone)
    val lines = remember(all) { Reports.tax(all, today) }
    val start = Reports.financialYearStart(today)

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHeader(
                "Tax view · FY ${start.year}–${(start.year + 1) % 100}",
                onBack,
                "Payments this year that often count as deductions under the old regime. A starting point to check against your documents, not tax advice.",
            )
        }
        if (lines.isEmpty()) {
            item { EmptyState("Nothing yet", "PPF, insurance, school fees, ELSS SIPs and donations show up here once reviewed.") }
        }
        items(lines) { line ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.label, fontWeight = FontWeight.SemiBold)
                        Text("Section ${line.section}", style = MaterialTheme.typography.bodySmall, color = Faint)
                    }
                    Text(Money.format(line.paidPaise), fontWeight = FontWeight.Bold)
                }
                line.capPaise?.let { cap -> Bar((line.paidPaise.toFloat() / cap).coerceAtMost(1f)) }
                Text(line.note, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        val section80c = lines.filter { it.section == "80C" }.sumOf { it.paidPaise }
        if (section80c > 0) {
            item {
                Panel(color = CoolTint) {
                    Text("80C so far: ${Money.format(section80c)} of ₹1,50,000", fontWeight = FontWeight.Bold)
                    Text(
                        if (section80c >= 1_50_000_00L) "Limit reached." else "${Money.format(1_50_000_00L - section80c)} more would use the full limit.",
                        color = Muted,
                    )
                }
            }
        }
    }
}
