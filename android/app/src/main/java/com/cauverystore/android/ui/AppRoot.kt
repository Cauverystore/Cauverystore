package com.cauverystore.android.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cauverystore.android.CauveryApp
import com.cauverystore.android.PaymentOutcome
import com.cauverystore.android.R
import org.json.JSONObject

typealias StartPayment = (options: JSONObject, key: String, onResult: (PaymentOutcome) -> Unit) -> Unit

object Routes {
    const val HOME = "home"
    const val CATEGORIES = "categories"
    const val CART = "cart"
    const val ACCOUNT = "account"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CHECKOUT = "checkout"
    const val ORDERS = "orders"

    fun products(category: String? = null, query: String? = null): String =
        "products?category=${Uri.encode(category ?: "")}&q=${Uri.encode(query ?: "")}"

    fun product(id: Long) = "product/$id"
    fun order(id: Long) = "order/$id"
    fun orderSuccess(id: Long) = "order-success/$id"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Tab(Routes.CATEGORIES, R.string.tab_categories, Icons.Outlined.Category, Icons.Filled.Category),
    Tab(Routes.CART, R.string.tab_cart, Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart),
    Tab(Routes.ACCOUNT, R.string.tab_account, Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun AppRoot(app: CauveryApp, startPayment: StartPayment) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val cartCount by app.cartCount.collectAsState()
    val user by app.session.user.collectAsState()

    // Keep the cart badge right after signing in or out.
    LaunchedEffect(user) {
        if (user == null) app.cartCount.value = 0
        else runCatching { app.network.api.cart() }.onSuccess { app.cartCount.value = it.totalItems }
    }

    Scaffold(
        bottomBar = {
            if (current in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = current == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab.route) },
                            icon = {
                                if (tab.route == Routes.CART && cartCount > 0) {
                                    BadgedBox(badge = { Badge { Text(cartCount.toString()) } }) {
                                        Icon(if (selected) tab.selectedIcon else tab.icon, null)
                                    }
                                } else {
                                    Icon(if (selected) tab.selectedIcon else tab.icon, null)
                                }
                            },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.HOME) { HomeScreen(app, nav) }
            composable(Routes.CATEGORIES) { CategoriesScreen(app, nav) }
            composable(Routes.CART) { CartScreen(app, nav) }
            composable(Routes.ACCOUNT) { AccountScreen(app, nav) }
            composable(
                "products?category={category}&q={q}",
                arguments = listOf(
                    navArgument("category") { type = NavType.StringType; defaultValue = "" },
                    navArgument("q") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                ProductListScreen(
                    app, nav,
                    category = entry.arguments?.getString("category").orEmpty().ifBlank { null },
                    initialQuery = entry.arguments?.getString("q").orEmpty(),
                )
            }
            composable("product/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ProductDetailScreen(app, nav, entry.arguments!!.getLong("id"))
            }
            composable(Routes.LOGIN) { LoginScreen(app, nav) }
            composable(Routes.REGISTER) { RegisterScreen(app, nav) }
            composable(Routes.CHECKOUT) { CheckoutScreen(app, nav, startPayment) }
            composable("order-success/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                OrderSuccessScreen(nav, entry.arguments!!.getLong("id"))
            }
            composable(Routes.ORDERS) { OrdersScreen(app, nav) }
            composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                OrderDetailScreen(app, nav, entry.arguments!!.getLong("id"))
            }
        }
    }
}

fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
