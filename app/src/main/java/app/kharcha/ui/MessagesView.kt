package app.kharcha.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kharcha.AppContainer
import app.kharcha.core.CategoryCatalog
import app.kharcha.capture.ScanProgress
import app.kharcha.data.MessageState
import app.kharcha.data.UnreadMessageEntity
import kotlinx.coroutines.launch

private enum class MessageFilter(val label: String, val states: Set<String>?) {
    ALL("All", null),
    PAYMENTS("Payments", setOf(MessageState.PAYMENT, MessageState.ADDED, MessageState.BILL)),
    UNREAD("Couldn’t read", setOf(MessageState.UNREAD)),
    SKIPPED("Other", setOf(MessageState.SKIPPED)),
    HIDDEN("Not money", setOf(MessageState.NOT_MONEY, MessageState.HIDDEN)),
}

@Composable
private fun stateLabel(state: String): Pair<String, Color> = when (state) {
    MessageState.PAYMENT -> "Payment" to Blue
    MessageState.ADDED -> "Added by you" to Blue
    MessageState.BILL -> "Card bill" to Blue
    MessageState.UNREAD -> "Couldn’t read" to Rust
    MessageState.SKIPPED -> "Not read as a payment" to Faint
    MessageState.NOT_MONEY -> "Not money — you said" to Faint
    MessageState.HIDDEN -> "Hidden by a pattern you taught" to Faint
    else -> state to Faint
}

/**
 * Every message the app has seen (one-time codes never kept), with what it did. Marking one "not money" teaches a pattern:
 * similar messages are hidden from then on (and the ones already here, with undo).
 */
@Composable
fun MessagesView(
    container: AppContainer,
    onOpenTransaction: (Long) -> Unit,
    onUndoable: (message: String, undo: suspend () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = container.repository
    val messages by repository.messages.collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by repository.learnedRules.collectAsStateWithLifecycle(initialValue = emptyList())
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(MessageFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf<UnreadMessageEntity?>(null) }
    var confirmPaymentRule by remember { mutableStateOf<Pair<UnreadMessageEntity, Int>?>(null) }
    var showRules by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf<ScanProgress?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var askingDate by remember { mutableStateOf(false) }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    val shown = remember(messages, filter, query) {
        val q = query.trim().lowercase()
        val states = filter.states
        messages.filter { m ->
            (states == null || m.state in states) && (q.isEmpty() || m.body.lowercase().contains(q) || m.sender.lowercase().contains(q))
        }
    }

    fun notMoney(m: UnreadMessageEntity) {
        scope.launch {
            val result = repository.markNotMoney(m.id) ?: return@launch
            val extra = if (result.similarCount > 0) " and ${result.similarCount} similar" else ""
            onUndoable("Hid this message$extra. Messages like it will be hidden from now on.") { repository.undoNotMoney(result) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Every message from your banks, and what the app did with it. Personal messages and OTPs are never kept. " +
                    "Mark anything that isn’t money and the app learns to hide messages like it.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${rules.size} learned pattern${if (rules.size == 1) "" else "s"}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Faint,
                )
                if (rules.isNotEmpty()) TextButton(onClick = { showRules = true }) { Text("Manage") }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search messages") },
                singleLine = true,
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MessageFilter.entries.forEach { option ->
                    val count = messages.count { option.states == null || it.state in option.states }
                    if (filter == option) {
                        Button(onClick = {}) { Text("${option.label} ($count)") }
                    } else {
                        OutlinedButton(onClick = { filter = option }) { Text("${option.label} ($count)") }
                    }
                }
            }
        }
        if (messages.isEmpty() || !container.prefs.allMessagesLoaded) {
            item {
                Panel(color = CoolTint) {
                    Text(if (messages.isEmpty()) "Load your messages" else "Load the rest of your inbox", fontWeight = FontWeight.Bold)
                    Text(
                        "Every message is listed here (never one-time codes), so you can mark what isn't a payment. New ones appear as they arrive; read your inbox once from a date you pick to see the ones already there.",
                        color = Muted,
                    )
                    Button(
                        enabled = !scanning,
                        onClick = {
                            if (!hasSmsPermission(context)) {
                                scanLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                            } else {
                                askingDate = true
                            }
                        },
                    ) { Text(if (scanning) "Reading… ${scan?.checked ?: 0} of ${scan?.total ?: 0}" else "Read my inbox…") }
                    if (askingDate) {
                        ScanDateDialog(onDismiss = { askingDate = false }, onPick = { date ->
                            askingDate = false
                            scanning = true
                            scope.launch {
                                container.scanner.scan(date.atStartOfDay(Zone).toInstant().toEpochMilli()) { scan = it }
                                container.prefs.allMessagesLoaded = true
                                scanning = false
                            }
                        })
                    }
                }
            }
        } else if (shown.isEmpty()) {
            item { EmptyState("Nothing here", "No messages match this filter.") }
        }
        items(shown, key = { it.id }) { m ->
            MessageCard(
                message = m,
                onAddPayment = { adding = m },
                onNotMoney = {
                    if (m.state == MessageState.PAYMENT) {
                        scope.launch { confirmPaymentRule = m to repository.previewNotMoney(m.id) }
                    } else {
                        notMoney(m)
                    }
                },
                onRestore = { scope.launch { repository.rereadMessage(m) } },
                onOpenTransaction = m.transactionId?.let { id -> { onOpenTransaction(id) } },
                onCopy = { copyToClipboard(context, m.body) },
            )
        }
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

    confirmPaymentRule?.let { (message, similar) ->
        AlertDialog(
            onDismissRequest = { confirmPaymentRule = null },
            title = { Text("Not money?") },
            text = {
                Text(
                    "This message reads like a payment. The payment will be ignored, and future messages like it will be hidden too" +
                        if (similar > 0) ", including $similar already here that you haven’t reviewed." else ".",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmPaymentRule = null
                    notMoney(message)
                }) { Text("Yes, not money") }
            },
            dismissButton = { TextButton(onClick = { confirmPaymentRule = null }) { Text("Cancel") } },
        )
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = { showRules = false },
            title = { Text("Learned patterns") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rules.forEach { rule ->
                        Column {
                            Text(
                                (if (rule.senderCode == "*") "Any bank" else rule.senderCode) +
                                    if (rule.coversPayments) " · includes payment-like messages" else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = Faint,
                            )
                            Text("“${rule.sample}”", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { scope.launch { repository.deleteRule(rule.id) } }) {
                                Text("Stop hiding messages like this", color = Rust)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRules = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun MessageCard(
    message: UnreadMessageEntity,
    onAddPayment: () -> Unit,
    onNotMoney: () -> Unit,
    onRestore: () -> Unit,
    onOpenTransaction: (() -> Unit)?,
    onCopy: () -> Unit,
) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    val (label, color) = stateLabel(message.state)
    Panel(Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${message.sender} · ${millisText(message.receivedAt)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Faint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
        }
        Text(message.body, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 3)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            when (message.state) {
                MessageState.PAYMENT, MessageState.ADDED, MessageState.BILL -> {
                    if (onOpenTransaction != null) TextButton(onClick = onOpenTransaction) { Text("Open payment") }
                    if (message.state == MessageState.PAYMENT) TextButton(onClick = onNotMoney) { Text("Not money", color = Faint) }
                }
                MessageState.UNREAD, MessageState.SKIPPED -> {
                    TextButton(onClick = onAddPayment) { Text("It’s a payment") }
                    TextButton(onClick = onNotMoney) { Text("Not money", color = Faint) }
                }
                MessageState.NOT_MONEY, MessageState.HIDDEN -> {
                    TextButton(onClick = onRestore) { Text("Show again") }
                    TextButton(onClick = onAddPayment) { Text("It’s a payment") }
                }
            }
            if (expanded) TextButton(onClick = onCopy) { Text("Copy") }
        }
    }
}
