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
import androidx.compose.foundation.layout.padding
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
import app.kharcha.core.Transaction
import kotlinx.coroutines.launch
import java.time.Instant
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
    modifier: Modifier = Modifier,
) {
    val repository = container.repository
    val scope = rememberCoroutineScope()
    var confirmAcceptAll by remember { mutableStateOf(false) }
    val byDay = remember(pending) { pending.groupBy { it.localDate() } }

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
        if (pending.isEmpty()) {
            item {
                Panel(color = CoolTint) {
                    Text("✓  All caught up", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("New payments will appear here, and you’ll get a notification after each one.", color = Muted)
                }
            }
        }
        byDay.forEach { (day, dayItems) ->
            item(key = "day-$day") {
                SectionLabel("${day.dayHeader()} · ${dayItems.size} to review", Modifier.padding(top = 6.dp))
            }
            items(dayItems, key = { it.id }) { t ->
                ReviewCard(
                    t = t,
                    catalog = catalog,
                    quickPicks = usage,
                    onReview = { category, remember, note ->
                        scope.launch { repository.review(t.id, category, remember, note) }
                    },
                    onIgnore = { scope.launch { repository.ignore(t.id) } },
                    onCreateCategory = { label, emoji, counts, remember, note ->
                        scope.launch {
                            val created = repository.saveCategory(Category.custom(label, emoji, counts))
                            repository.review(t.id, created, remember, note)
                        }
                    },
                )
            }
        }
        if (pending.size > 1) {
            item {
                TextButton(onClick = { confirmAcceptAll = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Accept all ${pending.size} with suggested categories")
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (confirmAcceptAll) {
        AlertDialog(
            onDismissRequest = { confirmAcceptAll = false },
            title = { Text("Accept all?") },
            text = { Text("All ${pending.size} payments will be counted with the categories shown. You can still change any of them later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmAcceptAll = false
                    val ids = pending.map { it.id }
                    scope.launch { ids.forEach { repository.accept(it) } }
                }) { Text("Accept all") }
            },
            dismissButton = { TextButton(onClick = { confirmAcceptAll = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(
    t: Transaction,
    catalog: CategoryCatalog,
    quickPicks: List<Category>,
    onReview: (Category, Boolean, String?) -> Unit,
    onIgnore: () -> Unit,
    onCreateCategory: (label: String, emoji: String, countsAsSpend: Boolean, remember: Boolean, note: String?) -> Unit,
) {
    var category by remember(t.id) { mutableStateOf(t.category) }
    var rememberForMerchant by remember(t.id) { mutableStateOf(true) }
    var note by remember(t.id) { mutableStateOf(t.note.orEmpty()) }
    var showMessage by remember(t.id) { mutableStateOf(false) }
    var moreOpen by remember(t.id) { mutableStateOf(false) }
    var creating by remember(t.id) { mutableStateOf(false) }
    val chips = remember(t.id, quickPicks) { (listOf(t.category) + quickPicks).distinctBy { it.id }.take(6) }
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
                    catalog.all.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.emoji}  ${option.label}") },
                            onClick = {
                                category = option
                                moreOpen = false
                            },
                        )
                    }
                }
            }
        }
        if (chips.none { it.id == category.id }) {
            Text("Selected: ${category.emoji} ${category.label}", style = MaterialTheme.typography.bodySmall, color = Muted)
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
            onSave = { label, emoji, counts ->
                creating = false
                onCreateCategory(label, emoji, counts, rememberForMerchant, note.takeIf { it.isNotBlank() })
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
