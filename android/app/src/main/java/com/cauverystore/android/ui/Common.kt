package com.cauverystore.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.cauverystore.android.R
import com.cauverystore.android.data.NETWORK_ERROR
import com.cauverystore.android.data.Product
import com.cauverystore.android.data.imageUrl
import java.text.NumberFormat
import java.util.Locale

private val rupees: NumberFormat = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 0
}

fun money(v: Double?): String = rupees.format(v ?: 0.0)

/** UI state for anything loaded from the network. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Done<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

@Composable
fun errorText(message: String): String =
    if (message == NETWORK_ERROR) stringResource(R.string.error_network) else message

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.CloudOff,
        title = stringResource(R.string.error_title),
        body = errorText(message),
        action = stringResource(R.string.retry),
        onAction = onRetry,
        modifier = modifier,
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
    val full = imageUrl(url)
    if (full == null) {
        Box(modifier) { placeholder() }
    } else {
        SubcomposeAsyncImage(
            model = full, contentDescription = null, contentScale = contentScale, modifier = modifier,
            loading = { placeholder() }, error = { placeholder() },
        )
    }
}

@Composable
fun PriceRow(price: Double, mrp: Double?, big: Boolean = false) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            money(price),
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (mrp != null && mrp > price) {
            Spacer(Modifier.padding(start = 6.dp))
            Text(
                money(mrp), style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(start = 6.dp))
            val off = (((mrp - price) / mrp) * 100).toInt()
            Text(stringResource(R.string.percent_off, off), style = MaterialTheme.typography.bodySmall, color = Saving)
        }
    }
}

@Composable
fun ProductCard(product: Product, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        RemoteImage(product.thumbnail, Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(10.dp)) {
            if (!product.brand.isNullOrBlank()) {
                Text(
                    product.brand, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            Text(product.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            PriceRow(product.sellingPrice, product.price)
            if ((product.stock ?: 1) <= 0) {
                Text(stringResource(R.string.out_of_stock), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun SignInPrompt(message: String, onSignIn: () -> Unit, icon: ImageVector, modifier: Modifier = Modifier) {
    EmptyState(
        icon = icon, title = message, body = stringResource(R.string.sign_in_body),
        action = stringResource(R.string.sign_in), onAction = onSignIn, modifier = modifier,
    )
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}
