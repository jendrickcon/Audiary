package com.example.audiary.spotify

import com.example.audiary.model.Song

data class SpotifyArtist(
    val name: String,
    val songs: List<Song>,
    val songCount: Int = songs.size,
    val artUrl: String? = null,
    val spotifyArtistId: String? = null
)

