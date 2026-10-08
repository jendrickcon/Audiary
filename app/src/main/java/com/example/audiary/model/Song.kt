package com.example.audiary.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val releaseDate: String,
    val artColor: Long,         // placeholder artwork color
    val artUrl: String? = null,
    val source: MusicSource = MusicSource.Demo,
    val spotifyTrackId: String? = null,
    val externalUrl: String? = null,
    val albumId: String = "",
    val addedAt: Long? = null
) {
    val durationText: String
        get() {
            val total = durationMs / 1000
            return "%d:%02d".format(total / 60, total % 60)
        }
}

enum class MusicSource { Demo, Spotify }
