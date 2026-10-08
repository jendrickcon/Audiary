package com.example.audiary.explore

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DiscoverySource {
    val displayName: String

    data object AllSavedSongs : DiscoverySource {
        override val displayName: String = "All Saved Songs"
    }

    data class SpotifyPlaylistSource(
        val playlistId: String,
        val playlistName: String,
        val artUrl: String? = null
    ) : DiscoverySource {
        override val displayName: String = "Playlist: $playlistName"
    }

    data class ArtistSource(
        val artistName: String,
        val artistId: String? = null
    ) : DiscoverySource {
        override val displayName: String = "Artist: $artistName"
    }

    data object Demo : DiscoverySource {
        override val displayName: String = "Demo Collection"
    }
}

class DiscoverySourceManager(context: Context? = null) {
    private val prefs = context?.getSharedPreferences("audiary_discovery", Context.MODE_PRIVATE)

    private val _currentSource = MutableStateFlow<DiscoverySource>(loadInitialSource())
    val currentSource = _currentSource.asStateFlow()

    private fun loadInitialSource(): DiscoverySource {
        val type = prefs?.getString("source_type", "ALL_SAVED") ?: "ALL_SAVED"
        return when (type) {
            "ALL_SAVED" -> DiscoverySource.AllSavedSongs
            "PLAYLIST" -> {
                val id = prefs?.getString("playlist_id", null)
                val name = prefs?.getString("playlist_name", null)
                val art = prefs?.getString("playlist_art", null)
                if (id != null && name != null) DiscoverySource.SpotifyPlaylistSource(id, name, art)
                else DiscoverySource.AllSavedSongs
            }
            "ARTIST" -> {
                val name = prefs?.getString("artist_name", null)
                if (name != null) DiscoverySource.ArtistSource(name)
                else DiscoverySource.AllSavedSongs
            }
            else -> DiscoverySource.Demo
        }
    }

    fun setSource(source: DiscoverySource) {
        _currentSource.value = source
        prefs?.edit()?.apply {
            when (source) {
                is DiscoverySource.AllSavedSongs -> {
                    putString("source_type", "ALL_SAVED")
                }
                is DiscoverySource.SpotifyPlaylistSource -> {
                    putString("source_type", "PLAYLIST")
                    putString("playlist_id", source.playlistId)
                    putString("playlist_name", source.playlistName)
                    putString("playlist_art", source.artUrl)
                }
                is DiscoverySource.ArtistSource -> {
                    putString("source_type", "ARTIST")
                    putString("artist_name", source.artistName)
                }
                is DiscoverySource.Demo -> {
                    putString("source_type", "DEMO")
                }
            }
            apply()
        }
    }
}

