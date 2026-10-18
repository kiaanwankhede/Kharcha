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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import app.kharcha.OpenRequest
import app.kharcha.core.Category
import app.kharcha.core.CategoryCatalog
import app.kharcha.core.Transaction
import kotlinx.coroutines.launch

/**
 * [openReviewRequest] goes up by one each time a notification asks to open the review inbox, and
 * [addCashRequest] each time the widget or the Quick Settings tile asks to add cash.
 */
@Composable
fun AppRoot(
    container: AppContainer,
    startWithOnboarding: Boolean,
    openReviewRequest: Int = 0,
    addCashRequest: Int = 0,
    openRequest: OpenRequest = OpenRequest(),
) {
    val nav = rememberNavController()
    LaunchedEffect(openRequest.count) {
        val route = openRequest.route
        if (openRequest.count > 0 && route != null && container.prefs.onboarded) nav.navigate(route) { launchSingleTop = true }
    }
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
                addCashRequest = addCashRequest,
                openTabRequest = openRequest,
                openTransaction = { id -> nav.navigate("transaction/$id") },
                openRoute = { route -> nav.navigate(route) },
            )
        }
        composable("unread") {
            UnreadScreen(container, onBack = { nav.popBackStack() })
        }
        composable("budgets") {
            BudgetsScreen(container, onBack = { nav.popBackStack() })
        }
        composable("accounts") {
            AccountsScreen(container, onBack = { nav.popBackStack() })
        }
        composable("upcoming") {
            UpcomingScreen(container, onBack = { nav.popBackStack() })
        }
        composable("people") {
            PeopleScreen(container, onBack = { nav.popBackStack() }, onTransaction = { id -> nav.navigate("transaction/$id") })
        }
        composable("trips") {
            TripsScreen(container, onBack = { nav.popBackStack() }, onTransaction = { id -> nav.navigate("transaction/$id") })
        }
        composable("reimburse") {
            ReimburseScreen(container, onBack = { nav.popBackStack() }, onTransaction = { id -> nav.navigate("transaction/$id") })
        }
        composable("tax") {
            TaxScreen(container, onBack = { nav.popBackStack() })
        }
        composable("merchant/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
            MerchantScreen(
                container,
                key = entry.arguments?.getString("key").orEmpty(),
                onBack = { nav.popBackStack() },
                onTransaction = { id -> nav.navigate("transaction/$id") },
            )
        }
        composable("goals") {
            GoalsScreen(container, onBack = { nav.popBackStack() })
        }
        composable("categories") {
            CategoriesScreen(container, onBack = { nav.popBackStack() })
        }
        composable("transaction/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            DetailScreen(
                container,
                entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
                onOpenMerchant = { key -> nav.navigate("merchant/${android.net.Uri.encode(key)}") },
            )
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
    addCashRequest: Int,
    openTabRequest: OpenRequest,
    openTransaction: (Long) -> Unit,
    openRoute: (String) -> Unit,
) {
    val openCategories = { openRoute("categories") }
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
    val usage = remember(reviewed, catalog) { mostUsedCategories(reviewed, catalog) }
    val byId = remember(all) { all.associateBy { it.id } }
    val unread by repository.unread.collectAsStateWithLifecycle(initialValue = emptyList())
    val budgetList by repository.budgets.collectAsStateWithLifecycle(initialValue = emptyList())
    val bills by repository.cardBills.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = java.time.LocalDate.now(Zone)
    val budgetStatus = remember(budgetList, reviewed, catalog) {
        app.kharcha.core.Budgets.status(budgetList, reviewed, today, catalog::groupOf)
    }
    val upcoming = remember(bills, all) {
        val live = all.filter { it.status != app.kharcha.core.ReviewStatus.IGNORED }
        app.kharcha.core.Upcoming.next(bills, app.kharcha.core.InsightsEngine(Zone).recurring(reviewed, today), live, today)
    }
    val accountCount = remember(all) { all.mapNotNull { t -> t.accountLast4?.let { (t.bank.orEmpty() + it).lowercase() } }.distinct().size }
    val snackbar = remember { SnackbarHostState() }
    val showUndo: (String, suspend () -> Unit) -> Unit = { message, undo ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    LaunchedEffect(pending.isEmpty(), all.isEmpty()) {
        if (pending.isEmpty() && all.isNotEmpty()) container.prefs.markInboxCleared()
    }
    val streak = remember(pending.size) { container.prefs.reviewStreak }

    LaunchedEffect(openReviewRequest) {
        if (openReviewRequest > 0) tab = Tab.REVIEW
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showWhatsNew by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (WhatsNew.shouldShow(context, container.prefs.lastSeenVersion)) showWhatsNew = true
        container.prefs.lastSeenVersion = WhatsNew.versionCode(context)
    }
    if (showWhatsNew) WhatsNewDialog(onDismiss = { showWhatsNew = false })
    LaunchedEffect(addCashRequest) {
        if (addCashRequest > 0) addingCash = true
    }
    LaunchedEffect(openTabRequest.count) {
        if (openTabRequest.count > 0) Tab.entries.firstOrNull { it.name.equals(openTabRequest.tab, ignoreCase = true) }?.let { tab = it }
    }

    Scaffold(
        containerColor = Paper,
        snackbarHost = { SnackbarHost(snackbar) },
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
                catalog = catalog,
                pendingCount = pending.size,
                pendingPaise = pending.filter { it.direction == app.kharcha.core.Direction.DEBIT }.sumOf { it.amountPaise },
                monthlyLimitPaise = limit,
                onOpenReview = { tab = Tab.REVIEW },
                budgets = budgetStatus,
                upcoming = upcoming,
                accountCount = accountCount,
                onOpenRoute = openRoute,
                onCategory = { category ->
                    categoryFilterId = category.id
                    tab = Tab.TRANSACTIONS
                },
                onTransaction = openTransaction,
                modifier = modifier,
            )
            Tab.REVIEW -> ReviewScreen(
                container = container,
                pending = pending,
                catalog = catalog,
                usage = usage,
                lookup = { byId[it] },
                unreadCount = unread.size,
                onOpenUnread = { openRoute("unread") },
                onUndoable = showUndo,
                onOpenTransaction = openTransaction,
                modifier = modifier,
                streak = streak,
            )
            Tab.TRANSACTIONS -> TransactionsScreen(
                transactions = reviewed,
                ignored = ignored,
                categoryFilter = categoryFilter,
                onClearFilter = { categoryFilterId = null },
                onTransaction = openTransaction,
                modifier = modifier,
            )
            Tab.INSIGHTS -> InsightsScreen(reviewed, limit, modifier, catalog::groupOf, openRoute)
            Tab.SETTINGS -> SettingsScreen(container, openCategories, modifier, openRoute)
        }
    }

    if (addingCash) {
        AddCashDialog(
            catalog = catalog,
            onDismiss = { addingCash = false },
            onCreateCategory = { label, emoji -> repository.saveCategory(Category.forTag(label, emoji)) },
            onSave = { amountPaise, whatFor, tags, timestampMillis ->
                addingCash = false
                scope.launch { repository.addCash(amountPaise, whatFor, Category.OTHER, timestampMillis, tags) }
            },
        )
    }
}

/** The tags used most, for one-tap choices when reviewing; the newest ones until there's history. */
private fun mostUsedCategories(reviewed: List<Transaction>, catalog: CategoryCatalog): List<Category> {
    val counts = reviewed.flatMap { it.tags }.groupingBy { it }.eachCount()
    return catalog.tags.sortedByDescending { counts[it.label] ?: 0 }.take(5)
}
