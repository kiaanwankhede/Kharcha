package app.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import app.kharcha.core.Money
import app.kharcha.core.Source
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(container: AppContainer, id: Long, onBack: () -> Unit) {
    val repository = container.repository
    val flow = remember(id) { repository.transaction(id) }
    val t by flow.collectAsStateWithLifecycle(initialValue = null)
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    var creatingCategory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var rememberForMerchant by rememberSaveable { mutableStateOf(true) }
    var note by rememberSaveable(t?.id) { mutableStateOf(t?.note.orEmpty()) }

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

        Panel(color = Sand) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category", Modifier.weight(1f), color = Muted)
                Box {
                    TextButton(onClick = { menuOpen = true }) {
                        Text("${txn.category.emoji} ${txn.category.label}  ▾", fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("＋ New category…", fontWeight = FontWeight.Bold) },
                            onClick = {
                                menuOpen = false
                                creatingCategory = true
                            },
                        )
                        catalog.all.forEach { category ->
                            DropdownMenuItem(
                                text = { Text("${category.emoji}  ${category.label}") },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { repository.setCategory(txn.id, category, rememberForMerchant) }
                                },
                            )
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
            label = { Text("Note or tag") },
        )
        OutlinedButton(onClick = { scope.launch { repository.setNote(txn.id, note) } }) { Text("Save note") }

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

        if (creatingCategory) {
            CategoryEditorDialog(
                initial = null,
                onDismiss = { creatingCategory = false },
                onSave = { label, emoji, countsAsSpend ->
                    creatingCategory = false
                    scope.launch {
                        val created = repository.saveCategory(Category.custom(label, emoji, countsAsSpend))
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
