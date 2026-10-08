package com.example.audiary.spotify

import com.example.audiary.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

data class SpotifyAuthState(
    val configured: Boolean, val connected: Boolean = false, val busy: Boolean = true,
    val error: String? = null, val sessionId: Long = 0
)

class SpotifyAuthManager(
    private val store: SessionStore,
    private val http: SpotifyHttp,
    private val scope: CoroutineScope,
    private val clientId: String = BuildConfig.SPOTIFY_CLIENT_ID,
    val redirect: String = BuildConfig.SPOTIFY_REDIRECT_URI,
    private val tokenEndpoint: String = "https://accounts.spotify.com/api/token",
    private val profileEndpoint: String = "https://api.spotify.com/v1/me"
) {
    private val configured = clientId.isNotBlank() && runCatching {
        val uri = java.net.URI(redirect)
        val scheme = uri.scheme?.lowercase()
        val validScheme = scheme == "https" || scheme == "http" || scheme == "com.example.audiary" || scheme?.contains(".") == true
        val notInvalid = uri.host != null && !uri.host.endsWith(".invalid")
        validScheme && notInvalid && uri.rawQuery == null && uri.fragment == null && uri.userInfo == null
    }.getOrDefault(false)
    private val _state = MutableStateFlow(SpotifyAuthState(configured))
    val state = _state.asStateFlow()
    private val mutex = Mutex()
    private var session = SpotifySession()
    private val ready = scope.async(Dispatchers.IO) {
        mutex.withLock {
            try {
                session = store.read()
                _state.value = SpotifyAuthState(configured, connected = session.tokens != null, busy = false)
            } catch (e: Exception) {
                AudiaryLog.w("SESSION_INIT_FAILED: clearing stored session", e)
                store.clear()
                _state.value = SpotifyAuthState(configured, busy = false, error = "Please reconnect Spotify on this device.")
            }
        }
    }

    suspend fun begin(): String {
        ready.await()
        return mutex.withLock {
            check(configured) { "Spotify isn't configured in this build yet." }
            val pending = PendingAuth(Pkce.random(), Pkce.random(), System.currentTimeMillis())
            session = session.copy(pending = pending)
            withContext(Dispatchers.IO) { store.write(session) }
            _state.update { it.copy(error = null) }
            AudiaryLog.i("AUTH_REQUEST_CREATED: clientId=${clientId.take(4)}***, redirectUri=$redirect, challenge_method=S256")
            "https://accounts.spotify.com/authorize".toHttpUrl().newBuilder()
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("redirect_uri", redirect)
                .addQueryParameter("scope", "user-library-read playlist-read-private playlist-read-collaborative")
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("code_challenge", Pkce.challenge(pending.verifier))
                .addQueryParameter("state", pending.state).build().toString()
        }
    }

    fun acceptRedirect(uri: String) {
        scope.launch {
            try {
                complete(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AudiaryLog.e("REDIRECT_PROCESSING_FAILED: unexpected error during complete()", e)
                fail("Authorization callback failed. ${e.message ?: "Please try again."}")
            }
        }
    }

    suspend fun complete(uri: String) {
        ready.await()
        val callback = CallbackUri.parse(uri)
        AudiaryLog.i("CALLBACK_RECEIVED: scheme=${callback.scheme}, host=${callback.host}, path=${callback.path}")

        if (!Pkce.isRedirect(uri, redirect)) {
            AudiaryLog.w("CALLBACK_REJECTED: uri does not match configured redirect. Received scheme=${callback.scheme}, host=${callback.host}, path=${callback.path}; Expected=$redirect")
            val expectedUri = runCatching { java.net.URI(redirect) }.getOrNull()
            if (expectedUri != null && callback.scheme.equals(expectedUri.scheme, ignoreCase = true)) {
                fail("Authorization callback failed. Redirect URI mismatch.")
            }
            return
        }

        mutex.withLock {
            val pending = session.pending
            val stateParams = callback.queryParams("state")
            val receivedState = callback.queryParam("state")
            val elapsedMs = if (pending != null) System.currentTimeMillis() - pending.createdAt else -1L

            if (pending == null) {
                AudiaryLog.e("STATE_VALIDATION_FAILED: pending authentication session is null")
                fail("Security validation failed. Sign-in session not found. Please start connecting again.")
                return
            }
            if (stateParams.size != 1) {
                AudiaryLog.e("STATE_VALIDATION_FAILED: state parameter count=${stateParams.size} (expected 1)")
                fail("Security validation failed. Invalid state parameter count.")
                return
            }
            if (!Pkce.matchesState(pending.state, receivedState)) {
                AudiaryLog.e("STATE_VALIDATION_FAILED: state mismatch")
                fail("Security validation failed. State mismatch. Please start connecting again.")
                return
            }
            if (elapsedMs !in 0..600000) {
                AudiaryLog.e("STATE_VALIDATION_FAILED: state expired (elapsed=${elapsedMs}ms > 600000ms)")
                fail("Security validation failed. This sign-in link has expired. Start connecting again.")
                return
            }

            AudiaryLog.i("STATE_VALIDATION_SUCCESS: state verified successfully")
            session = session.copy(pending = null)
            withContext(Dispatchers.IO) { store.write(session) } // Consume state before exchanging the one-time code.

            val errorParam = callback.queryParam("error")
            if (errorParam != null) {
                val errorDesc = callback.queryParam("error_description")
                AudiaryLog.w("CALLBACK_ERROR: error=$errorParam, description=$errorDesc")
                if (errorParam.equals("access_denied", ignoreCase = true)) {
                    _state.update { it.copy(error = "Spotify connection was cancelled. You can continue using Audiary.", busy = false) }
                } else {
                    fail("Authorization callback failed: ${errorParam.replace('_', ' ')}.")
                }
                return
            }

            val code = callback.queryParam("code")
            val codeParams = callback.queryParams("code")
            val hasCode = !code.isNullOrBlank() && codeParams.size == 1
            AudiaryLog.i("AUTHORIZATION_CODE_PRESENT: present=$hasCode")
            if (!hasCode) {
                AudiaryLog.e("AUTHORIZATION_CODE_PRESENT: missing or duplicate code parameter (count=${codeParams.size})")
                fail("Authorization callback failed. Spotify didn't return an authorization code.")
                return
            }

            _state.update { it.copy(busy = true, error = null) }
            AudiaryLog.i("TOKEN_EXCHANGE_STARTED: endpoint=$tokenEndpoint, clientId=${clientId.take(4)}***, redirectUri=$redirect")

            val tokens: Tokens = try {
                val body = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "authorization_code")
                    .add("code", code!!)
                    .add("redirect_uri", redirect)
                    .add("code_verifier", pending.verifier)
                    .build()
                val response = http.json(Request.Builder().url(tokenEndpoint).post(body).build())
                val accessToken = response.getString("access_token")
                val refreshToken = response.getString("refresh_token")
                val expiresIn = response.getLong("expires_in")
                AudiaryLog.i("TOKEN_EXCHANGE_SUCCESS: tokens received successfully (expires_in=${expiresIn}s)")
                Tokens(accessToken, refreshToken, System.currentTimeMillis() + expiresIn * 1000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SpotifyFailure) {
                AudiaryLog.e("TOKEN_EXCHANGE_FAILED: http_status=${e.status}, message=${e.message}")
                fail("Spotify rejected the token request. ${e.message}")
                return
            } catch (e: java.io.IOException) {
                AudiaryLog.e("TOKEN_EXCHANGE_FAILED: network error: ${e.message}")
                fail("Connection failed due to network issues. Check your connection and try again.")
                return
            } catch (e: Exception) {
                AudiaryLog.e("TOKEN_EXCHANGE_FAILED: unexpected error: ${e.message}", e)
                fail("Spotify rejected the token request: ${e.message ?: "Please try again."}")
                return
            }

            try {
                session = SpotifySession(tokens)
                withContext(Dispatchers.IO) { store.write(session) }
                AudiaryLog.i("TOKEN_STORAGE_SUCCESS: session and tokens persisted securely")
            } catch (e: Exception) {
                AudiaryLog.e("TOKEN_STORAGE_FAILED: could not write session to store", e)
                fail("Failed to securely save Spotify tokens on device.")
                return
            }

            // Verify profile request on first authenticated connection
            try {
                AudiaryLog.i("SPOTIFY_PROFILE_REQUEST_STARTED: checking profile at $profileEndpoint")
                val profileReq = Request.Builder()
                    .url(profileEndpoint)
                    .header("Authorization", "Bearer ${tokens.access}")
                    .build()
                val profileJson = http.json(profileReq)
                val accountId = profileJson.optString("id", "unknown")
                val product = profileJson.optString("product", "standard")
                AudiaryLog.i("SPOTIFY_PROFILE_REQUEST_SUCCESS: account=$accountId, product=$product")
            } catch (e: SpotifyFailure) {
                AudiaryLog.w("SPOTIFY_PROFILE_REQUEST_FAILED: status=${e.status}, message=${e.message}")
            } catch (e: Exception) {
                AudiaryLog.w("SPOTIFY_PROFILE_REQUEST_FAILED: error=${e.message}")
            }

            _state.update { it.copy(connected = true, busy = false, error = null, sessionId = it.sessionId + 1) }
        }
    }

    suspend fun accessToken(forceRefresh: Boolean = false): String {
        ready.await()
        return mutex.withLock {
            val current = session.tokens ?: throw SpotifyFailure(401, "Connect Spotify to load your saved music.")
            if (!forceRefresh && current.expiresAt > System.currentTimeMillis() + 60000) return@withLock current.access
            try {
                AudiaryLog.i("TOKEN_REFRESH_STARTED: refreshing access token")
                val body = FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", current.refresh)
                    .add("client_id", clientId).build()
                val response = http.json(Request.Builder().url(tokenEndpoint).post(body).build())
                val refreshed = Tokens(response.getString("access_token"), response.optString("refresh_token").ifBlank { current.refresh },
                    System.currentTimeMillis() + response.getLong("expires_in") * 1000)
                session = session.copy(tokens = refreshed)
                withContext(Dispatchers.IO) { store.write(session) }
                AudiaryLog.i("TOKEN_REFRESH_SUCCESS: refreshed token received and stored")
                refreshed.access
            } catch (e: SpotifyFailure) {
                AudiaryLog.e("TOKEN_REFRESH_FAILED: status=${e.status}, message=${e.message}")
                if (e.status == 400 || e.status == 401) {
                    session = SpotifySession()
                    withContext(Dispatchers.IO) { store.clear() }
                    _state.update { it.copy(connected = false, error = "Spotify access has expired or was revoked. Please reconnect.", sessionId = it.sessionId + 1) }
                }
                throw e
            }
        }
    }

    suspend fun disconnect() {
        ready.await()
        mutex.withLock {
            withContext(Dispatchers.IO) { store.clear() }
            session = SpotifySession()
            AudiaryLog.i("DISCONNECT: session and tokens cleared")
            _state.update { it.copy(connected = false, busy = false, error = null, sessionId = it.sessionId + 1) }
        }
    }

    fun fail(message: String) { _state.update { it.copy(busy = false, error = message) } }
}

fun Exception.userMessage(): String = when (this) {
    is SpotifyFailure -> message
    is java.io.IOException -> "Connection failed due to network issues. Check your connection and try again."
    else -> message ?: "Couldn't reach Spotify. Check your connection and try again."
}
