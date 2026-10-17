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
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(container: AppContainer, onBack: () -> Unit) {
    val repository = container.repository
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    val custom = catalog.all.filterNot { it.builtIn }
    val builtIn = catalog.all.filter { it.builtIn }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("‹ Back") } }
        item { Text("Categories", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ New category") } }
        item { SectionLabel("Yours", Modifier.padding(top = 12.dp)) }
        if (custom.isEmpty()) {
            item { Text("None yet. Add one for anything that matters to you — Pets, Gym, Kids, Office lunch…", color = Muted) }
        }
        items(custom, key = { it.id }) { category ->
            CategoryRow(category, subtitle = "Tap to edit") { editing = category }
        }
        item { SectionLabel("Built in", Modifier.padding(top = 12.dp)) }
        items(builtIn, key = { it.id }) { category ->
            CategoryRow(category, subtitle = if (category.countsAsSpend) null else "Not counted as spending", onClick = null)
        }
    }

    if (creating) {
        CategoryEditorDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { label, emoji, countsAsSpend ->
                creating = false
                scope.launch { repository.saveCategory(Category.custom(label, emoji, countsAsSpend)) }
            },
        )
    }
    editing?.let { category ->
        CategoryEditorDialog(
            initial = category,
            onDismiss = { editing = null },
            onSave = { label, emoji, countsAsSpend ->
                editing = null
                scope.launch {
                    repository.saveCategory(category.copy(label = label.trim(), emoji = emoji.trim().ifEmpty { "🏷️" }, countsAsSpend = countsAsSpend))
                }
            },
            onDelete = {
                editing = null
                scope.launch { repository.deleteCategory(category) }
            },
        )
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
    onSave: (label: String, emoji: String, countsAsSpend: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🏷️") }
    var countsAsSpend by remember { mutableStateOf(initial?.countsAsSpend ?: true) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Count as spending", Modifier.weight(1f))
                    Switch(checked = countsAsSpend, onCheckedChange = { countsAsSpend = it })
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
            TextButton(onClick = { onSave(label, emoji, countsAsSpend) }, enabled = label.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
