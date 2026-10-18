package app.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.time.LocalDate
import java.time.LocalTime

/** Quick entry for a cash payment: amount, what for, category, which day. */
@Composable
fun AddCashDialog(
    catalog: CategoryCatalog,
    onDismiss: () -> Unit,
    onSave: (amountPaise: Long, whatFor: String, category: Category, timestampMillis: Long) -> Unit,
    onCreateCategory: (suspend (label: String, emoji: String) -> Category)? = null,
) {
    var amount by remember { mutableStateOf("") }
    var whatFor by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.OTHER) }
    var daysAgo by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val paise = Money.parsePaise(amount)
    val today = LocalDate.now(Zone)
    val day = today.minusDays(daysAgo.toLong())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add cash payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { input -> amount = input.filter { it.isDigit() || it == '.' }.take(10) },
                    label = { Text("Amount") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = whatFor,
                    onValueChange = { whatFor = it.take(40) },
                    label = { Text("What for (e.g. Chai, Auto, Sabzi)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${category.emoji}  ${category.label}  ▾")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { daysAgo++ }, enabled = daysAgo < 60) { Text("‹") }
                    Text(day.dayHeader(today), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { daysAgo-- }, enabled = daysAgo > 0) { Text("›") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = paise != null,
                onClick = {
                    val time = if (daysAgo == 0) LocalTime.now(Zone) else LocalTime.NOON
                    onSave(paise!!, whatFor, category, day.atTime(time).atZone(Zone).toInstant().toEpochMilli())
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (menuOpen) {
        CategoryPickerSheet(
            catalog = catalog,
            selected = category,
            filter = { it.countsAsSpend },
            hints = listOf(whatFor).filter { it.isNotBlank() },
            onCreate = onCreateCategory,
            onPick = {
                category = it
                menuOpen = false
            },
            onDismiss = { menuOpen = false },
        )
    }
}
