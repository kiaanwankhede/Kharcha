@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.kharcha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog

/**
 * Pick a subcategory from a bottom sheet: search box, suggestions first, then every group as a block
 * of chips. Faster than scrolling a 90-item menu.
 */
@Composable
fun CategoryPickerSheet(
    catalog: CategoryCatalog,
    selected: Category?,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
    suggestions: List<Category> = emptyList(),
    filter: (Category) -> Boolean = { true },
    onCreateNew: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val matches = remember(q, catalog) {
        if (q.isEmpty()) {
            emptyList()
        } else {
            catalog.all.filter(filter).filter {
                it.label.lowercase().contains(q) || catalog.groupOf(it).label.lowercase().contains(q)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Paper) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search categories (e.g. milk, cab, rent)") },
                    singleLine = true,
                )
            }
            if (q.isNotEmpty()) {
                item {
                    if (matches.isEmpty()) {
                        Text("No category matches “$query”.", color = Muted)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            matches.forEach { PickChip(it, it.id == selected?.id) { onPick(it) } }
                        }
                    }
                }
            } else {
                val suggested = suggestions.filter(filter).distinctBy { it.id }
                if (suggested.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel("Suggested")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggested.forEach { PickChip(it, it.id == selected?.id) { onPick(it) } }
                            }
                        }
                    }
                }
                catalog.groups.forEach { group ->
                    val children = catalog.childrenOf(group).filter(filter)
                    if (children.isNotEmpty()) {
                        item(key = group.id) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${group.emoji}  ${group.label}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    children.forEach { PickChip(it, it.id == selected?.id) { onPick(it) } }
                                }
                            }
                        }
                    }
                }
            }
            if (onCreateNew != null) {
                item { TextButton(onClick = onCreateNew) { Text("＋ New category…") } }
            }
        }
    }
}

@Composable
private fun PickChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    Text(
        "${category.emoji} ${category.label}",
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Navy else CardColor)
            .then(if (selected) Modifier else Modifier.border(1.dp, Line, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) Paper else Navy,
        style = MaterialTheme.typography.bodyMedium,
    )
}
