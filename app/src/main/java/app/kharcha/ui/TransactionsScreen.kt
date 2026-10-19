@file:OptIn(ExperimentalMaterial3Api::class)

package app.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kharcha.core.Category
import app.kharcha.core.Money
import app.kharcha.core.Transaction

@Composable
fun TransactionsScreen(
    transactions: List<Transaction>,
    ignored: List<Transaction>,
    categoryFilter: Category?,
    onClearFilter: () -> Unit,
    onTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showIgnored by rememberSaveable { mutableStateOf(false) }
    val shown = remember(transactions, ignored, showIgnored, query, categoryFilter) {
        val q = query.trim().lowercase()
        (if (showIgnored) ignored else transactions).filter { t ->
            (categoryFilter == null || t.category.id == categoryFilter.id || categoryFilter.tagName in t.tags) && t.matches(q)
        }
    }
    val byDay = remember(shown) { shown.groupBy { it.localDate() } }

    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search payee, amount, UPI ID, note, SMS…") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (categoryFilter != null) {
                    AssistChip(onClick = onClearFilter, label = { Text("${categoryFilter.emoji} ${categoryFilter.label}".trim() + "  ✕") })
                }
                if (ignored.isNotEmpty()) {
                    FilterChip(
                        selected = showIgnored,
                        onClick = { showIgnored = !showIgnored },
                        label = { Text("Ignored (${ignored.size})") },
                    )
                }
            }
        }
        if (shown.isEmpty()) {
            EmptyState(
                if (transactions.isEmpty()) "No transactions yet" else "No matches",
                if (transactions.isEmpty()) "Allow SMS access in Settings, then re-scan your inbox." else "Try a different search.",
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
            byDay.forEach { (day, dayItems) ->
                item(key = "header-$day") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
                        SectionLabel(day.dayHeader(), Modifier.weight(1f))
                        val spent = dayItems.filter { it.isSpend }.sumOf { it.amountPaise }
                        if (spent > 0) Text(Money.format(spent), style = MaterialTheme.typography.labelMedium, color = Faint)
                    }
                }
                items(dayItems, key = { it.id }) { t -> TransactionRow(t) { onTransaction(t.id) } }
            }
        }
    }
}
