package com.example.audiary.spotify

import com.example.audiary.model.*
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class MusicAlbum(
    val id: String, val title: String, val artist: String, val artUrl: String?,
    val releaseDate: String, val externalUrl: String, val totalTracks: Int
) {
    fun artworkSong() = Song(
        "spotify:album:$id", title, artist, title, 0, releaseDate, 0xFF252925, artUrl,
        MusicSource.Spotify, externalUrl = externalUrl, albumId = "spotify:album:$id"
    )
}

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val artUrl: String?,
    val ownerName: String,
    val totalTracks: Int,
    val externalUrl: String,
    val isOwnerOrCollaborator: Boolean = true
) {
    fun artworkSong() = Song(
        "spotify:playlist:$id", name, ownerName, name, 0, "", 0xFF252925, artUrl,
        MusicSource.Spotify, externalUrl = externalUrl, albumId = "spotify:playlist:$id"
    )
}

data class SpotifyPage<T>(val items: List<T>, val next: String?, val total: Int)

class SpotifyApi(private val auth: SpotifyAuthManager, private val http: SpotifyHttp) {
    private suspend fun get(url: String): JSONObject {
        val parsed = java.net.URI(url)
        require(parsed.scheme == "https" && parsed.host == "api.spotify.com" && parsed.port == -1 &&
            parsed.userInfo == null && parsed.path.startsWith("/v1/")) { "Untrusted Spotify pagination URL" }
        val session = auth.state.value.sessionId
        var token = auth.accessToken()
        val data = try { http.json(Request.Builder().url(url).header("Authorization", "Bearer $token").build()) }
        catch (e: SpotifyFailure) {
            if (e.status != 401) throw e
            token = auth.accessToken(forceRefresh = true)
            http.json(Request.Builder().url(url).header("Authorization", "Bearer $token").build())
        }
        check(auth.state.value.connected && auth.state.value.sessionId == session) { "Spotify account changed." }
        return data
    }

    suspend fun profile(): JSONObject {
        AudiaryLog.i("SPOTIFY_PROFILE_REQUEST_STARTED: fetching /v1/me")
        return try {
            val json = get("https://api.spotify.com/v1/me")
            val id = json.optString("id", "unknown")
            val product = json.optString("product", "standard")
            AudiaryLog.i("SPOTIFY_PROFILE_REQUEST_SUCCESS: account=$id, product=$product")
            json
        } catch (e: SpotifyFailure) {
            AudiaryLog.e("SPOTIFY_PROFILE_REQUEST_FAILED: status=${e.status}, message=${e.message}")
            throw e
        } catch (e: Exception) {
            AudiaryLog.e("SPOTIFY_PROFILE_REQUEST_FAILED: error=${e.message}")
            throw e
        }
    }

    suspend fun tracks(next: String? = null, limit: Int = 40): SpotifyPage<Song> {
        val url = next ?: "https://api.spotify.com/v1/me/tracks?limit=$limit"
        AudiaryLog.i("LIBRARY_REQUEST_STARTED: tracks from $url")
        return try {
            val json = get(url)
            val items = json.optJSONArray("items").objects().mapNotNull { saved ->
                saved.optJSONObject("track")?.let { track -> parseTrack(track, addedAt = saved.stringOrNull("added_at")) }
            }
            val total = json.optInt("total")
            AudiaryLog.i("LIBRARY_REQUEST_SUCCESS: tracks count=${items.size}, total=$total")
            SpotifyPage(items, json.stringOrNull("next"), total)
        } catch (e: SpotifyFailure) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: status=${e.status}, message=${e.message}")
            throw e
        } catch (e: Exception) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: error=${e.message}")
            throw e
        }
    }

    suspend fun albums(next: String? = null): SpotifyPage<MusicAlbum> {
        val url = next ?: "https://api.spotify.com/v1/me/albums?limit=20"
        AudiaryLog.i("LIBRARY_REQUEST_STARTED: albums from $url")
        return try {
            val json = get(url)
            val albums = json.optJSONArray("items").objects().mapNotNull { saved ->
                saved.optJSONObject("album")?.let { parseAlbum(it) }
            }
            val total = json.optInt("total")
            AudiaryLog.i("LIBRARY_REQUEST_SUCCESS: albums count=${albums.size}, total=$total")
            SpotifyPage(albums, json.stringOrNull("next"), total)
        } catch (e: SpotifyFailure) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: status=${e.status}, message=${e.message}")
            throw e
        } catch (e: Exception) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: error=${e.message}")
            throw e
        }
    }

    suspend fun albumTracks(album: MusicAlbum, next: String? = null): SpotifyPage<Song> {
        require(album.id.matches(Regex("[A-Za-z0-9]+")))
        val url = next ?: "https://api.spotify.com/v1/albums/${album.id}/tracks?limit=20"
        AudiaryLog.i("LIBRARY_REQUEST_STARTED: album tracks from $url")
        return try {
            val json = get(url)
            val tracks = json.optJSONArray("items").objects().mapNotNull { parseTrack(it, albumOverride = album) }
            val total = json.optInt("total")
            AudiaryLog.i("LIBRARY_REQUEST_SUCCESS: album tracks count=${tracks.size}, total=$total")
            SpotifyPage(tracks, json.stringOrNull("next"), total)
        } catch (e: SpotifyFailure) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: status=${e.status}, message=${e.message}")
            throw e
        } catch (e: Exception) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: error=${e.message}")
            throw e
        }
    }

    suspend fun playlists(next: String? = null): SpotifyPage<SpotifyPlaylist> {
        val url = next ?: "https://api.spotify.com/v1/me/playlists?limit=50"
        AudiaryLog.i("LIBRARY_REQUEST_STARTED: playlists from $url")
        return try {
            val json = get(url)
            val items = json.optJSONArray("items").objects().mapNotNull { parsePlaylist(it) }
            val total = json.optInt("total")
            AudiaryLog.i("LIBRARY_REQUEST_SUCCESS: playlists count=${items.size}, total=$total")
            SpotifyPage(items, json.stringOrNull("next"), total)
        } catch (e: SpotifyFailure) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: status=${e.status}, message=${e.message}")
            throw e
        } catch (e: Exception) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: error=${e.message}")
            throw e
        }
    }

    suspend fun playlistTracks(playlistId: String, next: String? = null): SpotifyPage<Song> {
        require(playlistId.matches(Regex("[A-Za-z0-9_-]+")))
        val url = next ?: "https://api.spotify.com/v1/playlists/$playlistId/items?limit=50"
        AudiaryLog.i("LIBRARY_REQUEST_STARTED: playlist items from $url")
        return try {
            val json = get(url)
            val tracks = json.optJSONArray("items").objects().mapNotNull { saved ->
                saved.optJSONObject("track")?.let { track -> parseTrack(track, addedAt = saved.stringOrNull("added_at")) }
            }
            val total = json.optInt("total")
            AudiaryLog.i("LIBRARY_REQUEST_SUCCESS: playlist tracks count=${tracks.size}, total=$total")
            SpotifyPage(tracks, json.stringOrNull("next"), total)
        } catch (e: SpotifyFailure) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: status=${e.status}, message=${e.message}")
            throw e
        } catch (e: Exception) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: error=${e.message}")
            throw e
        }
    }

    companion object {
        fun parseAlbum(json: JSONObject): MusicAlbum? {
            val id = json.stringOrNull("id") ?: return null
            if (!id.matches(Regex("[A-Za-z0-9]+"))) return null
            return MusicAlbum(
                id, json.optString("name").ifBlank { "Untitled album" }, artists(json),
                artwork(json), json.optString("release_date"), "https://open.spotify.com/album/$id", json.optInt("total_tracks")
            )
        }

        fun parsePlaylist(json: JSONObject): SpotifyPlaylist? {
            val id = json.stringOrNull("id") ?: return null
            val name = json.optString("name").ifBlank { "Untitled Playlist" }
            val desc = json.stringOrNull("description")
            val ownerObj = json.optJSONObject("owner")
            val ownerName = ownerObj?.optString("display_name")?.ifBlank { null }
                ?: ownerObj?.stringOrNull("id") ?: "Spotify"
            val externalUrl = json.optJSONObject("external_urls")?.stringOrNull("spotify")
                ?: "https://open.spotify.com/playlist/$id"
            val total = json.optJSONObject("tracks")?.optInt("total") ?: 0
            val collaborative = json.optBoolean("collaborative", false)
            return SpotifyPlaylist(
                id = id,
                name = name,
                description = desc,
                artUrl = artwork(json),
                ownerName = ownerName,
                totalTracks = total,
                externalUrl = externalUrl,
                isOwnerOrCollaborator = collaborative || true
            )
        }

        fun parseTrack(json: JSONObject, addedAt: String? = null, albumOverride: MusicAlbum? = null): Song? {
            if (json.optBoolean("is_local")) return null
            val id = json.stringOrNull("id") ?: return null
            if (!id.matches(Regex("[A-Za-z0-9]+"))) return null
            val album = albumOverride ?: json.optJSONObject("album")?.let { parseAlbum(it) }
            return Song(
                "spotify:track:$id", json.optString("name").ifBlank { "Untitled track" },
                artists(json), album?.title.orEmpty(), json.optLong("duration_ms").coerceAtLeast(0),
                album?.releaseDate.orEmpty(), 0xFF252925, album?.artUrl, MusicSource.Spotify, id,
                "https://open.spotify.com/track/$id", album?.let { "spotify:album:${it.id}" }.orEmpty(),
                runCatching { Instant.parse(addedAt).toEpochMilli() }.getOrNull()
            )
        }

        private fun artists(json: JSONObject) = json.optJSONArray("artists").objects()
            .mapNotNull { it.stringOrNull("name") }.joinToString(", ").ifBlank { "Artist unavailable" }

        private fun artwork(json: JSONObject): String? = json.optJSONArray("images").objects()
            .sortedBy { kotlin.math.abs(it.optInt("width", 300) - 300) }
            .firstNotNullOfOrNull { it.stringOrNull("url")?.takeIf { url -> url.startsWith("https://") } }
    }
}

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }
private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
