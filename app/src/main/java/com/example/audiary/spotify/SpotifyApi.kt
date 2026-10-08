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
    val ownerId: String? = null,
    val totalTracks: Int?,
    val externalUrl: String,
    val isOwnerOrCollaborator: Boolean = true,
    val isAccessible: Boolean = true
) {
    fun artworkSong() = Song(
        "spotify:playlist:$id", name, ownerName, name, 0, "", 0xFF252925, artUrl,
        MusicSource.Spotify, externalUrl = externalUrl, albumId = "spotify:playlist:$id"
    )

    fun formattedTrackCount(): String = when (totalTracks) {
        null -> "Track count unavailable"
        0 -> "0 tracks"
        1 -> "1 track"
        else -> "$totalTracks tracks"
    }
}

data class SpotifyPage<T>(val items: List<T>, val next: String?, val total: Int)

class SpotifyApi(
    private val auth: SpotifyAuthManager,
    private val http: SpotifyHttp,
    private val baseUrl: String = "https://api.spotify.com"
) {
    private suspend fun get(url: String): JSONObject {
        val parsed = java.net.URI(url)
        val baseUri = java.net.URI(baseUrl)
        val matchesBase = if (baseUri.host == "api.spotify.com") {
            parsed.scheme == "https" && parsed.host == "api.spotify.com" && parsed.port == -1
        } else {
            parsed.host == baseUri.host && parsed.port == baseUri.port
        }
        require(matchesBase && parsed.userInfo == null && parsed.path.startsWith("/v1/")) { "Untrusted Spotify pagination URL" }
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
            val json = get("$baseUrl/v1/me")
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

    private var cachedUserId: String? = null

    suspend fun currentUserId(): String? {
        if (cachedUserId != null) return cachedUserId
        return runCatching {
            val userProfile = profile()
            val id = userProfile.stringOrNull("id")
            cachedUserId = id
            id
        }.getOrNull()
    }

    suspend fun tracks(next: String? = null, limit: Int = 40): SpotifyPage<Song> {
        val url = next ?: "$baseUrl/v1/me/tracks?limit=$limit"
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
        val url = next ?: "$baseUrl/v1/me/albums?limit=20"
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
        val url = next ?: "$baseUrl/v1/albums/${album.id}/tracks?limit=20"
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
        val url = next ?: "$baseUrl/v1/me/playlists?limit=50"
        AudiaryLog.i("LIBRARY_REQUEST_STARTED: playlists from $url")
        val userId = currentUserId()
        return try {
            val json = get(url)
            val items = json.optJSONArray("items").objects().mapNotNull { parsePlaylist(it, userId) }
            val total = json.optInt("total", items.size)
            AudiaryLog.i("LIBRARY_REQUEST_SUCCESS: playlists count=${items.size}, total=$total, currentUserId=$userId")
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
        val url = next ?: "$baseUrl/v1/playlists/$playlistId/items?limit=50"
        AudiaryLog.i("LIBRARY_REQUEST_STARTED: playlist items from $url")
        return try {
            val json = get(url)
            val rawItems = json.optJSONArray("items").objects()
            var itemFields = 0
            var trackFields = 0
            var skipped = 0

            val tracks = rawItems.mapNotNull { saved ->
                val hasItem = saved.has("item") && !saved.isNull("item")
                val hasTrack = saved.has("track") && !saved.isNull("track")
                if (hasItem) itemFields++ else if (hasTrack) trackFields++

                val song = parsePlaylistItem(saved)
                if (song == null) skipped++
                song
            }

            val total = json.optInt("total", tracks.size)
            AudiaryLog.i("LIBRARY_REQUEST_SUCCESS: playlist tracks raw=${rawItems.size}, mapped=${tracks.size}, itemFields=$itemFields, trackFields=$trackFields, skipped=$skipped, total=$total")
            SpotifyPage(tracks, json.stringOrNull("next"), total)
        } catch (e: SpotifyFailure) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: status=${e.status}, message=${e.message}, playlistId=$playlistId")
            throw e
        } catch (e: Exception) {
            AudiaryLog.e("LIBRARY_REQUEST_FAILED: error=${e.message}, playlistId=$playlistId")
            throw e
        }
    }

    companion object {
        fun parsePlaylistItem(saved: JSONObject): Song? {
            val trackObj = when {
                saved.has("item") && !saved.isNull("item") -> saved.optJSONObject("item")
                saved.has("track") && !saved.isNull("track") -> saved.optJSONObject("track")
                else -> null
            } ?: return null

            // Filter out non-music items (e.g. podcast episodes) and local files
            val type = trackObj.optString("type", "track")
            if (type.equals("episode", ignoreCase = true) || saved.optBoolean("is_local", false)) {
                return null
            }

            return parseTrack(trackObj, addedAt = saved.stringOrNull("added_at"))
        }

        fun parseAlbum(json: JSONObject): MusicAlbum? {
            val id = json.stringOrNull("id") ?: return null
            if (!id.matches(Regex("[A-Za-z0-9]+"))) return null
            return MusicAlbum(
                id, json.optString("name").ifBlank { "Untitled album" }, artists(json),
                artwork(json), json.optString("release_date"), "https://open.spotify.com/album/$id", json.optInt("total_tracks")
            )
        }

        fun parsePlaylist(json: JSONObject, currentUserId: String? = null): SpotifyPlaylist? {
            val id = json.stringOrNull("id") ?: return null
            val name = json.optString("name").ifBlank { "Untitled Playlist" }
            val desc = json.stringOrNull("description")
            val ownerObj = json.optJSONObject("owner")
            val ownerId = ownerObj?.stringOrNull("id")
            val ownerName = ownerObj?.optString("display_name")?.ifBlank { null }
                ?: ownerId ?: "Spotify"
            val externalUrl = json.optJSONObject("external_urls")?.stringOrNull("spotify")
                ?: "https://open.spotify.com/playlist/$id"

            // Spotify February 2026 changes: 'items.total' instead of 'tracks.total'.
            // In addition, legacy/cached endpoints might return 'tracks.total' or direct count fields.
            val itemsObj = json.optJSONObject("items")
            val tracksObj = json.optJSONObject("tracks")
            val hasItemsTotal = itemsObj?.has("total") == true
            val hasTracksTotal = tracksObj?.has("total") == true

            var resolvedSource = "unavailable"
            val totalTracks: Int? = when {
                hasItemsTotal && itemsObj?.optInt("total", -1) != -1 -> {
                    resolvedSource = "items.total"
                    itemsObj!!.optInt("total")
                }
                hasTracksTotal && tracksObj?.optInt("total", -1) != -1 -> {
                    resolvedSource = "tracks.total"
                    tracksObj!!.optInt("total")
                }
                json.has("total_tracks") && json.optInt("total_tracks", -1) != -1 -> {
                    resolvedSource = "total_tracks"
                    json.optInt("total_tracks")
                }
                json.has("total") && json.optInt("total", -1) != -1 -> {
                    resolvedSource = "total"
                    json.optInt("total")
                }
                else -> null
            }

            val collaborative = json.optBoolean("collaborative", false)
            val isOwner = currentUserId != null && ownerId != null && currentUserId.equals(ownerId, ignoreCase = true)
            // If currentUserId is known: user owns it or is collaborator.
            // If currentUserId is unknown: assume accessible unless owner is explicitly non-matching.
            val isOwnerOrCollaborator = if (currentUserId != null) {
                isOwner || collaborative
            } else {
                collaborative || true
            }

            AudiaryLog.d("PLAYLIST_PARSED: id=$id, name=\"$name\", hasItemsTotal=$hasItemsTotal, hasTracksTotal=$hasTracksTotal, countSource=$resolvedSource, totalTracks=$totalTracks, ownerId=$ownerId, isOwnerOrCollaborator=$isOwnerOrCollaborator")

            return SpotifyPlaylist(
                id = id,
                name = name,
                description = desc,
                artUrl = artwork(json),
                ownerName = ownerName,
                ownerId = ownerId,
                totalTracks = totalTracks,
                externalUrl = externalUrl,
                isOwnerOrCollaborator = isOwnerOrCollaborator,
                isAccessible = true
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
