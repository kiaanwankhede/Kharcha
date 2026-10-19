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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import app.kharcha.core.CategoryMatch
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kharcha.AppContainer
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Kind
import kotlinx.coroutines.launch

/** Your tags: made as you review, renamed, merged or deleted here. */
@Composable
fun CategoriesScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.repository
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    val all by repository.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val counts = remember(all) { all.flatMap { it.tags }.groupingBy { it }.eachCount() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("‹ Back") } }
        item { Text("Your tags", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Text(
                "Give each payment one or more tags when you review it. Type a tag: if you have it, it's used; if it's close to one you have, that's suggested; otherwise you're asked before it's created. The first tag on a payment counts in your totals; every tag also gets its own total.",
                color = Muted,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(30) },
                    placeholder = { Text("New tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(enabled = newName.isNotBlank(), onClick = { creating = newName.trim() }) { Text("Add") }
            }
        }
        if (catalog.tags.isEmpty()) {
            item { EmptyState("No tags yet", "Make them as you go: review a payment and type what it was.") }
        }
        items(catalog.tags, key = { it.id }) { category ->
            CategoryRow(
                category,
                subtitle = "${counts[category.label] ?: 0} payments · tap to rename, merge or delete",
                onClick = { editing = category },
            )
        }
        item {
            Text(
                "Not spending (built in, so transfers, ATM cash and wallet top-ups are never counted twice): " +
                    Category.SYSTEM.joinToString(", ") { it.label.lowercase() } + ".",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Faint,
            )
        }
    }

    creating?.let { name ->
        CreateCategoryDialog(
            name = name,
            similar = CategoryMatch.find(name, catalog.tags).close + listOfNotNull(CategoryMatch.find(name, catalog.tags).exact),
            onUseExisting = {
                creating = null
                newName = ""
            },
            onDismiss = { creating = null },
            onCreate = { label, emoji ->
                creating = null
                newName = ""
                scope.launch { repository.saveCategory(Category.forTag(label, emoji)) }
            },
        )
    }

    editing?.let { category ->
        EditCategoryDialog(
            category = category,
            others = catalog.tags.filter { it.id != category.id },
            payments = counts[category.label] ?: 0,
            onDismiss = { editing = null },
            onSave = { label, emoji ->
                editing = null
                scope.launch { repository.renameTag(category, label, emoji) }
            },
            onMerge = { into ->
                editing = null
                scope.launch { repository.mergeCategory(category, into) }
            },
            onDelete = {
                editing = null
                scope.launch { repository.deleteCategory(category) }
            },
        )
    }
}

@Composable
private fun EditCategoryDialog(
    category: Category,
    others: List<Category>,
    payments: Int,
    onDismiss: () -> Unit,
    onSave: (label: String, emoji: String) -> Unit,
    onMerge: (Category) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember { mutableStateOf(category.label) }
    var emoji by remember { mutableStateOf(category.emoji) }
    var mergeMenu by remember { mutableStateOf(false) }
    var mergeInto by remember { mutableStateOf<Category?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(label, { label = it.take(30) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (others.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { mergeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Merge into another tag ▾") }
                        DropdownMenu(expanded = mergeMenu, onDismissRequest = { mergeMenu = false }) {
                            others.forEach { other ->
                                DropdownMenuItem(text = { Text("${other.emoji}  ${other.label}".trim()) }, onClick = {
                                    mergeMenu = false
                                    mergeInto = other
                                })
                            }
                        }
                    }
                }
                mergeInto?.let { into ->
                    Text("Move all $payments payments from “${category.label}” to “${into.label}” and delete “${category.label}”?", color = Rust)
                    Row {
                        TextButton(onClick = { onMerge(into) }) { Text("Yes, merge") }
                        TextButton(onClick = { mergeInto = null }) { Text("Cancel") }
                    }
                }
                if (confirmDelete) {
                    Text("Delete “${category.label}”? It comes off its $payments payments.", color = Rust)
                    Row {
                        TextButton(onClick = onDelete) { Text("Yes, delete", color = Rust) }
                        TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete tag", color = Rust) }
                }
            }
        },
        confirmButton = { TextButton(enabled = label.isNotBlank(), onClick = { onSave(label.trim(), emoji) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CategoryMenuItems(catalog: CategoryCatalog, filter: (Category) -> Boolean = { true }, onPick: (Category) -> Unit) {
    (catalog.tags.filter(filter) + Category.OTHER).forEach { option ->
        DropdownMenuItem(text = { Text("${option.emoji}  ${option.label}".trim()) }, onClick = { onPick(option) })
    }
}

@Composable
private fun CategoryRow(category: Category, subtitle: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmojiBadge(category.emoji)
        Column(Modifier.weight(1f)) {
            Text(category.label, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Faint) }
        }
    }
}

@Composable
fun EmojiBadge(emoji: String, size: Int = 40) {
    if (emoji.isBlank()) return
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(Sand),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size * 0.5f).sp) }
}
