package com.example.audiary.spotify

import android.graphics.Bitmap

/**
 * State representing Spotify playback controlled via Spotify App Remote SDK.
 */
data class SpotifyPlaybackState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isSpotifyInstalled: Boolean = true,
    val trackId: String? = null,
    val trackUri: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val coverUrl: String? = null,
    val coverBitmap: Bitmap? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val trackDurationMs: Long = 0L,
    val canSkipNext: Boolean = true,
    val canSkipPrev: Boolean = true,
    val error: String? = null
) {
    val hasTrack: Boolean get() = title.isNotBlank() || !trackUri.isNullOrBlank()
    val progress: Float get() = if (trackDurationMs > 0L) {
        (playbackPositionMs.toFloat() / trackDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val positionFormatted: String get() = formatTime(playbackPositionMs)
    val durationFormatted: String get() = formatTime(trackDurationMs)

    private fun formatTime(ms: Long): String {
        val totalSecs = (ms / 1000).coerceAtLeast(0)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "%d:%02d".format(mins, secs)
    }
}
