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

@Composable
fun CategoriesScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.repository
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creatingIn by remember { mutableStateOf<Category?>(null) }
    var editingGroup by remember { mutableStateOf<Category?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("‹ Back") } }
        item { Text("Categories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Text(
                "Pick a subcategory when you review; totals roll up into its group. Add your own subcategories under any group, or make your own groups.",
                color = Muted,
            )
        }
        item { Button(onClick = { creatingGroup = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ New group") } }
        catalog.groups.forEach { group ->
            item(key = "g-${group.id}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${group.emoji}  ${group.label}" + if (group.builtIn) "" else "  ✎",
                        Modifier
                            .weight(1f)
                            .then(if (group.builtIn) Modifier else Modifier.clickable { editingGroup = group }),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { creatingIn = group }) { Text("＋ Add") }
                }
                val note = when (group.kind) {
                    Kind.SAVING -> "Shown as saved, not spent"
                    Kind.MOVEMENT -> "Never counted as spending"
                    Kind.INCOME -> "Money coming in"
                    Kind.SPENDING -> null
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Faint) }
            }
            items(catalog.childrenOf(group), key = { it.id }) { category ->
                CategoryRow(
                    category,
                    subtitle = when {
                        !category.builtIn -> "Yours · tap to edit"
                        category.kind != group.kind -> "Not counted as spending"
                        else -> null
                    },
                    onClick = if (category.builtIn) null else ({ editing = category }),
                )
            }
        }
    }

    if (creatingGroup) {
        GroupEditorDialog(
            initial = null,
            onDismiss = { creatingGroup = false },
            onSave = { label, emoji, kind ->
                creatingGroup = false
                scope.launch { repository.saveCategory(Category.customGroup(label, emoji, kind)) }
            },
        )
    }
    editingGroup?.let { group ->
        GroupEditorDialog(
            initial = group,
            subcategoryCount = catalog.childrenOf(group).size,
            onDismiss = { editingGroup = null },
            onSave = { label, emoji, kind ->
                editingGroup = null
                scope.launch {
                    repository.saveCategory(
                        group.copy(label = label.trim(), emoji = emoji.trim().ifEmpty { "📁" }, kind = kind, countsAsSpend = kind == Kind.SPENDING),
                    )
                }
            },
            onDelete = {
                editingGroup = null
                scope.launch { repository.deleteCategory(group) }
            },
        )
    }
    creatingIn?.let { group ->
        CategoryEditorDialog(
            initial = null,
            defaultGroup = group,
            groups = catalog.groups,
            onDismiss = { creatingIn = null },
            onSave = { label, emoji, parent ->
                creatingIn = null
                scope.launch { repository.saveCategory(Category.custom(label, emoji, parent)) }
            },
        )
    }
    editing?.let { category ->
        CategoryEditorDialog(
            initial = category,
            defaultGroup = catalog.groupOf(category),
            groups = catalog.groups,
            onDismiss = { editing = null },
            onSave = { label, emoji, parent ->
                editing = null
                scope.launch {
                    repository.saveCategory(
                        category.copy(
                            label = label.trim(),
                            emoji = emoji.trim().ifEmpty { "🏷️" },
                            parentId = parent.id,
                            kind = parent.kind,
                            countsAsSpend = parent.kind == Kind.SPENDING,
                        ),
                    )
                }
            },
            onDelete = {
                editing = null
                scope.launch { repository.deleteCategory(category) }
            },
        )
    }
}

/** A category menu grouped under group headings; [onPick] gets the chosen subcategory. */
@Composable
fun CategoryMenuItems(catalog: CategoryCatalog, filter: (Category) -> Boolean = { true }, onPick: (Category) -> Unit) {
    catalog.groups.forEach { group ->
        val children = catalog.childrenOf(group).filter(filter)
        if (children.isEmpty()) return@forEach
        DropdownMenuItem(
            text = { Text("${group.emoji}  ${group.label}", fontWeight = FontWeight.Bold, color = Faint) },
            onClick = {},
            enabled = false,
        )
        children.forEach { option ->
            DropdownMenuItem(
                text = { Text("${option.emoji}  ${option.label}", Modifier.padding(start = 20.dp)) },
                onClick = { onPick(option) },
            )
        }
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
