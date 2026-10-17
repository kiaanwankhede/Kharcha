@file:OptIn(ExperimentalMaterial3Api::class)

package app.kharcha.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.kharcha.AppContainer
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Transaction
import kotlinx.coroutines.launch

/** [openReviewRequest] goes up by one each time a notification asks to open the review inbox. */
@Composable
fun AppRoot(container: AppContainer, startWithOnboarding: Boolean, openReviewRequest: Int = 0) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (startWithOnboarding) "onboarding" else "main") {
        composable("onboarding") {
            OnboardingScreen(container) {
                container.prefs.onboarded = true
                nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
            }
        }
        composable("main") {
            MainScreen(
                container,
                openReviewRequest = openReviewRequest,
                openTransaction = { id -> nav.navigate("transaction/$id") },
                openCategories = { nav.navigate("categories") },
            )
        }
        composable("categories") {
            CategoriesScreen(container, onBack = { nav.popBackStack() })
        }
        composable("transaction/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            DetailScreen(container, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    REVIEW("Review", Icons.Outlined.Inbox),
    TRANSACTIONS("History", Icons.Outlined.Receipt),
    INSIGHTS("Insights", Icons.Outlined.Lightbulb),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

@Composable
private fun MainScreen(
    container: AppContainer,
    openReviewRequest: Int,
    openTransaction: (Long) -> Unit,
    openCategories: () -> Unit,
) {
    val repository = container.repository
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var categoryFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog by repository.catalog.collectAsStateWithLifecycle(initialValue = CategoryCatalog())
    val all by repository.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val limit by container.prefs.monthlyLimit.collectAsStateWithLifecycle()
    var addingCash by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val categoryFilter = categoryFilterId?.let(catalog::byId)

    // Only reviewed payments count anywhere; pending ones live in the Review tab until confirmed.
    val reviewed = remember(all) { all.filter { it.status == app.kharcha.core.ReviewStatus.REVIEWED } }
    val pending = remember(all) { all.filter { it.status == app.kharcha.core.ReviewStatus.PENDING } }
    val ignored = remember(all) { all.filter { it.status == app.kharcha.core.ReviewStatus.IGNORED } }
    val usage = remember(reviewed) { mostUsedCategories(reviewed) }

    LaunchedEffect(openReviewRequest) {
        if (openReviewRequest > 0) tab = Tab.REVIEW
    }

    Scaffold(
        containerColor = Paper,
        floatingActionButton = {
            if (tab == Tab.HOME || tab == Tab.TRANSACTIONS) {
                ExtendedFloatingActionButton(
                    onClick = { addingCash = true },
                    containerColor = Navy,
                    contentColor = Paper,
                    text = { Text("＋ Add cash") },
                    icon = {},
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CardColor) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            tab = item
                            if (item != Tab.TRANSACTIONS) categoryFilterId = null
                        },
                        icon = {
                            if (item == Tab.REVIEW && pending.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${pending.size}") } }) {
                                    Icon(item.icon, contentDescription = null)
                                }
                            } else {
                                Icon(item.icon, contentDescription = null)
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> HomeScreen(
                transactions = reviewed,
                pendingCount = pending.size,
                monthlyLimitPaise = limit,
                onOpenReview = { tab = Tab.REVIEW },
                onCategory = { category ->
                    categoryFilterId = category.id
                    tab = Tab.TRANSACTIONS
                },
                onTransaction = openTransaction,
                modifier = modifier,
            )
            Tab.REVIEW -> ReviewScreen(container, pending, catalog, usage, modifier)
            Tab.TRANSACTIONS -> TransactionsScreen(
                transactions = reviewed,
                ignored = ignored,
                categoryFilter = categoryFilter,
                onClearFilter = { categoryFilterId = null },
                onTransaction = openTransaction,
                modifier = modifier,
            )
            Tab.INSIGHTS -> InsightsScreen(reviewed, limit, modifier)
            Tab.SETTINGS -> SettingsScreen(container, openCategories, modifier)
        }
    }

    if (addingCash) {
        AddCashDialog(
            catalog = catalog,
            onDismiss = { addingCash = false },
            onSave = { amountPaise, whatFor, category, timestampMillis ->
                addingCash = false
                scope.launch { repository.addCash(amountPaise, whatFor, category, timestampMillis) }
            },
        )
    }
}

/** The categories the user picks most, for one-tap choices when reviewing. */
private fun mostUsedCategories(reviewed: List<Transaction>): List<Category> =
    reviewed.asSequence()
        .filter { it.category.countsAsSpend }
        .groupingBy { it.category }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
        .take(5)
        .toList()
        .ifEmpty { listOf(Category.FOOD, Category.GROCERIES, Category.TRAVEL, Category.SHOPPING, Category.BILLS) }
