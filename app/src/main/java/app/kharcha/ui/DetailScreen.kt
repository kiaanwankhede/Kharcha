package app.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kharcha.AppContainer
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Direction
import app.kharcha.core.Merchants
import app.kharcha.core.Money
import app.kharcha.core.Reimburse
import app.kharcha.core.Tags
import app.kharcha.core.ReviewStatus
import app.kharcha.core.Source
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(container: AppContainer, id: Long, onBack: () -> Unit, onOpenMerchant: (String) -> Unit = {}) {
    val repository = container.repository
    val flow = remember(id) { repository.transaction(id) }
    val t by flow.collectAsStateWithLifecycle(initialValue = null)
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    var creatingCategory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var rememberForMerchant by rememberSaveable(t?.id) { mutableStateOf(t?.let { Merchants.brandOf(it)?.isMultiBusiness } != true) }
    var note by rememberSaveable(t?.id) { mutableStateOf(t?.note.orEmpty()) }
    var renaming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var splitting by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        val txn = t ?: return@Column
        Text("${txn.merchant} · ${txn.dateTimeText()}", color = Faint)
        Text(
            Money.format(txn.amountPaise, showPaise = txn.amountPaise % 100 != 0L),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = if (txn.direction == Direction.CREDIT) Blue else Navy,
        )
        Text(if (txn.direction == Direction.CREDIT) "Money in" else "Money out", color = Muted)
        TextButton(onClick = { onOpenMerchant(txn.merchantKey) }) { Text("Everything with ${txn.merchant} ›") }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { editing = true }) { Text("✎ Amount / date") }
            TextButton(onClick = { renaming = true }) { Text("Rename payee") }
            if (txn.direction == Direction.DEBIT) TextButton(onClick = { splitting = true }) { Text("Split") }
        }
        if (txn.isSplit) {
            Panel(color = CoolTint) {
                Text("Split across ${txn.splits.size} categories", fontWeight = FontWeight.Bold)
                txn.splits.forEach { part ->
                    Row {
                        Text("${part.category.emoji}  ${part.category.label}", Modifier.weight(1f))
                        Text(Money.format(part.amountPaise, showPaise = part.amountPaise % 100 != 0L))
                    }
                }
                TextButton(onClick = { scope.launch { repository.setSplits(txn.id, emptyList()) } }) { Text("Remove split") }
            }
        }

        Panel(color = Sand) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category", Modifier.weight(1f), color = Muted)
                Box {
                    TextButton(onClick = { menuOpen = true }) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${txn.category.emoji} ${txn.category.label}  ▾", fontWeight = FontWeight.Bold)
                            txn.hint?.let { Text("Looks like $it", style = MaterialTheme.typography.bodySmall, color = Faint) }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Always use this for ${txn.merchant}", Modifier.weight(1f), color = Blue, fontWeight = FontWeight.SemiBold)
                Switch(checked = rememberForMerchant, onCheckedChange = { rememberForMerchant = it })
            }
        }

        Panel(color = Sand) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tags", Modifier.weight(1f), color = Muted)
                TextButton(onClick = { tagging = true }) { Text("＋ Tag") }
            }
            if (txn.tags.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    txn.tags.forEach { tag ->
                        AssistChip(onClick = { scope.launch { repository.setTags(txn.id, txn.tags - tag) } }, label = { Text("$tag  ✕") })
                    }
                }
            }
            if (txn.direction == Direction.DEBIT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Office will pay this back", fontWeight = FontWeight.SemiBold)
                        Text(
                            when (txn.reimburse) {
                                Reimburse.PENDING -> "Not counted as your spending · waiting"
                                Reimburse.DONE -> "Not counted as your spending · paid back ✓"
                                null -> "Takes it out of your spending"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Faint,
                        )
                    }
                    Switch(
                        checked = txn.reimburse != null,
                        onCheckedChange = { on -> scope.launch { repository.setReimburse(txn.id, if (on) Reimburse.PENDING else null) } },
                    )
                }
            }
        }

        Panel(color = Sand) {
            Detail("Account", listOfNotNull(txn.bank, txn.accountLast4?.let { "··$it" }).joinToString(" ").ifEmpty { "—" })
            txn.vpa?.let { Detail("UPI ID", it) }
            txn.reference?.let { Detail("UPI reference", it) }
            txn.balancePaise?.let { Detail("Balance after", Money.format(it, showPaise = true)) }
            Detail(
                "From",
                when (txn.source) {
                    Source.SMS -> "Bank SMS"
                    Source.NOTIFICATION -> "App notification"
                    Source.MANUAL -> "Added by you"
                },
            )
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
        )
        OutlinedButton(onClick = { scope.launch { repository.setNote(txn.id, note) } }) { Text("Save note") }

        when (txn.status) {
            ReviewStatus.PENDING -> Button(onClick = { scope.launch { repository.accept(txn.id) } }) { Text("✓ Mark reviewed") }
            ReviewStatus.REVIEWED -> OutlinedButton(onClick = { scope.launch { repository.sendBackToReview(txn.id) } }) {
                Text("Send back to review")
            }
            ReviewStatus.IGNORED -> OutlinedButton(onClick = { scope.launch { repository.sendBackToReview(txn.id) } }) {
                Text("Un-ignore (back to review)")
            }
        }
        if (txn.status != ReviewStatus.IGNORED && txn.source != Source.MANUAL) {
            TextButton(onClick = { scope.launch { repository.ignore(txn.id) } }) { Text("Ignore — not a real spend", color = Faint) }
        }

        if (txn.source == Source.MANUAL) {
            OutlinedButton(onClick = {
                scope.launch {
                    repository.delete(txn.id)
                    onBack()
                }
            }) { Text("Delete this cash entry", color = Rust) }
        }

        SectionLabel("Original message")
        Text(txn.rawText, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Muted)

        if (menuOpen) {
            CategoryPickerSheet(
                catalog = catalog,
                selected = txn.category,
                hints = (listOfNotNull(txn.hint) + Merchants.brandOf(txn)?.choices.orEmpty().map { it.label }).distinct(),
                onCreate = { label, emoji -> repository.saveCategory(Category.mine(label, emoji)) },
                onPick = { category ->
                    menuOpen = false
                    scope.launch { repository.setCategory(txn.id, category, rememberForMerchant) }
                },
                onDismiss = { menuOpen = false },
            )
        }
        if (renaming) {
            RenamePayeeDialog(
                current = txn.merchant,
                onDismiss = { renaming = false },
                onSave = { name ->
                    renaming = false
                    scope.launch { repository.renamePayee(txn.merchantKey, name) }
                },
            )
        }
        if (editing) {
            EditPaymentDialog(
                t = txn,
                onDismiss = { editing = false },
                onSave = { amount, millis ->
                    editing = false
                    scope.launch { repository.edit(txn.id, amount, millis) }
                },
            )
        }
        if (splitting) {
            SplitDialog(
                t = txn,
                catalog = catalog,
                onDismiss = { splitting = false },
                onSave = { parts ->
                    splitting = false
                    scope.launch { repository.setSplits(txn.id, parts) }
                },
            )
        }

        if (tagging) {
            val everything by repository.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
            val known = remember(everything) { Tags.inUse(everything) }
            var text by remember { mutableStateOf("") }
            val add = { tag: String ->
                tagging = false
                scope.launch { repository.setTags(txn.id, txn.tags + tag) }
                Unit
            }
            AlertDialog(
                onDismissRequest = { tagging = false },
                title = { Text("Add a tag") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(text, { text = it.replace(",", "").take(30) }, label = { Text("Tag (like office, goa trip)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        val suggestions = known.filter { it !in txn.tags && it.contains(Tags.clean(text)) }.take(6)
                        suggestions.forEach { tag -> TextButton(onClick = { add(tag) }) { Text("# $tag") } }
                    }
                },
                confirmButton = { TextButton(enabled = Tags.clean(text).isNotEmpty(), onClick = { add(Tags.clean(text)) }) { Text("Add") } },
                dismissButton = { TextButton(onClick = { tagging = false }) { Text("Cancel") } },
            )
        }

        if (creatingCategory) {
            CategoryEditorDialog(
                initial = null,
                onDismiss = { creatingCategory = false },
                defaultGroup = catalog.groupOf(txn.category),
                groups = catalog.groups,
                onSave = { label, emoji, parent ->
                    creatingCategory = false
                    scope.launch {
                        val created = repository.saveCategory(Category.custom(label, emoji, parent))
                        repository.setCategory(txn.id, created, rememberForMerchant)
                    }
                },
            )
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), color = Muted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
