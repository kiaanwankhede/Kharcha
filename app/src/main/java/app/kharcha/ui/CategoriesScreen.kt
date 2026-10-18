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

/** Your own categories: a flat list you add to while reviewing, and can rename, merge or delete here. */
@Composable
fun CategoriesScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.repository
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    val all by repository.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val counts = remember(all) { all.groupingBy { it.category.id }.eachCount() }
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
        item { Text("Your categories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Text(
                "Type a category when you review a payment: if you have it, it's used; if it's close to one you have, that's suggested; otherwise you're asked whether to create it.",
                color = Muted,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(30) },
                    placeholder = { Text("New category") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(enabled = newName.isNotBlank(), onClick = { creating = newName.trim() }) { Text("Add") }
            }
        }
        if (catalog.mine.isEmpty()) {
            item { EmptyState("No categories yet", "Make them as you go: review a payment and type what it was.") }
        }
        items(catalog.mine, key = { it.id }) { category ->
            CategoryRow(
                category,
                subtitle = "${counts[category.id] ?: 0} payments · tap to rename, merge or delete",
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
            similar = CategoryMatch.find(name, catalog.mine).close + listOfNotNull(CategoryMatch.find(name, catalog.mine).exact),
            onUseExisting = { creating = null },
            onDismiss = { creating = null },
            onCreate = { label, emoji ->
                creating = null
                newName = ""
                scope.launch { repository.saveCategory(Category.mine(label, emoji)) }
            },
        )
    }

    editing?.let { category ->
        EditCategoryDialog(
            category = category,
            others = catalog.mine.filter { it.id != category.id },
            payments = counts[category.id] ?: 0,
            onDismiss = { editing = null },
            onSave = { label, emoji ->
                editing = null
                scope.launch { repository.saveCategory(category.copy(label = label, emoji = emoji)) }
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
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Emoji") }, singleLine = true, modifier = Modifier.width(84.dp))
                    OutlinedTextField(label, { label = it.take(30) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (others.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { mergeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Merge into another category ▾") }
                        DropdownMenu(expanded = mergeMenu, onDismissRequest = { mergeMenu = false }) {
                            others.forEach { other ->
                                DropdownMenuItem(text = { Text("${other.emoji}  ${other.label}") }, onClick = {
                                    mergeMenu = false
                                    onMerge(other)
                                })
                            }
                        }
                    }
                }
                if (confirmDelete) {
                    Text("Delete ${category.label}? Its $payments payments go back to Uncategorised.", color = Rust)
                    Row {
                        TextButton(onClick = onDelete) { Text("Yes, delete", color = Rust) }
                        TextButton(onClick = { confirmDelete = false }) { Text("Keep it") }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete category", color = Rust) }
                }
            }
        },
        confirmButton = { TextButton(enabled = label.isNotBlank(), onClick = { onSave(label.trim(), emoji.trim().ifEmpty { "🏷️" }) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CategoryMenuItems(catalog: CategoryCatalog, filter: (Category) -> Boolean = { true }, onPick: (Category) -> Unit) {
    (catalog.mine.filter(filter) + Category.OTHER).forEach { option ->
        DropdownMenuItem(text = { Text("${option.emoji}  ${option.label}") }, onClick = { onPick(option) })
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
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(Sand),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size * 0.5f).sp) }
}

private val EMOJI_CHOICES = listOf(
    "🍔", "☕", "🍺", "🛒", "🛍️", "👕", "🚕", "✈️", "⛽", "🏠", "💡", "📱", "🎬", "🎮", "🎁", "💊",
    "🏋️", "🐶", "👶", "🎓", "💼", "🧾", "💇", "🔧", "🙏", "❤️", "🏷️",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (label: String, emoji: String, parent: Category) -> Unit,
    defaultGroup: Category = Category.G_OTHER,
    groups: List<Category> = Category.GROUPS,
    onDelete: (() -> Unit)? = null,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🏷️") }
    var parent by remember { mutableStateOf(if (defaultGroup.isGroup) defaultGroup else Category.groupOf(defaultGroup)) }
    var groupMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmojiBadge(emoji, size = 48)
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(30) },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EMOJI_CHOICES.forEach { choice ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(if (choice == emoji) Modifier.border(2.dp, Navy, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable { emoji = choice },
                            contentAlignment = Alignment.Center,
                        ) { Text(choice, fontSize = 20.sp) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { groupMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("In group: ${parent.emoji} ${parent.label}  ▾")
                    }
                    DropdownMenu(expanded = groupMenuOpen, onDismissRequest = { groupMenuOpen = false }) {
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text("${group.emoji}  ${group.label}") },
                                onClick = {
                                    parent = group
                                    groupMenuOpen = false
                                },
                            )
                        }
                    }
                }
                if (onDelete != null) {
                    if (confirmDelete) {
                        Text("Its transactions will move to Other.", color = Rust)
                        TextButton(onClick = onDelete) { Text("Yes, delete", color = Rust) }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete category", color = Rust) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label, emoji, parent) }, enabled = label.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Create or edit one of the user's own groups. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupEditorDialog(
    initial: Category?,
    onDismiss: () -> Unit,
    onSave: (label: String, emoji: String, kind: Kind) -> Unit,
    subcategoryCount: Int = 0,
    onDelete: (() -> Unit)? = null,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "📁") }
    var kind by remember { mutableStateOf(initial?.kind ?: Kind.SPENDING) }
    var confirmDelete by remember { mutableStateOf(false) }
    val kinds = listOf(
        Kind.SPENDING to "Spending",
        Kind.SAVING to "Saving & investing",
        Kind.MOVEMENT to "Not counted",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New group" else "Edit group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmojiBadge(emoji, size = 48)
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(30) },
                        label = { Text("Group name") },
                        singleLine = true,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (listOf("📁", "🧒", "💼", "🏡", "🚗", "🐾", "🎉", "💍", "🧳", "🛠️", "🎸", "🌱") + EMOJI_CHOICES).distinct().forEach { choice ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .then(if (choice == emoji) Modifier.border(2.dp, Navy, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable { emoji = choice },
                            contentAlignment = Alignment.Center,
                        ) { Text(choice, fontSize = 20.sp) }
                    }
                }
                Text("Type", style = MaterialTheme.typography.labelLarge, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEach { (value, name) ->
                        if (kind == value) {
                            Button(onClick = {}) { Text(name) }
                        } else {
                            OutlinedButton(onClick = { kind = value }) { Text(name) }
                        }
                    }
                }
                Text(
                    when (kind) {
                        Kind.SPENDING -> "Counts in your spending totals."
                        Kind.SAVING -> "Shown as saved, not spent."
                        else -> "Never counted as spending (money just moving around)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                if (onDelete != null) {
                    if (confirmDelete) {
                        Text(
                            if (subcategoryCount > 0) "Its $subcategoryCount subcategories will move to Other." else "This group is empty.",
                            color = Rust,
                        )
                        TextButton(onClick = onDelete) { Text("Yes, delete group", color = Rust) }
                    } else {
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete group", color = Rust) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label, emoji, kind) }, enabled = label.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
