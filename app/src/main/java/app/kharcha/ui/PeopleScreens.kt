package app.kharcha.ui

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kharcha.AppContainer
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Event
import app.kharcha.core.Events
import app.kharcha.core.Goal
import app.kharcha.core.Goals
import app.kharcha.core.Kind
import app.kharcha.core.Money
import app.kharcha.core.People
import app.kharcha.core.PersonBalance
import app.kharcha.core.Reimburse
import app.kharcha.core.ReviewStatus
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun LocalDate.shortText(): String = "$dayOfMonth ${month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)} $year"

/** A button showing a date; tapping it opens the phone's date picker. */
@Composable
fun DateButton(label: String, date: LocalDate?, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val start = date ?: LocalDate.now(Zone)
            DatePickerDialog(context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, start.year, start.monthValue - 1, start.dayOfMonth).show()
        },
        modifier = modifier,
    ) { Text("$label: ${date?.shortText() ?: "pick a date"}") }
}

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String = "Amount") {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() || it == '.' }.take(12)) },
        label = { Text(label) },
        prefix = { Text("₹") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------------------------------------
// Lent & borrowed

/** Who owes you and whom you owe, with UPI settle-up. */
@Composable
fun PeopleScreen(container: AppContainer, onBack: () -> Unit, onTransaction: (Long) -> Unit) {
    val repository = container.repository
    val all by repository.reviewed.collectAsStateWithLifecycle(initialValue = emptyList())
    val ledger = remember(all) { People.ledger(all) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<String?>(null) }
    var settling by remember { mutableStateOf<PersonBalance?>(null) }
    var askingUpiFor by remember { mutableStateOf<PersonBalance?>(null) }
    val owedToMe = ledger.filter { it.owedToMePaise > 0 }.sumOf { it.owedToMePaise }
    val iOwe = ledger.filter { it.owedToMePaise < 0 }.sumOf { -it.owedToMePaise }

    fun remind(person: PersonBalance) {
        val vpa = container.prefs.myUpiId
        if (vpa == null) {
            askingUpiFor = person
            return
        }
        val text = People.reminder(person, vpa, container.prefs.myName ?: "Me")
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Ask ${person.name}"))
    }

    fun payBack(person: PersonBalance) {
        val vpa = person.vpa ?: return
        val link = People.upiLink(vpa, person.name, -person.owedToMePaise, "Paying back")
        try {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(link)), "Pay with"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No UPI app found", Toast.LENGTH_SHORT).show()
        }
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
                "Lent & borrowed",
                onBack,
                "File a payment as “Lent (to get back)” or “Borrowed (to repay)” when reviewing. When they pay you back, Kharcha spots it.",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f), color = CoolTint) {
                    Text("Owed to you", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(Money.format(owedToMe), fontWeight = FontWeight.Bold, color = Blue)
                }
                Panel(Modifier.weight(1f), color = WarmTint) {
                    Text("You owe", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(Money.format(iOwe), fontWeight = FontWeight.Bold, color = Rust)
                }
            }
        }
        if (ledger.isEmpty()) {
            item { EmptyState("Nobody owes anybody", "Lend money over UPI, file it as “Lent (to get back)”, and it will show up here.") }
        }
        items(ledger, key = { it.key }) { person ->
            val open = expanded == person.key
            Panel(Modifier.clickable { expanded = if (open) null else person.key }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(person.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                person.owedToMePaise > 0 -> "Owes you ${Money.format(person.owedToMePaise)}"
                                person.owedToMePaise < 0 -> "You owe ${Money.format(-person.owedToMePaise)}"
                                else -> "All settled"
                            },
                            color = when {
                                person.owedToMePaise > 0 -> Blue
                                person.owedToMePaise < 0 -> Rust
                                else -> Faint
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(if (open) "▴" else "▾", color = Faint)
                }
                if (person.owedToMePaise > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { remind(person) }) { Text("Ask to pay") }
                        OutlinedButton(onClick = { settling = person }) { Text("Got it in cash") }
                    }
                } else if (person.owedToMePaise < 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { payBack(person) }, enabled = person.vpa != null) { Text("Pay with UPI") }
                        OutlinedButton(onClick = { settling = person }) { Text("Paid in cash") }
                    }
                    if (person.vpa == null) Text("No UPI ID seen for ${person.name} yet.", color = Faint, style = MaterialTheme.typography.bodySmall)
                }
                if (open) person.history.forEach { t -> TransactionRow(t) { onTransaction(t.id) } }
            }
        }
    }

    settling?.let { person ->
        var amount by remember(person.key) { mutableStateOf(Money.format(kotlin.math.abs(person.owedToMePaise), showPaise = true).removePrefix("₹").replace(",", "")) }
        val theyPaidMe = person.owedToMePaise > 0
        AlertDialog(
            onDismissRequest = { settling = null },
            title = { Text(if (theyPaidMe) "${person.name} paid you back" else "You paid ${person.name} back") },
            text = { AmountField(amount, { amount = it }) },
            confirmButton = {
                TextButton(
                    enabled = Money.parsePaise(amount) != null,
                    onClick = {
                        val paise = Money.parsePaise(amount) ?: return@TextButton
                        settling = null
                        scope.launch { repository.addSettlement(person.key, person.name, paise, theyPaidMe) }
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { settling = null }) { Text("Cancel") } },
        )
    }

    askingUpiFor?.let { person ->
        var vpa by remember { mutableStateOf("") }
        var name by remember { mutableStateOf(container.prefs.myName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { askingUpiFor = null },
            title = { Text("Your UPI ID") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Goes into the reminder so ${person.name} can pay you in one tap. Asked only once.", color = Muted)
                    OutlinedTextField(vpa, { vpa = it.trim() }, label = { Text("UPI ID (like name@okhdfcbank)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = vpa.contains('@'),
                    onClick = {
                        container.prefs.myUpiId = vpa
                        container.prefs.myName = name
                        askingUpiFor = null
                        remind(person)
                    },
                ) { Text("Save & send") }
            },
            dismissButton = { TextButton(onClick = { askingUpiFor = null }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Trips & events

@Composable
fun TripsScreen(container: AppContainer, onBack: () -> Unit, onTransaction: (Long) -> Unit) {
    val repository = container.repository
    val events by repository.events.collectAsStateWithLifecycle(initialValue = emptyList())
    val all by repository.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = LocalDate.now(Zone)
    val summaries = remember(events, all) { events.map { Events.summary(it, all, today) } }
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Event?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Trips & events", onBack, "While a trip is on, new payments get its tag by themselves. Add tags to any payment from its page.") }
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ New trip or event") } }
        if (summaries.isEmpty()) {
            item { EmptyState("No trips yet", "Make one for a holiday, a wedding or a house move to see what it cost in total.") }
        }
        items(summaries, key = { it.event.id }) { summary ->
            val event = summary.event
            val open = expanded == event.id
            val going = event.covers(today)
            Panel(Modifier.clickable { expanded = if (open) null else event.id }, color = if (going) CoolTint else CardColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(event.emoji, size = 36)
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(event.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${event.start.shortText()} – ${event.end?.shortText() ?: "now"}${if (going) " · on now" else ""} · ${summary.payments} payments",
                            style = MaterialTheme.typography.bodySmall,
                            color = Faint,
                        )
                    }
                    Text(Money.format(summary.spentPaise), fontWeight = FontWeight.Bold)
                }
                if (open) {
                    summary.byCategory.forEach { c ->
                        Row {
                            Text("${c.category.emoji}  ${c.category.label}", Modifier.weight(1f), color = Muted)
                            Text(Money.format(c.spentPaise), color = Muted)
                        }
                    }
                    val days = summary.perDay.count { it.second > 0 }
                    if (days > 0) Text("About ${Money.format(summary.spentPaise / maxOf(1, summary.perDay.size))} a day", style = MaterialTheme.typography.bodySmall, color = Faint)
                    all.filter { it.status == ReviewStatus.REVIEWED && event.tag in it.tags }.forEach { t -> TransactionRow(t) { onTransaction(t.id) } }
                    Row {
                        TextButton(onClick = { editing = event }) { Text("Edit") }
                        TextButton(onClick = { scope.launch { repository.deleteEvent(event) } }) { Text("Delete", color = Rust) }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        EventDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { event, tagExisting ->
                creating = false
                editing = null
                scope.launch { repository.saveEvent(event, tagExisting) }
            },
        )
    }
}

private val TRIP_EMOJI = listOf("🏖️", "🏔️", "✈️", "💒", "🎉", "🏠", "🎓", "🛕")

@Composable
private fun EventDialog(initial: Event?, onDismiss: () -> Unit, onSave: (Event, Boolean) -> Unit) {
    val today = LocalDate.now(Zone)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji ?: TRIP_EMOJI.first()) }
    var start by remember { mutableStateOf(initial?.start ?: today) }
    var end by remember { mutableStateOf(initial?.end) }
    var ongoing by remember { mutableStateOf(initial?.end == null) }
    var tagExisting by remember { mutableStateOf(true) }
    var emojiMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New trip or event" else "Edit ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { emojiMenu = true }) { Text(emoji) }
                        DropdownMenu(expanded = emojiMenu, onDismissRequest = { emojiMenu = false }) {
                            TRIP_EMOJI.forEach { e ->
                                DropdownMenuItem(text = { Text(e) }, onClick = {
                                    emoji = e
                                    emojiMenu = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it.replace(",", "").take(30) }, label = { Text("Name (like Goa trip)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                DateButton("From", start, { start = it }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Still going", Modifier.weight(1f))
                    Switch(checked = ongoing, onCheckedChange = { ongoing = it })
                }
                if (!ongoing) DateButton("To", end, { end = it }, Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tagExisting, onCheckedChange = { tagExisting = it })
                    Text("Tag the payments already made in these dates")
                }
            }
        },
        confirmButton = {
            val endDate = if (ongoing) null else end
            TextButton(
                enabled = name.isNotBlank() && (endDate == null || !endDate.isBefore(start)) && (ongoing || end != null),
                onClick = { onSave(Event(initial?.id ?: 0, name.trim(), emoji, start, endDate), tagExisting) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Reimbursements

@Composable
fun ReimburseScreen(container: AppContainer, onBack: () -> Unit, onTransaction: (Long) -> Unit) {
    val repository = container.repository
    val all by repository.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val live = remember(all) { all.filter { it.status != ReviewStatus.IGNORED } }
    val pending = live.filter { it.reimburse == Reimburse.PENDING }
    val done = live.filter { it.reimburse == Reimburse.DONE }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var showDone by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            BackHeader(
                "Reimbursements",
                onBack,
                "Payments the office will pay back. They don’t count as your spending. Mark a payment from its page.",
            )
        }
        item {
            Panel(color = WarmTint) {
                Text("Waiting to be paid back", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(Money.format(pending.sumOf { it.amountPaise }), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                Text("${pending.size} payments", color = Faint, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (pending.isEmpty()) {
            item { EmptyState("Nothing to claim", "Open a work payment and turn on “Office will pay this back”.") }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val ids = selected.ifEmpty { pending.map { it.id }.toSet() }
                            selected = emptySet()
                            scope.launch { repository.markReimbursed(ids) }
                        },
                    ) { Text(if (selected.isEmpty()) "All paid back" else "${selected.size} paid back") }
                    if (selected.isNotEmpty()) TextButton(onClick = { selected = emptySet() }) { Text("Clear") }
                }
            }
        }
        items(pending, key = { it.id }) { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = t.id in selected, onCheckedChange = { on -> selected = if (on) selected + t.id else selected - t.id })
                Box(Modifier.weight(1f)) { TransactionRow(t) { onTransaction(t.id) } }
            }
        }
        if (done.isNotEmpty()) {
            item {
                TextButton(onClick = { showDone = !showDone }) {
                    Text("${if (showDone) "▴" else "▾"} Paid back (${done.size} · ${Money.format(done.sumOf { it.amountPaise })})")
                }
            }
            if (showDone) items(done, key = { it.id }) { t -> TransactionRow(t) { onTransaction(t.id) } }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Goals

@Composable
fun GoalsScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.repository
    val goals by repository.goals.collectAsStateWithLifecycle(initialValue = emptyList())
    val all by repository.reviewed.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = LocalDate.now(Zone)
    val progress = remember(goals, all) { goals.map { Goals.progress(it, all, today) } }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var creating by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<Goal?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("Goals", onBack, "Save up for something. Link a goal to SIPs or FDs and those payments count towards it.") }
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ New goal") } }
        if (progress.isEmpty()) {
            item { EmptyState("No goals yet", "An emergency fund, a phone, a trip: pick a target and a date.") }
        }
        items(progress, key = { it.goal.id }) { p ->
            val g = p.goal
            Panel(Modifier.clickable { editing = g }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${g.emoji}  ${g.name}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${Money.format(p.savedPaise)} / ${Money.format(g.targetPaise)}", style = MaterialTheme.typography.bodySmall)
                }
                Bar(p.fraction, color = if (p.reached) ChartStrong else Blue)
                val perMonth = p.perMonthNeededPaise
                Text(
                    when {
                        p.reached -> "Reached 🎉"
                        perMonth != null -> "${Money.format(perMonth)} a month to reach it by ${g.deadline?.shortText()}"
                        else -> "${Money.format(p.leftPaise)} to go"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (g.deadline?.isBefore(today) == true && !p.reached) Rust else Faint,
                )
                g.linkedCategoryId?.let { id -> Text("Counting ${CategoryCatalog().byId(id).label} payments", style = MaterialTheme.typography.bodySmall, color = Faint) }
                if (!p.reached) OutlinedButton(onClick = { adding = g }) { Text("＋ Add money") }
            }
        }
    }

    adding?.let { goal ->
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("Add to ${goal.name}") },
            text = { AmountField(amount, { amount = it }, "Amount put aside") },
            confirmButton = {
                TextButton(enabled = Money.parsePaise(amount) != null, onClick = {
                    val paise = Money.parsePaise(amount) ?: return@TextButton
                    adding = null
                    scope.launch { repository.saveGoal(goal.copy(addedPaise = goal.addedPaise + paise)) }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }

    if (creating || editing != null) {
        GoalDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { goal ->
                creating = false
                editing = null
                scope.launch { repository.saveGoal(goal) }
            },
            onDelete = { goal ->
                editing = null
                scope.launch { repository.deleteGoal(goal.id) }
            },
        )
    }
}

private val GOAL_EMOJI = listOf("🎯", "🛟", "📱", "🏖️", "🚗", "🏠", "💍", "🎓")

@Composable
private fun GoalDialog(initial: Goal?, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onDelete: (Goal) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji ?: GOAL_EMOJI.first()) }
    var target by remember { mutableStateOf(initial?.targetPaise?.let { (it / 100).toString() }.orEmpty()) }
    var deadline by remember { mutableStateOf(initial?.deadline) }
    var linked by remember { mutableStateOf(initial?.linkedCategoryId) }
    var emojiMenu by remember { mutableStateOf(false) }
    var linkMenu by remember { mutableStateOf(false) }
    val savings = remember { Category.BUILT_IN.filter { it.kind == Kind.SAVING } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { emojiMenu = true }) { Text(emoji) }
                        DropdownMenu(expanded = emojiMenu, onDismissRequest = { emojiMenu = false }) {
                            GOAL_EMOJI.forEach { e ->
                                DropdownMenuItem(text = { Text(e) }, onClick = {
                                    emoji = e
                                    emojiMenu = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(name, { name = it.take(30) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                AmountField(target, { target = it }, "Target")
                DateButton("By", deadline, { deadline = it }, Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { linkMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Count payments: ${linked?.let { id -> savings.firstOrNull { it.id == id }?.label } ?: "none"} ▾")
                    }
                    DropdownMenu(expanded = linkMenu, onDismissRequest = { linkMenu = false }) {
                        DropdownMenuItem(text = { Text("None, I’ll add money by hand") }, onClick = {
                            linked = null
                            linkMenu = false
                        })
                        savings.forEach { c ->
                            DropdownMenuItem(text = { Text("${c.emoji}  ${c.label}") }, onClick = {
                                linked = c.id
                                linkMenu = false
                            })
                        }
                    }
                }
                if (initial != null) TextButton(onClick = { onDelete(initial) }) { Text("Delete goal", color = Rust) }
            }
        },
        confirmButton = {
            val paise = Money.parsePaise(target)
            TextButton(enabled = name.isNotBlank() && paise != null, onClick = {
                onSave(
                    Goal(
                        id = initial?.id ?: 0,
                        name = name.trim(),
                        emoji = emoji,
                        targetPaise = paise!!,
                        deadline = deadline,
                        addedPaise = initial?.addedPaise ?: 0,
                        linkedCategoryId = linked,
                        createdOn = initial?.createdOn ?: LocalDate.now(Zone),
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
