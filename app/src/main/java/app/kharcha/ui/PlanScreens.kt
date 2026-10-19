package app.kharcha.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kharcha.AppContainer
import app.kharcha.core.Accounts
import app.kharcha.core.BillState
import app.kharcha.core.Budget
import app.kharcha.core.BudgetKind
import app.kharcha.core.BudgetLevel
import app.kharcha.core.BudgetStatus
import app.kharcha.core.Budgets
import app.kharcha.core.CardBills
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.InsightsEngine
import app.kharcha.core.Kind
import app.kharcha.core.Money
import app.kharcha.core.ReviewStatus
import app.kharcha.core.Upcoming
import app.kharcha.core.UpcomingKind
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private fun shortDate(d: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, d)) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> "${d.dayOfMonth} ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = Muted) }
    }
}

@Composable
fun BudgetRow(status: BudgetStatus, onClick: (() -> Unit)? = null) {
    val b = status.budget
    val color = when (status.level) {
        BudgetLevel.OVER -> Rust
        BudgetLevel.NEAR -> Rust
        BudgetLevel.OK -> ChartStrong
    }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${b.emoji}  ${b.label}".trim(), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${Money.format(status.spentPaise)} / ${Money.format(b.limitPaise)}", style = MaterialTheme.typography.bodySmall)
        }
        Bar(status.fraction, color = color)
        Text(
            when (status.level) {
                BudgetLevel.OVER -> "Over by ${Money.format(-status.leftPaise)}"
                BudgetLevel.NEAR -> "${Money.format(status.leftPaise)} left · on pace for ${Money.format(status.projectedPaise)}"
                BudgetLevel.OK -> "${Money.format(status.leftPaise)} left · on pace for ${Money.format(status.projectedPaise)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (status.level == BudgetLevel.OK) Faint else Rust,
        )
    }
}

/** Monthly limits on a tag or a merchant. */
@Composable
fun BudgetsScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.repository
    val budgets by repository.budgets.collectAsStateWithLifecycle(initialValue = emptyList())
    val all by repository.reviewed.collectAsStateWithLifecycle(initialValue = emptyList())
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(Zone)
    val statuses = remember(budgets, all, catalog) { Budgets.status(budgets, all, today, catalog::groupOf) }
    var editing by remember { mutableStateOf<Budget?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Budgets", onBack, "Monthly limits. You’ll get a notification at 80% and when a budget is crossed.") }
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ New budget") } }
        if (statuses.isEmpty()) {
            item { EmptyState("No budgets yet", "Try one for Food & drinks, or a merchant like Swiggy.") }
        }
        items(statuses, key = { it.budget.id }) { status ->
            Panel { BudgetRow(status) { editing = status.budget } }
        }
    }

    if (creating || editing != null) {
        BudgetDialog(
            initial = editing,
            catalog = catalog,
            merchants = remember(all) {
                all.filter { it.isSpend }.groupBy { it.merchantKey }
                    .map { (key, items) -> key to items.first().merchant }
                    .sortedByDescending { (key, _) -> all.count { it.merchantKey == key } }
                    .take(40)
            },
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { budget ->
                creating = false
                editing = null
                scope.launch { repository.saveBudget(budget) }
            },
            onDelete = editing?.let { b ->
                {
                    editing = null
                    scope.launch { repository.deleteBudget(b.id) }
                    Unit
                }
            },
        )
    }
}

@Composable
private fun BudgetDialog(
    initial: Budget?,
    catalog: CategoryCatalog,
    merchants: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (Budget) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var kind by remember { mutableStateOf(initial?.kind?.takeIf { it != BudgetKind.GROUP } ?: BudgetKind.CATEGORY) }
    var targetId by remember { mutableStateOf(initial?.targetId) }
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🎯") }
    var amount by remember { mutableStateOf(initial?.let { (it.limitPaise / 100).toString() }.orEmpty()) }
    var menuOpen by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    val limit = amount.toLongOrNull()?.takeIf { it > 0 }?.times(100)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New budget" else "Edit budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(BudgetKind.CATEGORY to "Tag", BudgetKind.MERCHANT to "Merchant").forEach { (value, name) ->
                        if (kind == value) {
                            Button(onClick = {}) { Text(name) }
                        } else {
                            OutlinedButton(onClick = {
                                kind = value
                                targetId = null
                                label = ""
                            }) { Text(name) }
                        }
                    }
                }
                Box {
                    OutlinedButton(
                        onClick = { if (kind == BudgetKind.CATEGORY) sheetOpen = true else menuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (targetId == null) "Choose…" else "$emoji  $label".trim() + "  ▾") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (kind == BudgetKind.GROUP) {
                            catalog.groups.filter { it.kind == Kind.SPENDING }.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text("${group.emoji}  ${group.label}".trim()) },
                                    onClick = {
                                        targetId = group.id
                                        label = group.label
                                        emoji = group.emoji
                                        menuOpen = false
                                    },
                                )
                            }
                        } else {
                            if (merchants.isEmpty()) {
                                DropdownMenuItem(text = { Text("No merchants yet") }, onClick = { menuOpen = false }, enabled = false)
                            }
                            merchants.forEach { (key, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        targetId = key
                                        label = name
                                        emoji = "🏪"
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(9) },
                    label = { Text("Monthly limit") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete budget", color = Rust) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = targetId != null && limit != null,
                onClick = { onSave(Budget(initial?.id ?: 0, kind, targetId!!, label, emoji, limit!!)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (sheetOpen) {
        CategoryPickerSheet(
            catalog = catalog,
            selected = targetId?.let(catalog::byId),
            filter = { it.countsAsSpend },
            onPick = {
                targetId = it.id
                label = it.label
                emoji = it.emoji
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

/** Bank accounts and cards seen in SMS, with their latest balance. */
@Composable
fun AccountsScreen(container: AppContainer, onBack: () -> Unit) {
    val all by container.repository.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val accounts = remember(all) {
        Accounts.summarize(all.filter { it.status != ReviewStatus.IGNORED }, YearMonth.now(Zone))
    }
    var threshold by remember { mutableStateOf(container.prefs.lowBalancePaise?.let { (it / 100).toString() }.orEmpty()) }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Accounts & cards", onBack, "From your bank SMS. Balances are as of the last message that showed one.") }
        if (accounts.isEmpty()) item { EmptyState("No accounts yet", "They appear once bank SMS with account numbers come in.") }
        items(accounts, key = { it.key }) { account ->
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmojiBadge(if (account.isCreditCard) "💳" else "🏦", size = 40)
                    Column(Modifier.weight(1f)) {
                        Text(account.label, fontWeight = FontWeight.Bold)
                        Text(
                            "${account.paymentsThisMonth} payments · ${Money.format(account.spentThisMonthPaise)} spent this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = Faint,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(account.latestBalancePaise?.let { Money.format(it) } ?: "—", fontWeight = FontWeight.Bold)
                        Text(
                            (if (account.isCreditCard) "available" else "balance") +
                                (account.balanceAtMillis?.let { " · " + shortDate(Upcoming.dateOf(it), LocalDate.now(Zone)) } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = Faint,
                        )
                    }
                }
            }
        }
        item {
            Panel(color = Sand) {
                Text("Low balance alert", fontWeight = FontWeight.Bold)
                Text("Get a notification when a bank SMS shows a balance below this.", color = Muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = {
                            threshold = it.filter(Char::isDigit).take(9)
                            saved = false
                        },
                        prefix = { Text("₹") },
                        singleLine = true,
                        placeholder = { Text("Off") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        container.prefs.lowBalancePaise = threshold.toLongOrNull()?.takeIf { it > 0 }?.times(100)
                        saved = true
                    }) { Text(if (saved) "Saved" else "Save") }
                }
            }
        }
    }
}

/** Card bills and expected recurring payments for the next 30 days. */
@Composable
fun UpcomingScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.repository
    val all by repository.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val bills by repository.cardBills.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(Zone)
    val live = remember(all) { all.filter { it.status != ReviewStatus.IGNORED } }
    val reviewed = remember(all) { all.filter { it.status == ReviewStatus.REVIEWED } }
    val upcoming = remember(bills, live, reviewed) {
        Upcoming.next(bills, InsightsEngine(Zone).recurring(reviewed, today), live, today)
    }
    val latestBills = remember(bills) {
        bills.groupBy { it.last4 ?: it.bank ?: "" }.mapNotNull { (_, list) -> list.maxByOrNull { it.receivedAtMillis } }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackHeader(
                "Upcoming",
                onBack,
                "Next 30 days: ${Money.format(upcoming.sumOf { it.amountPaise })} in card bills and recurring payments.",
            )
        }
        if (upcoming.isEmpty()) item { EmptyState("Nothing due", "Card statements and monthly payments show up here.") }
        items(upcoming, key = { "${it.kind}-${it.label}-${it.date}" }) { item ->
            Panel(color = if (item.date.isBefore(today)) WarmTint else CardColor) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmojiBadge(item.emoji, size = 36)
                    Column(Modifier.weight(1f)) {
                        Text(item.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (item.date.isBefore(today)) "Overdue · " else "") + shortDate(item.date, today) +
                                if (item.kind == UpcomingKind.RECURRING) " · expected" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.date.isBefore(today)) Rust else Faint,
                        )
                    }
                    Text(Money.format(item.amountPaise), fontWeight = FontWeight.Bold)
                }
                val billId = item.billId
                if (item.kind == UpcomingKind.CARD_BILL && billId != null) {
                    TextButton(onClick = { scope.launch { repository.setBillPaid(billId, true) } }) { Text("Mark as paid") }
                }
            }
        }
        if (latestBills.isNotEmpty()) {
            item { SectionLabel("Card statements") }
            items(latestBills, key = { "bill-${it.id}" }) { bill ->
                val state = CardBills.state(bill, live, today)
                Panel(color = Sand) {
                    Row {
                        Text("💳  ${bill.label}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(Money.format(bill.totalDuePaise), fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Due ${shortDate(bill.dueDate, today)}" + (bill.minDuePaise?.let { " · minimum ${Money.format(it)}" } ?: "") + " · " +
                            when (state) {
                                BillState.PAID -> "Paid ✓"
                                BillState.PARTLY_PAID -> "Partly paid"
                                BillState.DUE -> "Not paid yet"
                                BillState.OVERDUE -> "Overdue"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state == BillState.OVERDUE) Rust else Muted,
                    )
                    if (bill.paidManually) {
                        TextButton(onClick = { scope.launch { repository.setBillPaid(bill.id, false) } }) { Text("Undo “paid”") }
                    }
                }
            }
        }
    }
}
