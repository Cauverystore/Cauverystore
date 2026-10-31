package com.cauverystore.android.data

import com.cauverystore.android.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

class Network(private val session: Session) {

    /** Shared by the API and by image loading. */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(session.cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = session.accessToken
            val req = chain.request()
            val withAuth = if (token != null && req.url.host == apiHost && req.header("Authorization") == null) {
                req.newBuilder().header("Authorization", "Bearer $token").build()
            } else req
            chain.proceed(withAuth)
        }
        .authenticator(RefreshAuthenticator())
        .build()

    val api: Api = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(Api::class.java)

    private val apiHost = BuildConfig.API_BASE_URL.removePrefix("https://").substringBefore('/')

    /**
     * When the access token has expired, swap the refresh cookie for a new one and retry once,
     * the same way the website does. If that fails, the user is signed out.
     */
    private inner class RefreshAuthenticator : Authenticator {
        private val lock = Any()

        override fun authenticate(route: Route?, response: Response): Request? {
            val path = response.request.url.encodedPath
            if (path.startsWith("/api/auth/")) return null
            if (response.priorResponse != null) return null
            if (!session.isSignedIn) return null

            synchronized(lock) {
                val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
                val current = session.accessToken
                // Another request already refreshed while this one waited.
                if (current != null && current != sent) {
                    return response.request.newBuilder().header("Authorization", "Bearer $current").build()
                }
                val fresh = refresh()
                if (fresh == null) {
                    session.signOut()
                    return null
                }
                session.updateToken(fresh)
                return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
            }
        }

        private fun refresh(): String? {
            val client = OkHttpClient.Builder().cookieJar(session.cookieJar)
                .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
            val req = Request.Builder()
                .url(BuildConfig.API_BASE_URL + "api/auth/refresh")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            return try {
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return null
                    val body = res.body?.string() ?: return null
                    json.decodeFromString(AuthResponse.serializer(), body).accessToken
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Absolute URL for an image path the backend may send as relative. */
fun imageUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http")) return path
    return BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
}

/** A short message to show the user for a failed call. */
fun Throwable.userMessage(fallback: String): String = when (this) {
    is HttpException -> {
        val body = try { response()?.errorBody()?.string() } catch (_: Exception) { null }
        val fromServer = try {
            body?.let { json.parseToJsonElement(it).jsonObject }?.let { o ->
                (o["message"] ?: o["error"])?.jsonPrimitive?.content
            }
        } catch (_: Exception) { null }
        fromServer?.takeIf { it.isNotBlank() } ?: fallback
    }
    is IOException -> NETWORK_ERROR
    else -> fallback
}

/** Marker the UI swaps for the localised "no internet" text. */
const val NETWORK_ERROR = "__network__"

fun Throwable.isUnauthorized(): Boolean = this is HttpException && (code() == 401 || code() == 403)
