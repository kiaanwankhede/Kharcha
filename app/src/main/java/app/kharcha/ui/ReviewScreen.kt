package app.kharcha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kharcha.AppContainer
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Direction
import app.kharcha.core.Kind
import app.kharcha.core.Merchants
import app.kharcha.core.Transaction
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/**
 * The review inbox: every captured payment waits here until the user has looked at it and confirmed
 * the category. Nothing counts in totals until it has been reviewed.
 */
@Composable
fun ReviewScreen(
    container: AppContainer,
    pending: List<Transaction>,
    catalog: CategoryCatalog,
    usage: List<Category>,
    lookup: (Long) -> Transaction?,
    unreadCount: Int,
    onOpenUnread: () -> Unit,
    /** Shows a message with an Undo action that runs [undo]. */
    onUndoable: (message: String, undo: suspend () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = container.repository
    val scope = rememberCoroutineScope()
    var confirmAcceptAll by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ReviewFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(ReviewSort.NEWEST) }
    val today = LocalDate.now(Zone)
    val shown = remember(pending, query, filter, sort) {
        pending.filter { it.matches(query) && filter.accepts(it, today) }.let { list ->
            when (sort) {
                ReviewSort.NEWEST -> list.sortedByDescending { it.timestampMillis }
                ReviewSort.OLDEST -> list.sortedBy { it.timestampMillis }
                ReviewSort.LARGEST -> list.sortedByDescending { it.amountPaise }
            }
        }
    }
    val isFiltered = query.isNotBlank() || filter != ReviewFilter.ALL
    val byDay = remember(shown, sort) {
        if (sort == ReviewSort.LARGEST) mapOf<LocalDate?, List<Transaction>>(null to shown) else shown.groupBy<Transaction, LocalDate?> { it.localDate() }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("To review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (pending.isEmpty()) {
                        "You’re all caught up."
                    } else {
                        "${pending.size} payment${if (pending.size == 1) "" else "s"} waiting. They count in your totals once reviewed."
                    },
                    color = Muted,
                )
            }
        }
        if (unreadCount > 0) {
            item {
                Panel(Modifier.clickable(onClick = onOpenUnread), color = WarmTint) {
                    Text(
                        "📭  $unreadCount bank message${if (unreadCount == 1) "" else "s"} we couldn’t read",
                        fontWeight = FontWeight.Bold,
                    )
                    Text("They mention money but in a format the app doesn’t know yet. Tap to add them yourself.", color = Muted)
                }
            }
        }
        if (pending.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search payee, amount, UPI ID, note, SMS…") },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("✕") }
                        },
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ReviewFilter.entries.forEach { option ->
                            val count = pending.count { it.matches(query) && option.accepts(it, today) }
                            CategoryChip("${option.label} ($count)", selected = filter == option) { filter = option }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sort:", style = MaterialTheme.typography.bodySmall, color = Faint)
                        ReviewSort.entries.forEach { option ->
                            Text(
                                option.label,
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { sort = option }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (sort == option) Blue else Faint,
                                fontWeight = if (sort == option) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        if (pending.isNotEmpty() && shown.isEmpty()) {
            item { EmptyState("No matches", "Nothing waiting for review matches this search or filter.") }
        }
        if (pending.isEmpty()) {
            item {
                Panel(color = CoolTint) {
                    Text("✓  All caught up", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("New payments will appear here, and you’ll get a notification after each one.", color = Muted)
                }
            }
        }
        byDay.forEach { (day, dayItems) ->
            if (day != null) {
                item(key = "day-$day") {
                    SectionLabel("${day.dayHeader()} · ${dayItems.size} to review", Modifier.padding(top = 6.dp))
                }
            }
            items(dayItems, key = { it.id }) { t ->
                ReviewCard(
                    t = t,
                    catalog = catalog,
                    quickPicks = usage,
                    twin = t.possibleDuplicateOf?.let(lookup),
                    onReview = { category, remember, note ->
                        scope.launch { repository.review(t.id, category, remember, note) }
                        onUndoable("Reviewed ${t.merchant}") { repository.sendBackToReview(t.id) }
                    },
                    onIgnore = {
                        scope.launch { repository.ignore(t.id) }
                        onUndoable("Ignored ${t.merchant}") { repository.sendBackToReview(t.id) }
                    },
                    onConfirmDuplicate = {
                        scope.launch { repository.confirmDuplicate(t.id) }
                        onUndoable("Ignored as a duplicate") { repository.sendBackToReview(t.id) }
                    },
                    onNotDuplicate = { scope.launch { repository.notDuplicate(t.id) } },
                    onCreateCategory = { label, emoji, parent, remember, note ->
                        scope.launch {
                            val created = repository.saveCategory(Category.custom(label, emoji, parent))
                            repository.review(t.id, created, remember, note)
                        }
                    },
                )
            }
        }
        if (shown.size > 1) {
            item {
                TextButton(onClick = { confirmAcceptAll = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (isFiltered) {
                            "Accept these ${shown.size} with suggested categories"
                        } else {
                            "Accept all ${shown.size} with suggested categories"
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (confirmAcceptAll) {
        AlertDialog(
            onDismissRequest = { confirmAcceptAll = false },
            title = { Text("Accept all?") },
            text = { Text("These ${shown.size} payments will be counted with the categories shown. You can still change any of them later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAcceptAll = false
                    val ids = shown.map { it.id }
                    scope.launch { ids.forEach { repository.accept(it) } }
                    onUndoable("Accepted ${ids.size} payments") { ids.forEach { repository.sendBackToReview(it) } }
                }) { Text("Accept all") }
            },
            dismissButton = { TextButton(onClick = { confirmAcceptAll = false }) { Text("Cancel") } },
        )
    }
}

private enum class ReviewFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("This week"),
    MONEY_IN("Money in"),
    LARGE("₹1,000+"),
    ;

    fun accepts(t: Transaction, today: LocalDate): Boolean = when (this) {
        ALL -> true
        TODAY -> t.localDate() == today
        YESTERDAY -> t.localDate() == today.minusDays(1)
        WEEK -> t.localDate() > today.minusDays(7)
        MONEY_IN -> t.direction == Direction.CREDIT
        LARGE -> t.amountPaise >= 1_000_00
    }
}

private enum class ReviewSort(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), LARGEST("Largest") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    t: Transaction,
    catalog: CategoryCatalog,
    quickPicks: List<Category>,
    twin: Transaction?,
    onReview: (Category, Boolean, String?) -> Unit,
    onIgnore: () -> Unit,
    onConfirmDuplicate: () -> Unit,
    onNotDuplicate: () -> Unit,
    onCreateCategory: (label: String, emoji: String, parent: Category, remember: Boolean, note: String?) -> Unit,
) {
    val brand = remember(t.id) { Merchants.brandOf(t) }
    val multiBusiness = brand?.isMultiBusiness == true
    var category by remember(t.id) { mutableStateOf(t.category) }
    // One choice shouldn't relabel every payment to a merchant that sells several things.
    var rememberForMerchant by remember(t.id) { mutableStateOf(!multiBusiness) }
    var note by remember(t.id) { mutableStateOf(t.note.orEmpty()) }
    var showMessage by remember(t.id) { mutableStateOf(false) }
    var moreOpen by remember(t.id) { mutableStateOf(false) }
    var creating by remember(t.id) { mutableStateOf(false) }
    val chips = remember(t.id, quickPicks) {
        val choices = brand?.choices.orEmpty()
        (listOf(t.category) + choices + quickPicks + Category.WALLET).distinctBy { it.id }.take(maxOf(7, choices.size + 2))
    }
    val time = Instant.ofEpochMilli(t.timestampMillis).atZone(Zone).format(timeFormat)
    val via = listOfNotNull(t.bank, t.accountLast4?.let { "··$it" }).joinToString(" ").ifEmpty {
        if (t.source.name == "NOTIFICATION") "UPI app" else "Bank SMS"
    }

    Panel {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(t.merchant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("$time · $via", style = MaterialTheme.typography.bodySmall, color = Faint)
            }
            Text(
                t.signedAmount(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (t.direction == Direction.CREDIT) Blue else Navy,
            )
        }

        if (twin != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarmTint)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Possible duplicate", fontWeight = FontWeight.Bold, color = Rust)
                Text(
                    "Same amount as ${twin.merchant} at ${Instant.ofEpochMilli(twin.timestampMillis).atZone(Zone).format(timeFormat)}" +
                        " (${if (twin.source.name == "NOTIFICATION") "app notification" else "bank SMS"}). Is this the same payment?",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirmDuplicate) { Text("Same — ignore this") }
                    TextButton(onClick = onNotDuplicate) { Text("Different payment") }
                }
            }
        }
        if (t.direction == Direction.CREDIT) {
            Text(
                "Money in. File it under a spending category (e.g. Food delivery) if it’s a refund: it will lower that category instead of counting as income.",
                style = MaterialTheme.typography.bodySmall,
                color = Blue,
            )
        }
        Text(
            if (showMessage) "Hide original message" else "Show original message",
            Modifier.clickable { showMessage = !showMessage },
            style = MaterialTheme.typography.bodySmall,
            color = Blue,
        )
        if (showMessage) {
            Text(
                t.rawText,
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Sand)
                    .padding(10.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }

        Text("What was this?", style = MaterialTheme.typography.labelLarge, color = Muted)
        if (multiBusiness && brand != null) {
            Text(
                "${brand.name} sells more than one thing (${brand.choices.joinToString(", ") { it.label.lowercase() }}) and the bank SMS doesn’t say which. Pick the right one.",
                style = MaterialTheme.typography.bodySmall,
                color = Rust,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { option ->
                CategoryChip(option, selected = option.id == category.id) { category = option }
            }
            Box {
                CategoryChip(label = "More…", selected = chips.none { it.id == category.id }) { moreOpen = true }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("＋ New category…", fontWeight = FontWeight.Bold) },
                        onClick = {
                            moreOpen = false
                            creating = true
                        },
                    )
                    CategoryMenuItems(catalog) { option ->
                        category = option
                        moreOpen = false
                    }
                }
            }
        }
        Text(
            "${catalog.groupOf(category).emoji} ${catalog.groupOf(category).label} › ${category.emoji} ${category.label}",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        when (category.kind) {
            Kind.SAVING -> Text("Saving & investing: shown as saved, not spent.", style = MaterialTheme.typography.bodySmall, color = Blue)
            Kind.INCOME -> Text("Money in: not counted as spending.", style = MaterialTheme.typography.bodySmall, color = Blue)
            else -> Unit
        }
        when (category.id) {
            Category.WALLET.id -> Text(
                "Money moved to your wallet or UPI Lite: not counted as spending. What you pay from the wallet is.",
                style = MaterialTheme.typography.bodySmall,
                color = Blue,
            )
            Category.CASH.id -> Text(
                "ATM cash: not counted as spending by itself. Log cash spends with ＋ Add cash; anything not logged still counts.",
                style = MaterialTheme.typography.bodySmall,
                color = Blue,
            )
            Category.TRANSFER.id, Category.CARD_BILL.id -> Text(
                "Between your own accounts: not counted as spending.",
                style = MaterialTheme.typography.bodySmall,
                color = Blue,
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Note (optional) — e.g. dinner with Rahul") },
            singleLine = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberForMerchant, onCheckedChange = { rememberForMerchant = it })
            Text("Always use this for ${t.merchant}", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onIgnore) { Text("Ignore", color = Faint) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onReview(category, rememberForMerchant, note.takeIf { it.isNotBlank() }) }) {
                Text("✓ Reviewed")
            }
        }
    }

    if (creating) {
        CategoryEditorDialog(
            initial = null,
            onDismiss = { creating = false },
            defaultGroup = catalog.groupOf(category),
            groups = catalog.groups,
            onSave = { label, emoji, parent ->
                creating = false
                onCreateCategory(label, emoji, parent, rememberForMerchant, note.takeIf { it.isNotBlank() })
            },
        )
    }
}

@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) =
    CategoryChip("${category.emoji} ${category.label}", selected, onClick)

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Navy else Sand)
            .then(if (selected) Modifier else Modifier.border(1.dp, Line, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) Paper else Navy,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}
