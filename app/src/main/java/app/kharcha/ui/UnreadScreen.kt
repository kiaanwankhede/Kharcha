package app.kharcha.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kharcha.AppContainer
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Direction
import app.kharcha.core.Money
import app.kharcha.data.UnreadMessageEntity
import kotlinx.coroutines.launch

/** Bank messages that mention money in a format the parser does not know yet. */
@Composable
fun UnreadScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.repository
    val messages by repository.unread.collectAsStateWithLifecycle(initialValue = emptyList())
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var adding by remember { mutableStateOf<UnreadMessageEntity?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("‹ Back") } }
        item { Text("Messages we couldn’t read", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Text(
                "These came from a bank and mention money, but in a format the app doesn’t understand yet. Add the payment yourself, " +
                    "or mark it as not a payment. Copying the text and sending it to the developer helps the app learn the format.",
                color = Muted,
            )
        }
        if (messages.isEmpty()) {
            item { EmptyState("Nothing here", "Every bank message so far was understood.") }
        }
        items(messages, key = { it.id }) { message ->
            Panel {
                Text("${message.sender} · ${millisText(message.receivedAt)}", style = MaterialTheme.typography.bodySmall, color = Faint)
                Text(message.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { adding = message }) { Text("Add payment") }
                    OutlinedButton(onClick = { scope.launch { repository.dismissUnread(message.id) } }) { Text("Not a payment") }
                }
                TextButton(onClick = { copyToClipboard(context, message.body) }) { Text("Copy text") }
            }
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
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Bank message", text))
}

@Composable
private fun AddFromMessageDialog(
    message: UnreadMessageEntity,
    catalog: CategoryCatalog,
    onDismiss: () -> Unit,
    onSave: (amountPaise: Long, direction: Direction, payee: String, category: Category) -> Unit,
) {
    var amount by remember {
        mutableStateOf(message.amountPaise?.let { Money.format(it, showPaise = it % 100 != 0L).removePrefix("₹").replace(",", "") }.orEmpty())
    }
    var direction by remember { mutableStateOf(Direction.DEBIT) }
    var payee by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.OTHER) }
    var menuOpen by remember { mutableStateOf(false) }
    val paise = Money.parsePaise(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add this payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' }.take(10) },
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Direction.DEBIT to "Money out", Direction.CREDIT to "Money in").forEach { (value, label) ->
                        if (direction == value) Button(onClick = {}) { Text(label) } else OutlinedButton(onClick = { direction = value }) { Text(label) }
                    }
                }
                OutlinedTextField(
                    value = payee,
                    onValueChange = { payee = it.take(40) },
                    label = { Text("Paid to / received from") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${category.emoji}  ${category.label}  ▾")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        CategoryMenuItems(catalog) { option ->
                            category = option
                            menuOpen = false
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = paise != null, onClick = { onSave(paise!!, direction, payee, category) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
