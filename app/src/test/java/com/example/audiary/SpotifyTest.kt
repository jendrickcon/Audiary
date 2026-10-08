package com.example.audiary

import com.example.audiary.spotify.*
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class SpotifyTest {
    private class MemoryStore : SessionStore {
        var session = SpotifySession()
        override fun read() = session
        override fun write(session: SpotifySession) { this.session = session }
        override fun clear() { session = SpotifySession() }
    }
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    @Before fun before() { server = MockWebServer(); server.start(); scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    @After fun after() { scope.cancel(); server.shutdown() }

    @Test fun pkceMatchesRfc7636VectorAndRejectsRedirectSpoofing() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertTrue(Pkce.random().length in 43..128)
        assertFalse(Pkce.matchesState("expected", "attacker"))
        assertFalse(Pkce.isRedirect("https://callback.test.attacker.com/oauth/callback", "https://callback.test/oauth/callback"))
        assertFalse(Pkce.isRedirect("https://callback.test/other", "https://callback.test/oauth/callback"))
        assertFalse(Pkce.isRedirect("http://callback.test/oauth/callback", "https://callback.test/oauth/callback"))
        assertTrue(Pkce.isRedirect("com.example.audiary://callback?code=abc&state=123", "com.example.audiary://callback"))
        assertFalse(Pkce.isRedirect("com.example.other://callback?code=abc", "com.example.audiary://callback"))
        assertFalse(Pkce.isRedirect("com.example.audiary://other", "com.example.audiary://callback"))
    }

    @Test fun signInValidatesStateExchangesVerifierRefreshesAndDisconnects() = runBlocking {
        val store = MemoryStore()
        val auth = SpotifyAuthManager(store, SpotifyHttp(), scope, "client-id", "https://callback.test/oauth/callback", server.url("/token").toString())
        val authorization = auth.begin().toHttpUrl()
        assertEquals("S256", authorization.queryParameter("code_challenge_method"))
        assertEquals(Pkce.challenge(store.session.pending!!.verifier), authorization.queryParameter("code_challenge"))
        val verifier = store.session.pending!!.verifier
        auth.complete("https://callback.test/oauth/callback?state=wrong&code=bad")
        assertEquals(0, server.requestCount)
        assertFalse(auth.state.value.connected)
        server.enqueue(MockResponse().setBody("""{"access_token":"access-one","refresh_token":"refresh-one","expires_in":3600}"""))
        auth.complete("https://callback.test/oauth/callback?state=${authorization.queryParameter("state")}&code=one-time-code")
        assertTrue(auth.state.value.connected)
        assertNull(store.session.pending)
        val exchange = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(exchange.body.readUtf8().contains("code_verifier=$verifier"))
        assertEquals("access-one", auth.accessToken())
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setBody("""{"access_token":"access-two","expires_in":3600}"""))
        assertEquals("access-two", auth.accessToken(forceRefresh = true))
        assertEquals("refresh-one", store.session.tokens!!.refresh)
        server.takeRequest(2, TimeUnit.SECONDS)
        auth.disconnect()
        assertNull(store.session.tokens)
        assertFalse(auth.state.value.connected)
    }

    @Test fun revokedRefreshRequiresReconnection() = runBlocking {
        val store = MemoryStore().apply { session = SpotifySession(Tokens("expired", "revoked", 0)) }
        val auth = SpotifyAuthManager(store, SpotifyHttp(), scope, "id", "https://callback.test/oauth/callback", server.url("/token").toString())
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        try { auth.accessToken(); fail("Expected revoked access") } catch (e: SpotifyFailure) { assertEquals(400, e.status) }
        assertFalse(auth.state.value.connected)
        assertNull(store.session.tokens)
    }

    @Test fun rateLimitHonorsRetryAfterWithoutSendingAnotherRequest() = runBlocking {
        val http = SpotifyHttp()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120")
            .setBody("""{"error":{"status":429,"reason":"QUOTA_EXCEEDED"}}"""))
        val request = Request.Builder().url(server.url("/me/tracks")).build()
        try { http.json(request); fail("Expected quota failure") } catch (e: SpotifyFailure) {
            assertEquals(429, e.status); assertTrue(e.message.contains("quota")); assertNotNull(e.retryAt)
        }
        try { http.json(request); fail("Expected backoff") } catch (e: SpotifyFailure) { assertEquals(429, e.status) }
        assertEquals(1, server.requestCount)
    }

    @Test fun trackMappingUsesProviderIdentityAndToleratesMissingFields() {
        val song = SpotifyApi.parseTrack(JSONObject("""{"id":"abc123","name":"Same title","artists":[{"name":"Artist"}],"album":{"id":"album1","name":"Album","images":[{"width":300,"url":"https://i.scdn.co/image/test"}]}}"""))!!
        assertEquals("spotify:track:abc123", song.id)
        assertEquals("https://open.spotify.com/track/abc123", song.externalUrl)
        assertEquals("https://i.scdn.co/image/test", song.artUrl)
        assertEquals(0L, song.durationMs)
        assertNull(song.addedAt)
        assertNull(SpotifyApi.parseTrack(JSONObject("""{"is_local":true,"name":"Local file"}""")))
        assertNull(SpotifyApi.parseTrack(JSONObject("""{"id":null}""")))
        assertNull(SpotifyApi.parseTrack(JSONObject("""{"id":"../bad"}""")))
    }

    @Test fun signInWithCustomSchemeRedirectSucceeds() = runBlocking {
        val store = MemoryStore()
        val auth = SpotifyAuthManager(
            store, SpotifyHttp(), scope, "client-id",
            "com.example.audiary://callback",
            server.url("/token").toString(),
            server.url("/me").toString()
        )
        auth.begin()
        val pendingState = store.session.pending!!.state
        val verifier = store.session.pending!!.verifier

        server.enqueue(MockResponse().setBody("""{"access_token":"custom-scheme-access","refresh_token":"custom-scheme-refresh","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"id":"test_user_spotify","product":"premium"}"""))

        // Simulate returning to app with custom scheme URI
        auth.complete("com.example.audiary://callback?state=$pendingState&code=custom-auth-code")

        assertTrue(auth.state.value.connected)
        assertNull(auth.state.value.error)
        assertNull(store.session.pending)
        assertNotNull(store.session.tokens)
        assertEquals("custom-scheme-access", store.session.tokens?.access)

        val tokenExchangeRequest = server.takeRequest(2, TimeUnit.SECONDS)!!
        val exchangeBody = tokenExchangeRequest.body.readUtf8()
        assertTrue(exchangeBody.contains("grant_type=authorization_code"))
        assertTrue(exchangeBody.contains("code=custom-auth-code"))
        assertTrue(exchangeBody.contains("code_verifier=$verifier"))
        assertTrue(exchangeBody.contains("redirect_uri=com.example.audiary%3A%2F%2Fcallback"))
    }

    @Test fun cancelledCallbackSetsAccurateError() = runBlocking {
        val store = MemoryStore()
        val auth = SpotifyAuthManager(store, SpotifyHttp(), scope, "client-id", "com.example.audiary://callback", server.url("/token").toString())
        auth.begin()
        val state = store.session.pending!!.state
        auth.complete("com.example.audiary://callback?error=access_denied&state=$state")
        assertFalse(auth.state.value.connected)
        assertEquals("Spotify connection was cancelled. You can continue using Audiary.", auth.state.value.error)
    }

    @Test fun stateMismatchSetsSecurityValidationError() = runBlocking {
        val store = MemoryStore()
        val auth = SpotifyAuthManager(store, SpotifyHttp(), scope, "client-id", "com.example.audiary://callback", server.url("/token").toString())
        auth.begin()
        auth.complete("com.example.audiary://callback?state=wrong-state&code=valid-code")
        assertFalse(auth.state.value.connected)
        assertTrue(auth.state.value.error?.contains("Security validation failed") == true)
    }

    @Test fun missingCodeSetsAccurateError() = runBlocking {
        val store = MemoryStore()
        val auth = SpotifyAuthManager(store, SpotifyHttp(), scope, "client-id", "com.example.audiary://callback", server.url("/token").toString())
        auth.begin()
        val state = store.session.pending!!.state
        auth.complete("com.example.audiary://callback?state=$state")
        assertFalse(auth.state.value.connected)
        assertTrue(auth.state.value.error?.contains("Spotify didn't return an authorization code") == true)
    }

    @Test fun callbackUriParsesSchemeAgnosticParametersCorrectly() {
        val uri = CallbackUri.parse("com.example.audiary://callback?code=abc%20123&state=xyz&multi=1&multi=2")
        assertEquals("com.example.audiary", uri.scheme)
        assertEquals("callback", uri.host)
        assertEquals("abc 123", uri.queryParam("code"))
        assertEquals("xyz", uri.queryParam("state"))
        assertEquals(listOf("1", "2"), uri.queryParams("multi"))
        assertNull(uri.queryParam("nonexistent"))
    }
}
