package com.cauverystore.android.ui

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import com.cauverystore.android.BuildConfig
import com.cauverystore.android.CauveryApp
import com.cauverystore.android.R
import com.cauverystore.android.data.LoginRequest
import com.cauverystore.android.data.MfaRequest
import com.cauverystore.android.data.RegisterRequest
import com.cauverystore.android.data.userMessage
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(app: CauveryApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val fallback = stringResource(R.string.login_failed)
    val notShopper = stringResource(R.string.not_shopper_account)
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var mfaEmail by rememberSaveable { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun done() { nav.popBackStack() }

    fun submit() {
        error = null
        scope.launch {
            busy = true
            try {
                val pending = mfaEmail
                val res = if (pending != null) app.network.api.loginMfa(MfaRequest(pending, otp.trim()))
                else app.network.api.login(LoginRequest(email.trim(), password))
                when {
                    res.mfaRequired == true -> mfaEmail = res.email ?: email.trim()
                    res.accessToken != null && !res.isCustomer -> {
                        runCatching { app.network.api.logout() }
                        app.session.signOut() // drops the refresh cookie too
                        mfaEmail = null
                        error = notShopper
                    }
                    res.accessToken != null -> { app.session.signIn(res); done() }
                    else -> error = res.message ?: fallback
                }
            } catch (e: Exception) {
                error = e.userMessage(fallback)
            } finally {
                busy = false
            }
        }
    }

    Screen(stringResource(R.string.sign_in), nav) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.welcome_back), style = MaterialTheme.typography.headlineSmall)
            if (mfaEmail == null) {
                OutlinedTextField(
                    email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(stringResource(R.string.mfa_prompt))
                OutlinedTextField(
                    otp, { otp = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.verification_code)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = ::submit,
                enabled = !busy && (if (mfaEmail == null) email.isNotBlank() && password.isNotBlank() else otp.length >= 6),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sign_in))
            }
            val context = LocalContext.current
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.WEBSITE_URL + "/forgot-password")))
            }) { Text(stringResource(R.string.forgot_password)) }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.new_here))
                TextButton(onClick = { nav.navigate(Routes.REGISTER) { popUpTo(Routes.LOGIN) { inclusive = true } } }) {
                    Text(stringResource(R.string.create_account))
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(app: CauveryApp, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val fallback = stringResource(R.string.register_failed)
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pwTooShort = stringResource(R.string.password_too_short)
    val badPhone = stringResource(R.string.phone_invalid)

    fun submit() {
        error = null
        if (password.length < 8) { error = pwTooShort; return }
        if (phone.isNotBlank() && phone.filter(Char::isDigit).length != 10) { error = badPhone; return }
        scope.launch {
            busy = true
            try {
                app.network.api.register(RegisterRequest(name.trim(), email.trim(), password, phone.trim().ifBlank { null }))
                // Sign straight in, the same as the website does after registering.
                val res = app.network.api.login(LoginRequest(email.trim(), password))
                if (res.accessToken != null) {
                    app.session.signIn(res)
                    nav.popBackStack()
                } else {
                    nav.navigate(Routes.LOGIN) { popUpTo(Routes.REGISTER) { inclusive = true } }
                }
            } catch (e: Exception) {
                error = e.userMessage(fallback)
            } finally {
                busy = false
            }
        }
    }

    Screen(stringResource(R.string.create_account), nav) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.full_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                phone, { phone = it.filter(Char::isDigit).take(10) }, label = { Text(stringResource(R.string.phone_optional)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                supportingText = { Text(stringResource(R.string.password_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = ::submit, enabled = !busy && name.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.create_account))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.have_account))
                TextButton(onClick = { nav.navigate(Routes.LOGIN) { popUpTo(Routes.REGISTER) { inclusive = true } } }) {
                    Text(stringResource(R.string.sign_in))
                }
            }
        }
    }
}

/** Language codes the app ships, in the order the website lists them. */
val LANGUAGES = listOf(
    "en" to "English",
    "ta" to "தமிழ்",
    "te" to "తెలుగు",
    "hi" to "हिन्दी",
    "kn" to "ಕನ್ನಡ",
    "ml" to "മലയാളം",
)

@Composable
fun AccountScreen(app: CauveryApp, nav: NavHostController) {
    val user by app.session.user.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showLanguages by remember { mutableStateOf(false) }

    Screen(stringResource(R.string.tab_account)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            val u = user
            if (u == null) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.sign_in_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.sign_in_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { nav.navigate(Routes.LOGIN) }) { Text(stringResource(R.string.sign_in)) }
                        SecondaryButton(stringResource(R.string.create_account), { nav.navigate(Routes.REGISTER) })
                    }
                }
            } else {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.hello_name, u.name?.takeIf { it.isNotBlank() } ?: u.email),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                    )
                    Text(u.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                Row_(Icons.Outlined.Receipt, stringResource(R.string.my_orders)) { nav.navigate(Routes.ORDERS) }
            }
            Row_(Icons.Outlined.Language, stringResource(R.string.language), currentLanguageName()) { showLanguages = true }
            Row_(Icons.Outlined.Info, stringResource(R.string.about_title), stringResource(R.string.about_body), onClick = null)
            Row_(Icons.Outlined.Public, stringResource(R.string.visit_website)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.WEBSITE_URL)))
            }
            if (u != null) {
                Row_(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.sign_out)) {
                    scope.launch {
                        runCatching { app.network.api.logout() }
                        app.session.signOut()
                    }
                }
            }
            Text(
                stringResource(R.string.version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
    }

    if (showLanguages) {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2).ifBlank { null }
        AlertDialog(
            onDismissRequest = { showLanguages = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    LanguageOption(null, stringResource(R.string.system_language), current == null) { pick(null); showLanguages = false }
                    LANGUAGES.forEach { (code, label) ->
                        LanguageOption(code, label, current == code) { pick(code); showLanguages = false }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguages = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

private fun pick(code: String?) {
    AppCompatDelegate.setApplicationLocales(
        if (code == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code)
    )
}

@Composable
private fun currentLanguageName(): String {
    val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags().take(2)
    return LANGUAGES.firstOrNull { it.first == tag }?.second ?: stringResource(R.string.system_language)
}

@Composable
private fun LanguageOption(@Suppress("UNUSED_PARAMETER") code: String?, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun Row_(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = if (onClick != null) { { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) } } else null,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
