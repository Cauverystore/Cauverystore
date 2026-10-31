package com.cauverystore.android.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class SignedInUser(val email: String, val name: String?)

/**
 * Who is signed in. The short-lived access token is kept here; the long-lived refresh token
 * comes back from the server only as a cookie, so [cookieJar] keeps it across app restarts.
 * Both live in the app's private storage, which other apps can't read.
 */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val cookiePrefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)

    @Volatile
    var accessToken: String? = prefs.getString("accessToken", null)
        private set

    private val _user = MutableStateFlow(
        prefs.getString("email", null)?.let { SignedInUser(it, prefs.getString("name", null)) }
    )
    val user: StateFlow<SignedInUser?> = _user.asStateFlow()

    val isSignedIn: Boolean get() = _user.value != null

    fun signIn(auth: AuthResponse) {
        val token = auth.accessToken ?: return
        accessToken = token
        val email = auth.email ?: ""
        prefs.edit().putString("accessToken", token).putString("email", email)
            .putString("name", auth.username).apply()
        _user.value = SignedInUser(email, auth.username)
    }

    fun updateToken(token: String) {
        accessToken = token
        prefs.edit().putString("accessToken", token).apply()
    }

    fun updateName(name: String?) {
        val u = _user.value ?: return
        prefs.edit().putString("name", name).apply()
        _user.value = u.copy(name = name)
    }

    fun signOut() {
        accessToken = null
        prefs.edit().clear().apply()
        cookiePrefs.edit().clear().apply()
        _user.value = null
    }

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val edit = cookiePrefs.edit()
            for (c in cookies) {
                val key = "${c.domain}|${c.path}|${c.name}"
                if (c.expiresAt < System.currentTimeMillis() || c.value.isEmpty()) edit.remove(key)
                else edit.putString(key, "${url}\u0000$c")
            }
            edit.apply()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return cookiePrefs.all.values.mapNotNull { v ->
                val parts = (v as? String)?.split('\u0000', limit = 2) ?: return@mapNotNull null
                val origin = parts[0].toHttpUrlOrNull() ?: return@mapNotNull null
                Cookie.parse(origin, parts[1])
            }.filter { it.expiresAt > now && it.matches(url) }
        }
    }

    val hasRefreshCookie: Boolean get() = cookiePrefs.all.isNotEmpty()
}
