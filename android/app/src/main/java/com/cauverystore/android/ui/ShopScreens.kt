package com.cauverystore.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cauverystore.android.CauveryApp
import com.cauverystore.android.PaymentOutcome
import com.cauverystore.android.R
import com.cauverystore.android.data.Address
import com.cauverystore.android.data.Bill
import com.cauverystore.android.data.BillRequest
import com.cauverystore.android.data.Cart
import com.cauverystore.android.data.CartItem
import com.cauverystore.android.data.OrderDetail
import com.cauverystore.android.data.OrderDetailItem
import com.cauverystore.android.data.OrderLine
import com.cauverystore.android.data.OrderPayload
import com.cauverystore.android.data.OrderSummary
import com.cauverystore.android.data.VerifyRequest
import com.cauverystore.android.data.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Same rule as the website and backend: free delivery from ₹500, otherwise ₹40. */
fun deliveryFor(subtotal: Double): Double = if (subtotal >= 500 || subtotal <= 0) 0.0 else 40.0

// ---------------------------------------------------------------- Cart

@Composable
fun CartScreen(app: CauveryApp, nav: NavHostController) {
    val user by app.session.user.collectAsState()
    val fallback = stringResource(R.string.error_generic)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var state by remember { mutableStateOf<Load<Cart>>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    var busyItem by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(user, reload) {
        if (user == null) return@LaunchedEffect
        if (state !is Load.Done) state = Load.Loading
        state = try {
            app.network.api.cart().also { app.cartCount.value = it.totalItems }.let { Load.Done(it) }
        } catch (e: Exception) {
            Load.Failed(e.userMessage(fallback))
        }
    }

    fun change(item: CartItem, qty: Int) {
        scope.launch {
            busyItem = item.id
            try {
                if (qty <= 0) app.network.api.removeFromCart(item.id) else app.network.api.updateQuantity(item.id, qty)
                reload++
            } catch (e: Exception) {
                snackbar.showSnackbar(errorTextPlain(app, e.userMessage(fallback)))
            } finally {
                busyItem = null
            }
        }
    }

    val cart = (state as? Load.Done)?.value
    Screen(
        title = stringResource(R.string.tab_cart),
        snackbar = snackbar,
        bottomBar = {
            if (user != null && cart != null && cart.items.isNotEmpty()) {
                val subtotal = cart.items.sumOf { (it.product.price ?: 0.0) * it.quantity }
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(money(subtotal + deliveryFor(subtotal)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.total), style = MaterialTheme.typography.labelSmall)
                        }
                        Button(onClick = { nav.navigate(Routes.CHECKOUT) }) { Text(stringResource(R.string.checkout)) }
                    }
                }
            }
        },
    ) { pad ->
        if (user == null) {
            SignInPrompt(stringResource(R.string.sign_in_for_cart), { nav.navigate(Routes.LOGIN) }, Icons.Outlined.ShoppingCart, Modifier.padding(pad))
            return@Screen
        }
        when (val s = state) {
            Load.Loading -> LoadingBox(Modifier.padding(pad))
            is Load.Failed -> ErrorBox(s.message, { reload++ }, Modifier.padding(pad))
            is Load.Done -> if (s.value.items.isEmpty()) {
                EmptyState(
                    Icons.Outlined.ShoppingCart, stringResource(R.string.cart_empty), stringResource(R.string.cart_empty_body),
                    stringResource(R.string.start_shopping), { nav.switchTab(Routes.HOME) }, Modifier.padding(pad),
                )
            } else {
                val subtotal = s.value.items.sumOf { (it.product.price ?: 0.0) * it.quantity }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(pad)) {
                    items(s.value.items, key = { it.id }) { item ->
                        CartRow(item, busy = busyItem == item.id, onOpen = { nav.navigate(Routes.product(item.product.id)) },
                            onQty = { change(item, it) })
                    }
                    item { Totals(subtotal, deliveryFor(subtotal), tax = s.value.tax) }
                    if (subtotal in 0.01..499.99) {
                        item {
                            Text(
                                stringResource(R.string.add_more_for_free_delivery, money(500 - subtotal)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartRow(item: CartItem, busy: Boolean, onOpen: () -> Unit, onQty: (Int) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(10.dp)) {
            RemoteImage(item.product.image, Modifier.size(84.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.product.name, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onOpen))
                Text(money(item.product.price), fontWeight = FontWeight.Bold)
                if (item.product.inStock == false) {
                    Text(stringResource(R.string.out_of_stock), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onQty(item.quantity - 1) }, enabled = !busy) {
                        Icon(if (item.quantity <= 1) Icons.Outlined.Delete else Icons.Outlined.Remove, stringResource(R.string.decrease))
                    }
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(item.quantity.toString())
                    IconButton(onClick = { onQty(item.quantity + 1) }, enabled = !busy) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.increase))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onQty(0) }, enabled = !busy) { Text(stringResource(R.string.remove)) }
                }
            }
        }
    }
}

@Composable
private fun Totals(subtotal: Double, delivery: Double, tax: Double?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TotalLine(stringResource(R.string.subtotal), money(subtotal))
            TotalLine(stringResource(R.string.delivery), if (delivery == 0.0) stringResource(R.string.free) else money(delivery))
            if (tax != null) TotalLine(stringResource(R.string.gst_included), money(tax), small = true)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TotalLine(stringResource(R.string.total), money(subtotal + delivery), bold = true)
        }
    }
}

@Composable
private fun TotalLine(label: String, value: String, bold: Boolean = false, small: Boolean = false) {
    val style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = style, fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, style = style, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

// ---------------------------------------------------------------- Checkout

private data class CheckoutData(val cart: Cart, val addresses: List<Address>)

@Composable
fun CheckoutScreen(app: CauveryApp, nav: NavHostController, startPayment: StartPayment) {
    val fallback = stringResource(R.string.error_generic)
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Load<CheckoutData>>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var newAddress by rememberSaveable { mutableStateOf(false) }
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var line1 by rememberSaveable { mutableStateOf("") }
    var line2 by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var stateName by rememberSaveable { mutableStateOf("Tamil Nadu") }
    var pincode by rememberSaveable { mutableStateOf("") }

    var bill by remember { mutableStateOf<Bill?>(null) }
    var billLoading by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val msgName = stringResource(R.string.err_name)
    val msgPhone = stringResource(R.string.err_phone)
    val msgAddress = stringResource(R.string.err_address)
    val msgCity = stringResource(R.string.err_city)
    val msgState = stringResource(R.string.err_state)
    val msgPin = stringResource(R.string.err_pincode)
    val msgTax = stringResource(R.string.err_tax_unknown)
    val msgCancelled = stringResource(R.string.payment_cancelled)
    val msgPayFailed = stringResource(R.string.payment_failed)
    val msgConfirmFailed = stringResource(R.string.order_confirm_failed)
    val msgNoKey = stringResource(R.string.payment_not_configured)
    val orderDescription = stringResource(R.string.order_payment)
    val appName = stringResource(R.string.app_name)

    LaunchedEffect(reload) {
        state = Load.Loading
        state = try {
            val cart = app.network.api.cart()
            val addresses = runCatching { app.network.api.addresses() }.getOrDefault(emptyList())
            if (selectedId == null && !newAddress) {
                selectedId = addresses.firstOrNull()?.id
                newAddress = addresses.isEmpty()
            }
            Load.Done(CheckoutData(cart, addresses))
        } catch (e: Exception) {
            Load.Failed(e.userMessage(fallback))
        }
    }

    val data = (state as? Load.Done)?.value
    val selected = data?.addresses?.firstOrNull { it.id == selectedId }?.takeIf { !newAddress }
    val shipState = if (selected != null) selected.state.orEmpty() else stateName
    val shipPin = if (selected != null) selected.pincode.orEmpty() else pincode
    val shipCity = if (selected != null) selected.city else city

    // GST depends on where the order is going, so it is worked out by the server once the
    // address is known (same as the website).
    LaunchedEffect(shipState, shipPin, shipCity, data) {
        if (data == null || shipState.isBlank() || shipPin.length < 6) { bill = null; return@LaunchedEffect }
        delay(400)
        billLoading = true
        bill = runCatching { app.network.api.bill(BillRequest(shipState.trim(), shipPin.trim(), shipCity?.trim())) }.getOrNull()
        billLoading = false
    }

    fun validate(): String? {
        if (selected != null) return null
        if (fullName.isBlank()) return msgName
        if (phone.filter(Char::isDigit).length != 10) return msgPhone
        if (line1.isBlank()) return msgAddress
        if (city.isBlank()) return msgCity
        if (stateName.isBlank()) return msgState
        if (pincode.length != 6) return msgPin
        return null
    }

    fun pay() {
        val d = data ?: return
        error = validate()
        if (error != null) return
        val b = bill
        if (b?.canProceed == false) { error = b.message ?: msgTax; return }
        if (b?.totalTax == null && d.cart.tax == null) { error = msgTax; return }

        val subtotal = d.cart.items.sumOf { (it.product.price ?: 0.0) * it.quantity }
        val delivery = deliveryFor(subtotal)
        val total = subtotal + delivery
        val payload = OrderPayload(
            items = d.cart.items.map { OrderLine(it.product.id, it.quantity) },
            addressId = selected?.id,
            fullName = selected?.fullName ?: fullName.trim(),
            phone = selected?.phone ?: phone.trim(),
            line1 = selected?.line1 ?: line1.trim(),
            line2 = selected?.line2 ?: line2.trim().ifBlank { null },
            city = selected?.city ?: city.trim(),
            state = selected?.state ?: stateName.trim(),
            pincode = selected?.pincode ?: pincode.trim(),
            subtotal = subtotal, delivery = delivery, total = total,
        )

        scope.launch {
            paying = true
            try {
                val rp = app.network.api.createPayment(payload.copy(amount = total))
                val key = rp.key
                val rzpOrder = rp.razorpayOrderId
                if (key.isNullOrBlank() || rzpOrder.isNullOrBlank()) { error = msgNoKey; paying = false; return@launch }
                val options = JSONObject().apply {
                    put("name", appName)
                    put("description", orderDescription)
                    put("order_id", rzpOrder)
                    put("currency", rp.currency ?: "INR")
                    put("amount", rp.amount?.toString()?.trim('"')?.toLongOrNull() ?: Math.round(total * 100))
                    put("prefill", JSONObject().apply {
                        put("name", payload.fullName)
                        put("contact", payload.phone)
                        app.session.user.value?.email?.let { put("email", it) }
                    })
                    put("theme", JSONObject().put("color", "#0E5C5C"))
                    // The store does not offer cash on delivery.
                    put("config", JSONObject().put("display", JSONObject().put("hide", JSONArray().put(JSONObject().put("method", "cod")))))
                }
                startPayment(options, key) { outcome ->
                    when (outcome) {
                        is PaymentOutcome.Failure -> {
                            // Razorpay uses code 0 when the customer closes the checkout.
                            error = if (outcome.code == 0) msgCancelled else msgPayFailed + (outcome.description?.let { ": $it" } ?: "")
                            paying = false
                        }
                        is PaymentOutcome.Success -> scope.launch {
                            try {
                                val v = app.network.api.verifyPayment(
                                    VerifyRequest(outcome.paymentId, outcome.orderId.ifBlank { rzpOrder }, outcome.signature)
                                )
                                val placed = app.network.api.placeOrder(
                                    payload.copy(
                                        paymentMethod = if (v.method == "cod") "COD" else "ONLINE",
                                        paymentId = outcome.paymentId,
                                        razorpayOrderId = outcome.orderId.ifBlank { rzpOrder },
                                    )
                                )
                                app.cartCount.value = 0
                                nav.navigate(Routes.orderSuccess(placed.id ?: 0)) {
                                    popUpTo(Routes.CART) { inclusive = false }
                                }
                            } catch (e: Exception) {
                                error = msgConfirmFailed
                                paying = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                error = e.userMessage(fallback)
                paying = false
            }
        }
    }

    Screen(
        title = stringResource(R.string.checkout),
        nav = nav,
        bottomBar = {
            if (data != null && data.cart.items.isNotEmpty()) {
                val subtotal = data.cart.items.sumOf { (it.product.price ?: 0.0) * it.quantity }
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                        Button(onClick = ::pay, enabled = !paying && !billLoading, modifier = Modifier.fillMaxWidth()) {
                            if (paying) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.pay_amount, money(subtotal + deliveryFor(subtotal))))
                        }
                    }
                }
            }
        },
    ) { pad ->
        when (val s = state) {
            Load.Loading -> LoadingBox(Modifier.padding(pad))
            is Load.Failed -> ErrorBox(s.message, { reload++ }, Modifier.padding(pad))
            is Load.Done -> {
                val d = s.value
                if (d.cart.items.isEmpty()) {
                    EmptyState(Icons.Outlined.ShoppingCart, stringResource(R.string.cart_empty), modifier = Modifier.padding(pad))
                    return@Screen
                }
                Column(
                    Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionTitle(stringResource(R.string.delivery_address))
                    d.addresses.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().selectable(!newAddress && selectedId == a.id) { selectedId = a.id; newAddress = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(!newAddress && selectedId == a.id, onClick = { selectedId = a.id; newAddress = false })
                            Column {
                                Text(a.fullName.orEmpty(), fontWeight = FontWeight.SemiBold)
                                Text(a.oneLine(), style = MaterialTheme.typography.bodySmall)
                                a.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    if (d.addresses.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().selectable(newAddress) { newAddress = true }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(newAddress, onClick = { newAddress = true })
                            Text(stringResource(R.string.new_address))
                        }
                    }
                    if (newAddress) {
                        Field(fullName, { fullName = it }, R.string.full_name)
                        Field(phone, { phone = it.filter(Char::isDigit).take(10) }, R.string.phone, KeyboardType.Phone)
                        Field(line1, { line1 = it }, R.string.address_line1)
                        Field(line2, { line2 = it }, R.string.address_line2)
                        Field(city, { city = it }, R.string.city)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(stateName, { stateName = it }, R.string.state, modifier = Modifier.weight(1f))
                            Field(pincode, { pincode = it.filter(Char::isDigit).take(6) }, R.string.pincode, KeyboardType.Number, Modifier.weight(1f))
                        }
                    }

                    SectionTitle(stringResource(R.string.order_summary))
                    d.cart.items.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("${item.product.name} × ${item.quantity}", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(money((item.product.price ?: 0.0) * item.quantity))
                        }
                    }
                    val subtotal = d.cart.items.sumOf { (it.product.price ?: 0.0) * it.quantity }
                    Totals(subtotal, deliveryFor(subtotal), tax = bill?.totalTax ?: d.cart.tax)
                    if (billLoading) Text(stringResource(R.string.calculating_tax), style = MaterialTheme.typography.bodySmall)
                    bill?.takeIf { it.canProceed == false }?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(stringResource(R.string.payment_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: Int,
    keyboard: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value, onChange, label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = modifier,
    )
}

// ---------------------------------------------------------------- Orders

@Composable
fun OrderSuccessScreen(nav: NavHostController, id: Long) {
    Screen(stringResource(R.string.order_placed)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState(
                Icons.Outlined.CheckCircle,
                stringResource(R.string.order_placed_title),
                if (id > 0) stringResource(R.string.order_number, id) else null,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                if (id > 0) Button(onClick = { nav.navigate(Routes.order(id)) { popUpTo(Routes.HOME) } }) { Text(stringResource(R.string.view_order)) }
                Spacer(Modifier.width(10.dp))
                SecondaryButton(stringResource(R.string.continue_shopping), { nav.switchTab(Routes.HOME) })
            }
        }
    }
}

@Composable
fun OrdersScreen(app: CauveryApp, nav: NavHostController) {
    val fallback = stringResource(R.string.error_generic)
    var state by remember { mutableStateOf<Load<List<OrderSummary>>>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        state = Load.Loading
        state = try { Load.Done(app.network.api.orders().sortedByDescending { it.id }) } catch (e: Exception) { Load.Failed(e.userMessage(fallback)) }
    }
    Screen(stringResource(R.string.my_orders), nav) { pad ->
        when (val s = state) {
            Load.Loading -> LoadingBox(Modifier.padding(pad))
            is Load.Failed -> ErrorBox(s.message, { reload++ }, Modifier.padding(pad))
            is Load.Done -> if (s.value.isEmpty()) {
                EmptyState(Icons.Outlined.Receipt, stringResource(R.string.no_orders), stringResource(R.string.no_orders_body),
                    stringResource(R.string.start_shopping), { nav.switchTab(Routes.HOME) }, Modifier.padding(pad))
            } else LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(pad)) {
                items(s.value, key = { it.id }) { o ->
                    Card(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.order(o.id)) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RemoteImage(o.items.firstOrNull()?.productImage, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.order_number, o.id), fontWeight = FontWeight.SemiBold)
                                Text(o.items.joinToString { it.productName.orEmpty() }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                                Text(o.createdAt?.take(10).orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(o.totalAmount), fontWeight = FontWeight.Bold)
                                StatusLabel(o.status)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLabel(status: String?) {
    if (status.isNullOrBlank()) return
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
        Text(
            status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun OrderDetailScreen(app: CauveryApp, nav: NavHostController, id: Long) {
    val fallback = stringResource(R.string.error_generic)
    var state by remember { mutableStateOf<Load<OrderDetail>>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        state = Load.Loading
        state = try {
            val detail = app.network.api.order(id)
            // The detail endpoint has no line items; the order list carries them.
            val lines = runCatching { app.network.api.orders().firstOrNull { it.id == id }?.items }.getOrNull().orEmpty()
            Load.Done(detail.copy(items = lines.map { OrderDetailItem(it.productName, it.quantity, it.price, null) }))
        } catch (e: Exception) {
            Load.Failed(e.userMessage(fallback))
        }
    }
    Screen(stringResource(R.string.order_number, id), nav) { pad ->
        when (val s = state) {
            Load.Loading -> LoadingBox(Modifier.padding(pad))
            is Load.Failed -> ErrorBox(s.message, { reload++ }, Modifier.padding(pad))
            is Load.Done -> {
                val o = s.value
                Column(
                    Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(o.createdAt?.take(10).orEmpty(), Modifier.weight(1f))
                        StatusLabel(o.status)
                    }
                    SectionTitle(stringResource(R.string.items))
                    o.items.forEach { i ->
                        Row(Modifier.fillMaxWidth()) {
                            Text("${i.name.orEmpty()} × ${i.quantity}", Modifier.weight(1f))
                            Text(money(i.subtotal ?: ((i.price ?: 0.0) * i.quantity)))
                        }
                    }
                    HorizontalDivider()
                    o.subTotal?.let { TotalLine(stringResource(R.string.subtotal), money(it)) }
                    o.deliveryCharge?.let { TotalLine(stringResource(R.string.delivery), if (it == 0.0) stringResource(R.string.free) else money(it)) }
                    o.tax?.let { TotalLine(stringResource(R.string.gst_included), money(it), small = true) }
                    TotalLine(stringResource(R.string.total), money(o.totalAmount), bold = true)
                    o.paymentMethod?.let { TotalLine(stringResource(R.string.payment), it, small = true) }
                    o.shippingAddress?.let { a ->
                        SectionTitle(stringResource(R.string.delivery_address))
                        Text(a.name.orEmpty(), fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(a.address, a.city, a.state, a.pincode).filter { it.isNotBlank() }.distinct().joinToString(", "))
                    }
                    if (!o.trackingNumber.isNullOrBlank()) {
                        SectionTitle(stringResource(R.string.tracking))
                        Text(listOfNotNull(o.courier, o.trackingNumber).joinToString(" · "))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}
