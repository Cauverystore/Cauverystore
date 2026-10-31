package com.cauverystore.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cauverystore.android.CauveryApp
import com.cauverystore.android.R
import com.cauverystore.android.data.Category
import com.cauverystore.android.data.Product
import com.cauverystore.android.data.isUnauthorized
import com.cauverystore.android.data.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private data class HomeData(val categories: List<Category>, val featured: List<Product>, val all: List<Product>)

@Composable
fun HomeScreen(app: CauveryApp, nav: NavHostController) {
    val fallback = stringResource(R.string.error_generic)
    var state by remember { mutableStateOf<Load<HomeData>>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        state = Load.Loading
        state = try {
            coroutineScopeLoad(app)
        } catch (e: Exception) {
            Load.Failed(e.userMessage(fallback))
        }
    }

    // White status-bar icons over the teal header; restored when leaving Home.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    Column(Modifier.fillMaxSize()) {
        // Header: brand + search box
        Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate(Routes.products()) },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.search_hint))
                    }
                }
            }
        }

        when (val s = state) {
            Load.Loading -> LoadingBox()
            is Load.Failed -> ErrorBox(s.message, onRetry = { reload++ })
            is Load.Done -> HomeContent(s.value, nav)
        }
    }
}

private suspend fun coroutineScopeLoad(app: CauveryApp): Load<HomeData> = kotlinx.coroutines.coroutineScope {
    val cats = async { app.network.api.categories() }
    val featured = async { runCatching { app.network.api.featured() }.getOrDefault(emptyList()) }
    val all = async { app.network.api.products() }
    Load.Done(HomeData(cats.await(), featured.await(), all.await()))
}

@Composable
private fun HomeContent(data: HomeData, nav: NavHostController) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { PromiseStrip() }

        if (data.categories.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionTitle(stringResource(R.string.shop_by_category))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(data.categories) { c ->
                            AssistChip(onClick = { nav.navigate(Routes.products(category = c.name)) }, label = { Text(c.name) })
                        }
                    }
                }
            }
        }

        if (data.featured.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.featured)) }
            items(data.featured.take(8), key = { "f" + it.id }) { p -> ProductCard(p, { nav.navigate(Routes.product(p.id)) }) }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.all_products)) }
        if (data.all.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = stringResource(R.string.no_products_title),
                    body = stringResource(R.string.no_products_body),
                )
            }
        } else {
            items(data.all, key = { it.id }) { p -> ProductCard(p, { nav.navigate(Routes.product(p.id)) }) }
        }
    }
}

@Composable
private fun PromiseStrip() {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Promise(Icons.Outlined.LocalShipping, stringResource(R.string.promise_delivery))
            Promise(Icons.Outlined.Replay, stringResource(R.string.promise_returns))
            Promise(Icons.Outlined.Lock, stringResource(R.string.promise_secure))
        }
    }
}

@Composable
private fun Promise(icon: ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun CategoriesScreen(app: CauveryApp, nav: NavHostController) {
    val fallback = stringResource(R.string.error_generic)
    var state by remember { mutableStateOf<Load<List<Category>>>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        state = Load.Loading
        state = try { Load.Done(app.network.api.categories()) } catch (e: Exception) { Load.Failed(e.userMessage(fallback)) }
    }
    Screen(stringResource(R.string.tab_categories)) { pad ->
        when (val s = state) {
            Load.Loading -> LoadingBox(Modifier.padding(pad))
            is Load.Failed -> ErrorBox(s.message, { reload++ }, Modifier.padding(pad))
            is Load.Done -> if (s.value.isEmpty()) {
                EmptyState(Icons.Outlined.Category, stringResource(R.string.no_categories), modifier = Modifier.padding(pad))
            } else LazyColumn(contentPadding = pad) {
                items(s.value, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.name) },
                        supportingContent = c.description?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                        leadingContent = {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) { Text(c.name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        },
                        trailingContent = { Icon(Icons.AutoMirroredKeyboardArrowRight, null) },
                        modifier = Modifier.clickable { nav.navigate(Routes.products(category = c.name)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private val Icons.AutoMirroredKeyboardArrowRight get() = Icons.AutoMirrored.Outlined.KeyboardArrowRight

@Composable
fun ProductListScreen(app: CauveryApp, nav: NavHostController, category: String?, initialQuery: String) {
    val fallback = stringResource(R.string.error_generic)
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var submitted by rememberSaveable { mutableStateOf(initialQuery) }
    var state by remember { mutableStateOf<Load<List<Product>>>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(submitted, reload) {
        state = Load.Loading
        state = try {
            Load.Done(app.network.api.search(name = submitted.ifBlank { null }, category = category).content)
        } catch (e: Exception) {
            Load.Failed(e.userMessage(fallback))
        }
    }
    Screen(category ?: stringResource(R.string.search), nav) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submitted = query.trim() }),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            when (val s = state) {
                Load.Loading -> LoadingBox()
                is Load.Failed -> ErrorBox(s.message, { reload++ })
                is Load.Done -> if (s.value.isEmpty()) {
                    EmptyState(
                        Icons.Outlined.SearchOff,
                        stringResource(if (submitted.isBlank()) R.string.no_products_title else R.string.no_results),
                        stringResource(R.string.no_products_body),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(s.value, key = { it.id }) { p -> ProductCard(p, { nav.navigate(Routes.product(p.id)) }) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductDetailScreen(app: CauveryApp, nav: NavHostController, id: Long) {
    val fallback = stringResource(R.string.error_generic)
    val added = stringResource(R.string.added_to_cart)
    val goToCart = stringResource(R.string.go_to_cart)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var state by remember { mutableStateOf<Load<Product>>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(reload) {
        state = Load.Loading
        state = try { Load.Done(app.network.api.product(id)) } catch (e: Exception) { Load.Failed(e.userMessage(fallback)) }
    }

    fun addToCart(thenCheckout: Boolean) {
        if (!app.session.isSignedIn) { nav.navigate(Routes.LOGIN); return }
        scope.launch {
            adding = true
            try {
                app.network.api.addToCart(id, 1)
                runCatching { app.network.api.cart() }.onSuccess { app.cartCount.value = it.totalItems }
                if (thenCheckout) nav.navigate(Routes.CART)
                else {
                    val r = snackbar.showSnackbar(added, actionLabel = goToCart, withDismissAction = true)
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) nav.switchTab(Routes.CART)
                }
            } catch (e: Exception) {
                if (e.isUnauthorized() && !app.session.isSignedIn) nav.navigate(Routes.LOGIN)
                else snackbar.showSnackbar(errorTextPlain(app, e.userMessage(fallback)))
            } finally {
                adding = false
            }
        }
    }

    val product = (state as? Load.Done)?.value
    Screen(
        title = product?.name ?: "",
        nav = nav,
        snackbar = snackbar,
        bottomBar = {
            if (product != null) {
                val inStock = (product.stock ?: 1) > 0
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            stringResource(R.string.add_to_cart), { addToCart(false) },
                            Modifier.weight(1f),
                        )
                        Button(onClick = { addToCart(true) }, enabled = inStock && !adding, modifier = Modifier.weight(1f)) {
                            Text(stringResource(if (inStock) R.string.buy_now else R.string.out_of_stock))
                        }
                    }
                }
            }
        },
    ) { pad ->
        when (val s = state) {
            Load.Loading -> LoadingBox(Modifier.padding(pad))
            is Load.Failed -> ErrorBox(s.message, { reload++ }, Modifier.padding(pad))
            is Load.Done -> ProductBody(s.value, Modifier.padding(pad))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductBody(p: Product, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val urls = p.imageUrls
        if (urls.isEmpty()) {
            RemoteImage(null, Modifier.fillMaxWidth().aspectRatio(1f))
        } else {
            val pager = rememberPagerState { urls.size }
            Box {
                HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(1f)) { page ->
                    RemoteImage(urls[page], Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                if (urls.size > 1) {
                    Text(
                        "${pager.currentPage + 1}/${urls.size}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            if (!p.brand.isNullOrBlank()) Text(p.brand, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(p.name, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            PriceRow(p.sellingPrice, p.price, big = true)
            Text(stringResource(R.string.inclusive_of_taxes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val stock = p.stock
            when {
                stock != null && stock <= 0 -> Text(stringResource(R.string.out_of_stock), color = MaterialTheme.colorScheme.error)
                stock != null && stock <= 5 -> Text(stringResource(R.string.only_left, stock), color = MaterialTheme.colorScheme.error)
                else -> Text(stringResource(R.string.in_stock), color = Saving)
            }
            Spacer(Modifier.height(12.dp))
            PromiseStrip()
            val details = listOfNotNull(
                p.color?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.attr_color) to it },
                p.size?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.attr_size) to it },
                p.material?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.attr_material) to it },
                p.warranty?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.attr_warranty) to it },
                p.category?.name?.let { stringResource(R.string.attr_category) to it },
            )
            if (details.isNotEmpty()) {
                SectionTitle(stringResource(R.string.details))
                details.forEach { (k, v) ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(k, Modifier.width(110.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v)
                    }
                }
            }
            val desc = p.description ?: p.shortDescription
            if (!desc.isNullOrBlank()) {
                SectionTitle(stringResource(R.string.description))
                Text(desc, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

/** Non-composable version of [errorText] for snackbars. */
fun errorTextPlain(app: CauveryApp, message: String): String =
    if (message == com.cauverystore.android.data.NETWORK_ERROR) app.getString(R.string.error_network) else message
