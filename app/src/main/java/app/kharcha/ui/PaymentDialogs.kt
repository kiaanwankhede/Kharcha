package app.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Money
import app.kharcha.core.SplitPart
import app.kharcha.core.Transaction
import java.time.Instant

private fun amountText(paise: Long): String =
    Money.format(paise, showPaise = paise % 100 != 0L).removePrefix("₹").replace(",", "")

/** Rename a payee everywhere; typing an existing payee's name merges the two. */
@Composable
fun RenamePayeeDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename payee") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("Name") })
                Text(
                    "Applies to all past and future payments to $current. Use the exact name of another payee to merge them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Fix the amount or the day of a payment. */
@Composable
fun EditPaymentDialog(t: Transaction, onDismiss: () -> Unit, onSave: (amountPaise: Long, timestampMillis: Long) -> Unit) {
    var amount by remember { mutableStateOf(amountText(t.amountPaise)) }
    var dayShift by remember { mutableIntStateOf(0) }
    val paise = Money.parsePaise(amount)
    val original = Instant.ofEpochMilli(t.timestampMillis).atZone(Zone)
    val moved = original.plusDays(dayShift.toLong())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit payment") },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { dayShift-- }) { Text("‹") }
                    Text(moved.toLocalDate().dayHeader(), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { dayShift++ }, enabled = moved.toLocalDate() < java.time.LocalDate.now(Zone)) { Text("›") }
                }
                if (t.isSplit && paise != t.amountPaise) {
                    Text("Changing the amount removes the split.", style = MaterialTheme.typography.bodySmall, color = Rust)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = paise != null, onClick = { onSave(paise!!, moved.toInstant().toEpochMilli()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Split one payment across categories; the parts must add up to the payment. */
@Composable
fun SplitDialog(t: Transaction, catalog: CategoryCatalog, onDismiss: () -> Unit, onSave: (List<SplitPart>) -> Unit) {
    val amounts = remember {
        mutableStateListOf<String>().apply {
            if (t.isSplit) addAll(t.splits.map { amountText(it.amountPaise) }) else addAll(listOf(amountText(t.amountPaise), ""))
        }
    }
    val categories = remember {
        mutableStateListOf<Category>().apply {
            if (t.isSplit) addAll(t.splits.map { it.category }) else addAll(listOf(t.category, Category.OTHER))
        }
    }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val parsed = amounts.map { Money.parsePaise(it) }
    val total = parsed.sumOf { it ?: 0L }
    val remaining = t.amountPaise - total
    val valid = amounts.size >= 2 && parsed.all { it != null } && remaining == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split ${Money.format(t.amountPaise, showPaise = t.amountPaise % 100 != 0L)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                amounts.indices.forEach { index ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = amounts[index],
                            onValueChange = { input -> amounts[index] = input.filter { it.isDigit() || it == '.' }.take(10) },
                            prefix = { Text("₹") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp),
                        )
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { menuFor = index }, modifier = Modifier.fillMaxWidth()) {
                                Text("${categories[index].emoji} ${categories[index].label}".trim(), maxLines = 1)
                            }
                            DropdownMenu(expanded = menuFor == index, onDismissRequest = { menuFor = null }) {
                                CategoryMenuItems(catalog, filter = { it.countsAsSpend }) { option ->
                                    categories[index] = option
                                    menuFor = null
                                }
                            }
                        }
                        if (amounts.size > 2) {
                            TextButton(onClick = {
                                amounts.removeAt(index)
                                categories.removeAt(index)
                            }) { Text("✕") }
                        }
                    }
                }
                TextButton(onClick = {
                    amounts.add(if (remaining > 0) amountText(remaining) else "")
                    categories.add(Category.OTHER)
                }) { Text("＋ Add part") }
                Text(
                    when {
                        remaining == 0L -> "Adds up ✓"
                        remaining > 0 -> "${Money.format(remaining, showPaise = remaining % 100 != 0L)} left to assign"
                        else -> "${Money.format(-remaining, showPaise = -remaining % 100 != 0L)} too much"
                    },
                    color = if (remaining == 0L) Blue else Rust,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(amounts.indices.map { SplitPart(parsed[it]!!, categories[it]) })
            }) { Text("Save split") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
