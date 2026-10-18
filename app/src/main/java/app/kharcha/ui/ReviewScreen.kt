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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import app.kharcha.core.Confidence
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
import androidx.compose.material3.OutlinedButton
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
import app.kharcha.core.CategoryMatch
import app.kharcha.core.Direction
import app.kharcha.core.Kind
import app.kharcha.core.Merchants
import app.kharcha.core.Tags
import app.kharcha.core.Transaction
import app.kharcha.data.UnreadMessageEntity
import app.kharcha.data.MessageState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onUndoable: (message: String, undo: suspend () -> Unit) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier,
    streak: Int = 0,
) {
    var showMessages by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val waitingMessages by container.repository.toDecide.collectAsStateWithLifecycle(initialValue = emptyList())
            listOf(false to "To review (${pending.size + waitingMessages.size})", true to "History").forEach { (value, label) ->
                if (showMessages == value) {
                    Button(onClick = {}) { Text(label) }
                } else {
                    OutlinedButton(onClick = { showMessages = value }) { Text(label) }
                }
            }
        }
        if (showMessages) {
            MessagesView(container, onOpenTransaction, onUndoable, Modifier.padding(top = 8.dp))
        } else {
            PaymentsReview(container, pending, catalog, usage, lookup, unreadCount, onOpenUnread, onUndoable, Modifier, streak)
        }
    }
}

@Composable
private fun PaymentsReview(
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
    streak: Int = 0,
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
    // Every message nobody has decided on yet sits in the same list as the payments.
    val messages by repository.toDecide.collectAsStateWithLifecycle(initialValue = emptyList())
    var adding by remember { mutableStateOf<UnreadMessageEntity?>(null) }
    var askingDate by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf<app.kharcha.capture.ScanProgress?>(null) }
    val shownMessages = remember(messages, query, filter) {
        val q = query.trim().lowercase()
        messages.filter { m ->
            (q.isEmpty() || m.body.lowercase().contains(q) || m.sender.lowercase().contains(q)) && filter.acceptsDay(dateOf(m.receivedAt), today)
        }
    }
    val total = pending.size + messages.size
    val byDay = remember(shown, shownMessages, sort) {
        val items = shown.map { ReviewItem.Pay(it) } + shownMessages.map { ReviewItem.Msg(it) }
        when (sort) {
            ReviewSort.LARGEST -> mapOf<LocalDate?, List<ReviewItem>>(null to (shown.map { ReviewItem.Pay(it) } + shownMessages.map { ReviewItem.Msg(it) }))
            ReviewSort.OLDEST -> items.sortedBy { it.time }.groupBy<ReviewItem, LocalDate?> { dateOf(it.time) }
            ReviewSort.NEWEST -> items.sortedByDescending { it.time }.groupBy<ReviewItem, LocalDate?> { dateOf(it.time) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("To review", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (streak > 1) {
                    Text("🔥 $streak-day streak of clearing your inbox", color = Rust, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    if (total == 0) {
                        "You’re all caught up."
                    } else {
                        "${pending.size} payment${if (pending.size == 1) "" else "s"} and ${messages.size} other message${if (messages.size == 1) "" else "s"} to go through. " +
                            "Payments count in your totals once reviewed; tell the app which messages aren’t payments."
                    },
                    color = Muted,
                )
            }
        }
        // A big backlog (often from the first inbox scan): offer to accept the old ones in one go.
        val weekAgo = today.minusDays(6).atStartOfDay(Zone).toInstant().toEpochMilli()
        val old = pending.filter { it.timestampMillis < weekAgo }
        if (old.size >= 20) {
            item {
                Panel(color = CoolTint) {
                    Text("${old.size} of these are more than a week old", fontWeight = FontWeight.Bold)
                    Text(
                        "Accept them as they are and review just the last 7 days. You can still change any of them later.",
                        color = Muted,
                    )
                    Button(onClick = {
                        val ids = old.map { it.id }
                        scope.launch { ids.forEach { repository.accept(it) } }
                        onUndoable("Accepted ${ids.size} older payments") { ids.forEach { repository.sendBackToReview(it) } }
                    }) { Text("Accept ${old.size} older payments") }
                }
            }
        }
        if (!container.prefs.allMessagesLoaded) {
            item {
                Panel(color = WarmTint) {
                    Text("Bring in the rest of your inbox", fontWeight = FontWeight.Bold)
                    Text("Read your SMS from a date you pick; every message (never one-time codes) lands here to sort.", color = Muted)
                    Button(enabled = !scanning, onClick = { askingDate = true }) {
                        Text(if (scanning) "Reading… ${scanned?.checked ?: 0} of ${scanned?.total ?: 0}" else "Read my inbox…")
                    }
                }
            }
        }
        val oldMessages = messages.filter { it.receivedAt < weekAgo }
        if (oldMessages.size >= 20) {
            item {
                Panel(color = CoolTint) {
                    Text("${oldMessages.size} messages are more than a week old", fontWeight = FontWeight.Bold)
                    Text("Set them aside as not payments and go through just the last 7 days. They stay in All messages.", color = Muted)
                    Button(onClick = {
                        val states = oldMessages.map { it.id to it.state }
                        scope.launch { repository.setAsideMessages(states.map { it.first }) }
                        onUndoable("Set aside ${states.size} older messages") { repository.restoreMessages(states) }
                    }) { Text("Set aside ${oldMessages.size} older messages") }
                }
            }
        }
        if (total > 0) {
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
                            val count = pending.count { it.matches(query) && option.accepts(it, today) } +
                                messages.count { option.acceptsDay(dateOf(it.receivedAt), today) && (query.isBlank() || it.body.contains(query.trim(), ignoreCase = true)) }
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
        if (total > 0 && shown.isEmpty() && shownMessages.isEmpty()) {
            item { EmptyState("No matches", "Nothing waiting for review matches this search or filter.") }
        }
        if (total == 0) {
            item {
                Panel(color = CoolTint) {
                    Text("✓  All caught up", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("New payments and messages will appear here as they arrive.", color = Muted)
                }
            }
        }
        byDay.forEach { (day, dayItems) ->
            if (day != null) {
                item(key = "day-$day") {
                    SectionLabel("${day.dayHeader()} · ${dayItems.size} to review", Modifier.padding(top = 6.dp))
                }
            }
            items(dayItems, key = { it.key }) { item ->
                if (item is ReviewItem.Msg) {
                    val m = item.m
                    MessageDecisionCard(
                        message = m,
                        onPayment = { adding = m },
                        onNotPayment = {
                            scope.launch {
                                val result = repository.markNotMoney(m.id) ?: return@launch
                                onUndoable("Marked not a payment") { repository.undoNotMoney(result) }
                            }
                        },
                    )
                    return@items
                }
                val t = (item as ReviewItem.Pay).t
                ReviewCard(
                    t = t,
                    catalog = catalog,
                    quickPicks = usage,
                    twin = t.possibleDuplicateOf?.let(lookup),
                    onReview = { type, tags, remember, note ->
                        scope.launch { repository.reviewTagged(t.id, type, tags, remember, note) }
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
                    onNotMoney = {
                        scope.launch {
                            val result = repository.markTransactionNotMoney(t.id) ?: return@launch
                            onUndoable("Marked not a payment") { repository.undoNotMoney(result) }
                        }
                    },
                    createCategory = { label, emoji -> repository.saveCategory(Category.forTag(label, emoji)) },
                )
            }
        }
        val sure = shown.filter { it.confidence == Confidence.SURE && it.possibleDuplicateOf == null }
        if (sure.size > 1) {
            item {
                OutlinedButton(
                    onClick = {
                        val ids = sure.map { it.id }
                        scope.launch { ids.forEach { repository.accept(it) } }
                        onUndoable("Accepted ${ids.size} sure payments") { ids.forEach { repository.sendBackToReview(it) } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("✓ Accept the ${sure.size} marked Sure") }
            }
        }
        if (shown.size > 1) {
            item {
                TextButton(onClick = { confirmAcceptAll = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (isFiltered) {
                            "Accept these ${shown.size} as they are"
                        } else {
                            "Accept all ${shown.size} as they are"
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (askingDate) {
        ScanDateDialog(onDismiss = { askingDate = false }, onPick = { date ->
            askingDate = false
            scanning = true
            scope.launch {
                container.scanner.scan(date.atStartOfDay(Zone).toInstant().toEpochMilli()) { scanned = it }
                container.prefs.allMessagesLoaded = true
                scanning = false
            }
        })
    }

    adding?.let { message ->
        AddFromMessageDialog(
            message = message,
            catalog = catalog,
            onDismiss = { adding = null },
            onSave = { amount, direction, payee, category ->
                adding = null
                scope.launch { repository.addFromMessage(message, amount, direction, payee, category) }
            },
        )
    }

    if (confirmAcceptAll) {
        AlertDialog(
            onDismissRequest = { confirmAcceptAll = false },
            title = { Text("Accept all?") },
            text = { Text("These ${shown.size} payments will be counted with the tags shown. You can still change any of them later.") },
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

/** One row in Review: a payment to confirm, or a message to sort into payment / not a payment. */
private sealed interface ReviewItem {
    val time: Long
    val key: String

    data class Pay(val t: Transaction) : ReviewItem {
        override val time get() = t.timestampMillis
        override val key get() = "t${t.id}"
    }

    data class Msg(val m: UnreadMessageEntity) : ReviewItem {
        override val time get() = m.receivedAt
        override val key get() = "m${m.id}"
    }
}

private fun dateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(Zone).toLocalDate()

/** A message the app didn't read as a payment: you say whether it is one. */
@Composable
private fun MessageDecisionCard(message: UnreadMessageEntity, onPayment: () -> Unit, onNotPayment: () -> Unit) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    Panel(Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${message.sender} · ${Instant.ofEpochMilli(message.receivedAt).atZone(Zone).format(timeFormat)}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
                maxLines = 1,
            )
            Text(
                if (message.state == MessageState.UNREAD) "Mentions money" else "Message",
                style = MaterialTheme.typography.labelSmall,
                color = if (message.state == MessageState.UNREAD) Rust else Faint,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(message.body, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 4)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNotPayment) { Text("Not a payment") }
            OutlinedButton(onClick = onPayment) { Text("It’s a payment") }
        }
    }
}

private enum class ReviewFilter(val label: String) {
    ALL("All"),
    GUESSES("Guesses"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    WEEK("This week"),
    MONEY_IN("Money in"),
    LARGE("₹1,000+"),
    ;

    fun accepts(t: Transaction, today: LocalDate): Boolean = when (this) {
        ALL -> true
        GUESSES -> t.confidence == Confidence.GUESS || t.possibleDuplicateOf != null
        TODAY -> t.localDate() == today
        YESTERDAY -> t.localDate() == today.minusDays(1)
        WEEK -> t.localDate() > today.minusDays(7)
        MONEY_IN -> t.direction == Direction.CREDIT
        LARGE -> t.amountPaise >= 1_000_00
    }

    /** Messages only filter by day; the payment-only filters leave them out. */
    fun acceptsDay(day: LocalDate, today: LocalDate): Boolean = when (this) {
        ALL -> true
        TODAY -> day == today
        YESTERDAY -> day == today.minusDays(1)
        WEEK -> day > today.minusDays(7)
        GUESSES, MONEY_IN, LARGE -> false
    }
}

private enum class ReviewSort(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), LARGEST("Largest") }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReviewCard(
    t: Transaction,
    catalog: CategoryCatalog,
    quickPicks: List<Category>,
    twin: Transaction?,
    onReview: (type: Category?, tags: List<String>, remember: Boolean, note: String?) -> Unit,
    onIgnore: () -> Unit,
    onConfirmDuplicate: () -> Unit,
    onNotDuplicate: () -> Unit,
    onNotMoney: () -> Unit,
    createCategory: suspend (label: String, emoji: String) -> Category,
) {
    val brand = remember(t.id) { Merchants.brandOf(t) }
    val multiBusiness = brand?.isMultiBusiness == true
    var tags by remember(t.id) { mutableStateOf(t.tags) }
    // Set only for money that isn't spending (a transfer, ATM cash...); spending is described by tags.
    var type by remember(t.id) { mutableStateOf(t.category.takeIf { !it.countsAsSpend }) }
    val category = type ?: tags.firstOrNull()?.let(catalog::tag) ?: Category.OTHER
    val summary = when {
        type != null -> "${category.emoji} ${category.label}".trim() + if (tags.isEmpty()) "" else " · " + tags.joinToString(", ")
        tags.isNotEmpty() -> tags.joinToString(", ")
        else -> "No tags yet"
    }
    // One choice shouldn't relabel every payment to a merchant that sells several things.
    var rememberForMerchant by remember(t.id) { mutableStateOf(!multiBusiness) }
    var note by remember(t.id) { mutableStateOf(t.note.orEmpty()) }
    var showMessage by remember(t.id) { mutableStateOf(false) }
    var sheetOpen by remember(t.id) { mutableStateOf(false) }
    // Sure payments start folded to one line; guesses and possible duplicates open, so you look at them.
    var expanded by remember(t.id) { mutableStateOf(t.confidence == Confidence.GUESS || twin != null) }
    val accept = { onReview(type, tags, rememberForMerchant, note.takeIf { it.isNotBlank() }) }
    var creating by remember(t.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // What the app thinks this is ("Food delivery", or each thing a store like Swiggy sells).
    val hints = remember(t.id) { (listOfNotNull(t.hint) + brand?.choices.orEmpty().map { it.label }).distinct() }
    val chips = remember(t.id, quickPicks, catalog) {
        val fromHints = hints.mapNotNull { CategoryMatch.best(it, catalog.tags) }
        (fromHints + quickPicks).distinctBy { it.id }.take(6)
    }
    val ideas = remember(t.id, catalog) { hints.filter { CategoryMatch.best(it, catalog.tags) == null }.take(2) }
    val time = Instant.ofEpochMilli(t.timestampMillis).atZone(Zone).format(timeFormat)
    val via = listOfNotNull(t.bank, t.accountLast4?.let { "··$it" }).joinToString(" ").ifEmpty {
        if (t.source.name == "NOTIFICATION") "UPI app" else "Bank SMS"
    }

    Panel(Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EmojiBadge(if (t.isSplit) "✂️" else category.emoji, size = 40)
            Column(Modifier.weight(1f)) {
                Text(t.merchant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("$time · $via", style = MaterialTheme.typography.bodySmall, color = Faint, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    t.signedAmount(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (t.direction == Direction.CREDIT) Blue else Navy,
                )
                ConfidenceTag(t.confidence)
            }
        }
        if (!expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (type == null && tags.isEmpty()) Rust else Muted,
                    maxLines = 1,
                )
                TextButton(onClick = { sheetOpen = true }) { Text("Change") }
                Button(onClick = accept) { Text("✓") }
            }
        } else {

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
                "Money in. If it’s a refund, give it the tag it came back from: it will lower that tag’s spending instead of counting as income.",
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
            type?.let { CategoryChip("${it.emoji} ${it.label}  ✕", selected = true) { type = null } }
            tags.forEach { name ->
                val tag = catalog.tag(name)
                CategoryChip("${tag.emoji} ${tag.label}  ✕", selected = true) { tags = tags - name }
            }
            chips.filter { it.label !in tags }.forEach { option ->
                CategoryChip(option, selected = false) { tags = tags + option.label }
            }
            ideas.filter { Tags.clean(it) !in tags }.forEach { idea -> IdeaChip(idea) { creating = idea } }
            CategoryChip(label = "＋ Tag 🔍", selected = false) { sheetOpen = true }
        }
        if (type == null && tags.isEmpty()) {
            Text("Add one or more tags: tap a suggestion or type one.", style = MaterialTheme.typography.bodySmall, color = Rust)
        } else if (tags.size > 1) {
            Text("“${tags.first()}” counts in your totals; each tag also gets its own total.", style = MaterialTheme.typography.bodySmall, color = Faint)
        }
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
            Button(onClick = accept) {
                Text("✓ Reviewed")
            }
        }
        TextButton(onClick = onNotMoney) { Text("Not a payment", color = Faint) }
        }
    }

    if (sheetOpen) {
        TagPickerSheet(
            catalog = catalog,
            selected = tags,
            type = type,
            hints = hints,
            onDone = { newType, newTags ->
                type = newType
                tags = newTags
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
            onCreate = createCategory,
        )
    }

    creating?.let { name ->
        CreateCategoryDialog(
            name = name,
            similar = CategoryMatch.find(name, catalog.tags).close,
            onUseExisting = {
                creating = null
                if (it.label !in tags) tags = tags + it.label
            },
            onDismiss = { creating = null },
            onCreate = { label, emoji ->
                creating = null
                scope.launch {
                    val created = createCategory(label, emoji)
                    if (created.label !in tags) tags = tags + created.label
                }
            },
        )
    }
}

@Composable
fun ConfidenceTag(confidence: Confidence) {
    val (label, color) = when (confidence) {
        Confidence.SURE -> "Sure" to Blue
        Confidence.LIKELY -> "Likely" to Faint
        Confidence.GUESS -> "Guess" to Rust
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) =
    CategoryChip("${category.emoji} ${category.label}".trim(), selected, onClick)

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
