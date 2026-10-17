package app.kharcha.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

@Composable
fun AppRoot(container: AppContainer, startWithOnboarding: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (startWithOnboarding) "onboarding" else "main") {
        composable("onboarding") {
            OnboardingScreen(container) {
                container.prefs.onboarded = true
                nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
            }
        }
        composable("main") {
            MainScreen(container, openTransaction = { id -> nav.navigate("transaction/$id") })
        }
        composable("transaction/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            DetailScreen(container, entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    TRANSACTIONS("Transactions", Icons.Outlined.Receipt),
    INSIGHTS("Insights", Icons.Outlined.Lightbulb),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

@Composable
private fun MainScreen(container: AppContainer, openTransaction: (Long) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var categoryFilter by rememberSaveable { mutableStateOf<Category?>(null) }
    val transactions by container.repository.transactions.collectAsStateWithLifecycle(initialValue = emptyList())
    val limit by container.prefs.monthlyLimit.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Paper,
        bottomBar = {
            NavigationBar(containerColor = CardColor) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            tab = item
                            if (item != Tab.TRANSACTIONS) categoryFilter = null
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> HomeScreen(
                transactions = transactions,
                monthlyLimitPaise = limit,
                onCategory = { category ->
                    categoryFilter = category
                    tab = Tab.TRANSACTIONS
                },
                onTransaction = openTransaction,
                modifier = modifier,
            )
            Tab.TRANSACTIONS -> TransactionsScreen(
                transactions = transactions,
                categoryFilter = categoryFilter,
                onClearFilter = { categoryFilter = null },
                onTransaction = openTransaction,
                modifier = modifier,
            )
            Tab.INSIGHTS -> InsightsScreen(transactions, limit, modifier)
            Tab.SETTINGS -> SettingsScreen(container, modifier)
        }
    }
}
