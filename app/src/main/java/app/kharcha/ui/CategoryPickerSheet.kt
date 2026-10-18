@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.kharcha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.CategoryMatch
import app.kharcha.core.Tags
import kotlinx.coroutines.launch

/**
 * Pick a category by typing it. An existing name is used as it is; close names are suggested
 * ("Groceries" for "grocery", "Food" for "khana"); anything new can be created on the spot, after
 * asking. [hints] are what the app thinks the payment is ("Food delivery"): matched to your
 * categories when possible, offered as new ones otherwise.
 */
@Composable
fun CategoryPickerSheet(
    catalog: CategoryCatalog,
    selected: Category?,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (suspend (label: String, emoji: String) -> Category)? = null,
    hints: List<String> = emptyList(),
    suggestions: List<Category> = emptyList(),
    filter: (Category) -> Boolean = { true },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf<String?>(null) }
    var showOthers by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val mine = remember(catalog) { catalog.mine.filter(filter) }
    val others = remember(catalog) { Category.SYSTEM.filter(filter) }
    val matches = remember(query, mine) { CategoryMatch.find(query, mine) }
    val otherMatches = remember(query, others) { CategoryMatch.find(query, others, limit = 3) }
    val fromHints = remember(hints, mine) { hints.mapNotNull { CategoryMatch.best(it, mine) } }
    val ideas = remember(hints, mine) { hints.filter { CategoryMatch.best(it, mine) == null }.distinct() }
    val suggested = (suggestions.filter(filter) + fromHints).filter { it.id != Category.OTHER.id }.distinctBy { it.id }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Paper) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(30) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    placeholder = { Text("Type a tag, like petrol or kids") },
                    singleLine = true,
                )
            }
            if (query.isNotBlank()) {
                matches.exact?.let { exact ->
                    item { PickChip(exact, selected = true) { onPick(exact) } }
                }
                if (matches.close.isNotEmpty() || otherMatches.close.isNotEmpty() || otherMatches.exact != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel(if (matches.exact == null) "Did you mean" else "Also close")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                (matches.close + listOfNotNull(otherMatches.exact) + otherMatches.close).distinctBy { it.id }.forEach {
                                    PickChip(it, it.id == selected?.id) { onPick(it) }
                                }
                            }
                        }
                    }
                }
                if (matches.exact == null && onCreate != null) {
                    item {
                        Button(onClick = { confirming = query.trim() }, modifier = Modifier.fillMaxWidth()) {
                            Text("＋ Create “${query.trim()}”")
                        }
                    }
                }
            } else {
                if (suggested.isNotEmpty() || (ideas.isNotEmpty() && onCreate != null)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel("Suggested")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggested.forEach { PickChip(it, it.id == selected?.id) { onPick(it) } }
                                if (onCreate != null) ideas.forEach { idea -> IdeaChip(idea) { confirming = idea } }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("Your tags")
                        if (mine.isEmpty()) {
                            Text("None yet. Type a name above to make your first tag.", color = Muted)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                mine.forEach { PickChip(it, it.id == selected?.id) { onPick(it) } }
                            }
                        }
                    }
                }
                if (others.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${if (showOthers) "▾" else "▸"}  Not spending: transfers, ATM cash, salary, lent…",
                                Modifier.clickable { showOthers = !showOthers },
                                color = Blue,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (showOthers) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    others.forEach { PickChip(it, it.id == selected?.id) { onPick(it) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirming?.let { name ->
        CreateCategoryDialog(
            name = name,
            similar = CategoryMatch.find(name, mine).close,
            onUseExisting = {
                confirming = null
                onPick(it)
            },
            onDismiss = { confirming = null },
            onCreate = { label, emoji ->
                confirming = null
                val create = onCreate ?: return@CreateCategoryDialog
                scope.launch { onPick(create(label, emoji)) }
            },
        )
    }
}

/** Asks before making a new category, with a fitting emoji and any close existing ones to use instead. */
@Composable
fun CreateCategoryDialog(
    name: String,
    onDismiss: () -> Unit,
    onCreate: (label: String, emoji: String) -> Unit,
    similar: List<Category> = emptyList(),
    onUseExisting: (Category) -> Unit = {},
) {
    var label by remember { mutableStateOf(Tags.clean(name)) }
    val guess = remember(label) { CategoryMatch.emojiFor(label) }
    var picked by remember { mutableStateOf<String?>(null) }
    val emoji = picked ?: guess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a new tag?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(label, { label = it.take(30) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf(guess) + CREATE_EMOJI).distinct().forEach { e ->
                        Text(
                            e,
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (e == emoji) Navy else CardColor)
                                .clickable { picked = e }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                if (similar.isNotEmpty()) {
                    Text("You already have something close:", color = Muted, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        similar.forEach { PickChip(it, selected = false) { onUseExisting(it) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = label.isNotBlank(), onClick = { onCreate(label.trim(), emoji) }) { Text("Create $emoji ${label.trim()}") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val CREATE_EMOJI = listOf(
    "🍔", "🛒", "🏠", "⛽", "🚕", "💊", "🧸", "🎓", "👪", "🛍️", "💡", "📱", "🧹", "🎬", "🎁", "🪔", "📈", "🏦", "💼", "☕", "✈️", "🏷️",
)

@Composable
fun PickChip(category: Category, selected: Boolean, onClick: () -> Unit) {
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

/** A category that doesn't exist yet; tapping it asks whether to create it. */
@Composable
fun IdeaChip(name: String, onClick: () -> Unit) {
    Text(
        "＋ $name",
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Blue, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = Blue,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Pick any number of tags for a payment, in order (the first one counts in totals). Typing works like
 * everywhere else: an existing tag is added, close ones are suggested, a new one asks first. Money
 * that isn't spending (a transfer, ATM cash, salary...) is picked under "Not spending" instead.
 */
@Composable
fun TagPickerSheet(
    catalog: CategoryCatalog,
    selected: List<String>,
    type: Category?,
    onDone: (type: Category?, tags: List<String>) -> Unit,
    onDismiss: () -> Unit,
    onCreate: suspend (label: String, emoji: String) -> Category,
    hints: List<String> = emptyList(),
    allowTypes: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val chosen = remember { androidx.compose.runtime.mutableStateListOf<String>().apply { addAll(selected) } }
    var chosenType by remember { mutableStateOf(type?.takeIf { !it.countsAsSpend }) }
    var query by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf<String?>(null) }
    var showOthers by remember { mutableStateOf(chosenType != null) }
    val focus = remember { FocusRequester() }
    val tags = catalog.tags
    val matches = remember(query, tags) { CategoryMatch.find(query, tags) }
    val fromHints = remember(hints, tags) { hints.mapNotNull { CategoryMatch.best(it, tags) } }
    val ideas = remember(hints, tags) { hints.filter { CategoryMatch.best(it, tags) == null }.map { Tags.clean(it) }.distinct() }
    fun add(name: String) {
        val clean = Tags.clean(name)
        if (clean.isNotEmpty() && clean !in chosen) chosen.add(clean)
        query = ""
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Paper) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Tags", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Button(onClick = { onDone(chosenType, chosen.toList()) }) { Text("Done") }
                }
            }
            if (chosen.isNotEmpty() || chosenType != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            chosenType?.let { t -> PickChip(t.copy(label = "${t.label}  ✕"), selected = true) { chosenType = null } }
                            chosen.forEach { name ->
                                val tag = catalog.tag(name)
                                PickChip(tag.copy(label = "${tag.label}  ✕"), selected = true) { chosen.remove(name) }
                            }
                        }
                        if (chosen.size > 1) Text("The first tag counts in your totals; every tag gets its own total too.", style = MaterialTheme.typography.bodySmall, color = Faint)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.replace(",", "").take(30) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus),
                    placeholder = { Text("Type a tag, like petrol or kids") },
                    singleLine = true,
                )
            }
            if (query.isNotBlank()) {
                matches.exact?.let { exact -> item { PickChip(exact.copy(label = "Add ${exact.label}"), selected = true) { add(exact.label) } } }
                if (matches.close.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel(if (matches.exact == null) "Did you mean" else "Also close")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                matches.close.forEach { PickChip(it, selected = false) { add(it.label) } }
                            }
                        }
                    }
                }
                if (matches.exact == null) {
                    item { Button(onClick = { confirming = query.trim() }, modifier = Modifier.fillMaxWidth()) { Text("＋ Create tag “${Tags.clean(query)}”") } }
                }
            } else {
                val suggested = fromHints.filter { it.label !in chosen }.distinctBy { it.id }
                if (suggested.isNotEmpty() || ideas.any { it !in chosen }) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SectionLabel("Suggested")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggested.forEach { PickChip(it, selected = false) { add(it.label) } }
                                ideas.filter { it !in chosen }.forEach { idea -> IdeaChip(idea) { confirming = idea } }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("Your tags")
                        if (tags.isEmpty()) {
                            Text("None yet. Type a name above to make your first tag.", color = Muted)
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                tags.forEach { tag ->
                                    val on = tag.label in chosen
                                    PickChip(tag, selected = on) { if (on) chosen.remove(tag.label) else add(tag.label) }
                                }
                            }
                        }
                    }
                }
                if (allowTypes) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${if (showOthers) "▾" else "▸"}  Not spending: transfers, ATM cash, salary, lent…",
                                Modifier.clickable { showOthers = !showOthers },
                                color = Blue,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (showOthers) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Category.SYSTEM.forEach { t ->
                                        PickChip(t, selected = t.id == chosenType?.id) { chosenType = if (t.id == chosenType?.id) null else t }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirming?.let { name ->
        CreateCategoryDialog(
            name = name,
            similar = CategoryMatch.find(name, tags).close,
            onUseExisting = {
                confirming = null
                add(it.label)
            },
            onDismiss = { confirming = null },
            onCreate = { label, emoji ->
                confirming = null
                scope.launch { add(onCreate(label, emoji).label) }
            },
        )
    }
}
