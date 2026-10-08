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

    // Priority 7: Spotify February 2026 Playlist Schema & Dev Mode Restriction Tests

    @Test fun playlistParsingSupportsItemsTotalField() {
        val json = JSONObject("""{
            "id": "pl_items_total",
            "name": "Items Total Playlist",
            "description": "2026 schema playlist",
            "owner": {"id": "user123", "display_name": "Test User"},
            "items": {"total": 42},
            "external_urls": {"spotify": "https://open.spotify.com/playlist/pl_items_total"}
        }""")
        val playlist = SpotifyApi.parsePlaylist(json, currentUserId = "user123")
        assertNotNull(playlist)
        assertEquals("pl_items_total", playlist!!.id)
        assertEquals(42, playlist.totalTracks)
        assertEquals("42 tracks", playlist.formattedTrackCount())
        assertTrue(playlist.isOwnerOrCollaborator)
        assertTrue(playlist.isAccessible)
    }

    @Test fun playlistParsingSupportsLegacyTracksTotalField() {
        val json = JSONObject("""{
            "id": "pl_tracks_total",
            "name": "Legacy Tracks Playlist",
            "owner": {"id": "user123", "display_name": "Test User"},
            "tracks": {"total": 15},
            "external_urls": {"spotify": "https://open.spotify.com/playlist/pl_tracks_total"}
        }""")
        val playlist = SpotifyApi.parsePlaylist(json, currentUserId = "user123")
        assertNotNull(playlist)
        assertEquals(15, playlist!!.totalTracks)
        assertEquals("15 tracks", playlist.formattedTrackCount())
    }

    @Test fun playlistParsingPrioritizesItemsTotalWhenBothFieldsPresent() {
        val json = JSONObject("""{
            "id": "pl_both_fields",
            "name": "Dual Fields Playlist",
            "owner": {"id": "user123", "display_name": "Test User"},
            "items": {"total": 88},
            "tracks": {"total": 12},
            "external_urls": {"spotify": "https://open.spotify.com/playlist/pl_both_fields"}
        }""")
        val playlist = SpotifyApi.parsePlaylist(json, currentUserId = "user123")
        assertNotNull(playlist)
        // items.total (88) takes precedence over tracks.total (12)
        assertEquals(88, playlist!!.totalTracks)
        assertEquals("88 tracks", playlist.formattedTrackCount())
    }

    @Test fun playlistParsingReturnsNullTotalTracksWhenNeitherFieldPresent() {
        val json = JSONObject("""{
            "id": "pl_neither_field",
            "name": "Missing Count Playlist",
            "owner": {"id": "user123", "display_name": "Test User"},
            "external_urls": {"spotify": "https://open.spotify.com/playlist/pl_neither_field"}
        }""")
        val playlist = SpotifyApi.parsePlaylist(json, currentUserId = "user123")
        assertNotNull(playlist)
        assertNull(playlist!!.totalTracks)
        assertNotEquals(0, playlist.totalTracks)
        assertEquals("Track count unavailable", playlist.formattedTrackCount())
    }

    @Test fun playlistParsingRepresentsGenuinelyZeroTracks() {
        val json = JSONObject("""{
            "id": "pl_zero_tracks",
            "name": "Empty Playlist",
            "owner": {"id": "user123", "display_name": "Test User"},
            "items": {"total": 0},
            "external_urls": {"spotify": "https://open.spotify.com/playlist/pl_zero_tracks"}
        }""")
        val playlist = SpotifyApi.parsePlaylist(json, currentUserId = "user123")
        assertNotNull(playlist)
        assertEquals(0, playlist!!.totalTracks)
        assertEquals("0 tracks", playlist.formattedTrackCount())
    }

    @Test fun playlistTracksParsesNewItemObjectField() = runBlocking {
        val store = MemoryStore()
        val auth = SpotifyAuthManager(
            store, SpotifyHttp(), scope, "client-id",
            "https://callback.test/oauth/callback",
            server.url("/token").toString(),
            server.url("/me").toString()
        )
        auth.begin()
        val pendingState = store.session.pending!!.state
        server.enqueue(MockResponse().setBody("""{"access_token":"token-6","refresh_token":"ref-6","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"id":"user_test"}"""))
        auth.complete("https://callback.test/oauth/callback?state=$pendingState&code=code-6")

        val api = SpotifyApi(auth, SpotifyHttp(), baseUrl = server.url("").toString().removeSuffix("/"))

        server.enqueue(MockResponse().setBody("""{
            "items": [
                {
                    "added_at": "2026-03-01T12:00:00Z",
                    "item": {
                        "id": "trackitem1",
                        "name": "Item Schema Song",
                        "artists": [{"name": "Modern Artist"}],
                        "album": {
                            "id": "album1",
                            "name": "Modern Album",
                            "release_date": "2026-01-01",
                            "images": [{"url": "https://i.scdn.co/image/modern"}]
                        },
                        "duration_ms": 205000,
                        "type": "track"
                    }
                }
            ],
            "total": 1,
            "next": null
        }"""))

        val page = api.playlistTracks("plitemtest")
        assertEquals(1, page.items.size)
        val song = page.items[0]
        assertEquals("spotify:track:trackitem1", song.id)
        assertEquals("Item Schema Song", song.title)
        assertEquals("Modern Artist", song.artist)
        assertEquals("Modern Album", song.album)
        assertEquals("https://i.scdn.co/image/modern", song.artUrl)
        assertEquals(205000L, song.durationMs)
    }

    @Test fun playlistTracksParsesLegacyTrackObjectField() = runBlocking {
        val store = MemoryStore()
        val auth = SpotifyAuthManager(
            store, SpotifyHttp(), scope, "client-id",
            "https://callback.test/oauth/callback",
            server.url("/token").toString(),
            server.url("/me").toString()
        )
        auth.begin()
        val pendingState = store.session.pending!!.state
        server.enqueue(MockResponse().setBody("""{"access_token":"token-7","refresh_token":"ref-7","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"id":"user_test"}"""))
        auth.complete("https://callback.test/oauth/callback?state=$pendingState&code=code-7")

        val api = SpotifyApi(auth, SpotifyHttp(), baseUrl = server.url("").toString().removeSuffix("/"))

        server.enqueue(MockResponse().setBody("""{
            "items": [
                {
                    "added_at": "2025-06-01T12:00:00Z",
                    "track": {
                        "id": "tracklegacy1",
                        "name": "Legacy Schema Song",
                        "artists": [{"name": "Classic Artist"}],
                        "album": {
                            "id": "albumlegacy",
                            "name": "Classic Album",
                            "release_date": "2025-01-01",
                            "images": [{"url": "https://i.scdn.co/image/legacy"}]
                        },
                        "duration_ms": 195000,
                        "type": "track"
                    }
                }
            ],
            "total": 1,
            "next": null
        }"""))

        val page = api.playlistTracks("pllegacytest")
        assertEquals(1, page.items.size)
        val song = page.items[0]
        assertEquals("spotify:track:tracklegacy1", song.id)
        assertEquals("Legacy Schema Song", song.title)
        assertEquals("Classic Artist", song.artist)
        assertEquals("Classic Album", song.album)
    }

    @Test fun playlistTracksPreservesHttp403InaccessibleError() = runBlocking {
        val store = MemoryStore()
        val auth = SpotifyAuthManager(
            store, SpotifyHttp(), scope, "client-id",
            "https://callback.test/oauth/callback",
            server.url("/token").toString(),
            server.url("/me").toString()
        )
        auth.begin()
        val pendingState = store.session.pending!!.state
        server.enqueue(MockResponse().setBody("""{"access_token":"token-8","refresh_token":"ref-8","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"id":"user_test"}"""))
        auth.complete("https://callback.test/oauth/callback?state=$pendingState&code=code-8")

        val api = SpotifyApi(auth, SpotifyHttp(), baseUrl = server.url("").toString().removeSuffix("/"))

        server.enqueue(MockResponse().setResponseCode(403).setBody("""{
            "error": {
                "status": 403,
                "message": "Spotify denied access. Check the app owner's Premium subscription and the account's access in the developer dashboard."
            }
        }"""))

        try {
            api.playlistTracks("inaccessibleplaylist")
            fail("Expected HTTP 403 SpotifyFailure for inaccessible playlist")
        } catch (e: SpotifyFailure) {
            assertEquals(403, e.status)
            assertTrue(e.message.contains("Spotify denied access"))
        }
    }

    @Test fun playlistItemParsingFiltersNullLocalAndEpisodeItems() {
        // Episode item should be filtered out
        val episodeJson = JSONObject("""{
            "item": {
                "id": "episode_123",
                "name": "Podcast Episode",
                "type": "episode"
            }
        }""")
        assertNull(SpotifyApi.parsePlaylistItem(episodeJson))

        // Local file item should be filtered out
        val localJson = JSONObject("""{
            "is_local": true,
            "item": {
                "id": "local_track_1",
                "name": "Local MP3",
                "type": "track"
            }
        }""")
        assertNull(SpotifyApi.parsePlaylistItem(localJson))

        // Null item should be filtered out
        val nullItemJson = JSONObject("""{"item": null}""")
        assertNull(SpotifyApi.parsePlaylistItem(nullItemJson))

        // Empty JSON should be filtered out
        val emptyJson = JSONObject("""{}""")
        assertNull(SpotifyApi.parsePlaylistItem(emptyJson))

        // Invalid track ID should be filtered out
        val invalidIdJson = JSONObject("""{
            "item": {
                "id": "../malformed",
                "name": "Bad ID Track",
                "type": "track"
            }
        }""")
        assertNull(SpotifyApi.parsePlaylistItem(invalidIdJson))
    }

    @Test fun playlistOwnershipFlagsNonOwnedAndNonCollaborativeAsRestricted() {
        val json = JSONObject("""{
            "id": "pl_followed",
            "name": "Followed Playlist",
            "owner": {"id": "stranger_account", "display_name": "Stranger"},
            "collaborative": false,
            "items": {"total": 30},
            "external_urls": {"spotify": "https://open.spotify.com/playlist/pl_followed"}
        }""")
        val playlist = SpotifyApi.parsePlaylist(json, currentUserId = "my_account")
        assertNotNull(playlist)
        assertEquals("stranger_account", playlist!!.ownerId)
        assertFalse(playlist.isOwnerOrCollaborator)
        assertEquals(30, playlist.totalTracks)
    }
}
