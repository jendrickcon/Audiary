package com.example.audiary.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.audiary.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp
import com.spotify.protocol.types.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Controller that interfaces directly with the Spotify App Remote SDK.
 * Exposes a reactive StateFlow of playback state and provides controls for
 * play, pause, resume, skip next/previous, seek, and external fallback.
 */
class SpotifyPlaybackController(
    private val context: Context,
    private val clientId: String = BuildConfig.SPOTIFY_CLIENT_ID,
    private val redirectUri: String = BuildConfig.SPOTIFY_REDIRECT_URI,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val _state = MutableStateFlow(
        SpotifyPlaybackState(isSpotifyInstalled = SpotifyAppRemote.isSpotifyInstalled(context))
    )
    val state: StateFlow<SpotifyPlaybackState> = _state.asStateFlow()

    private var appRemote: SpotifyAppRemote? = null
    private var progressTickerJob: Job? = null

    init {
        // Start position ticker job to smoothly update progress while playing
        startProgressTicker()
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = scope.launch {
            while (isActive) {
                delay(500)
                val current = _state.value
                if (current.isPlaying && current.trackDurationMs > 0L) {
                    val nextPos = (current.playbackPositionMs + 500).coerceAtMost(current.trackDurationMs)
                    _state.update { it.copy(playbackPositionMs = nextPos) }
                }
            }
        }
    }

    /**
     * Connect to Spotify App Remote.
     * @param showAuthView whether to show Spotify auth view if needed
     * @param onConnected callback invoked when connection is established
     */
    fun connect(showAuthView: Boolean = false, onConnected: (() -> Unit)? = null) {
        if (appRemote?.isConnected == true) {
            _state.update { it.copy(isConnected = true, isConnecting = false, error = null) }
            onConnected?.invoke()
            return
        }

        val isInstalled = SpotifyAppRemote.isSpotifyInstalled(context)
        if (!isInstalled) {
            AudiaryLog.w("SpotifyPlaybackController: Spotify app is not installed.")
            _state.update {
                it.copy(
                    isSpotifyInstalled = false,
                    isConnecting = false,
                    error = "Spotify app is not installed."
                )
            }
            return
        }

        if (clientId.isBlank()) {
            AudiaryLog.w("SpotifyPlaybackController: Client ID is blank.")
            _state.update {
                it.copy(
                    isConnecting = false,
                    error = "Spotify Client ID not configured."
                )
            }
            return
        }

        _state.update { it.copy(isConnecting = true, error = null) }
        AudiaryLog.i("SpotifyPlaybackController: Connecting to Spotify App Remote...")

        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(showAuthView)
            .build()

        SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(remote: SpotifyAppRemote) {
                AudiaryLog.i("SpotifyPlaybackController: Connected to Spotify App Remote!")
                appRemote = remote
                _state.update { it.copy(isConnected = true, isConnecting = false, error = null) }

                remote.playerApi.subscribeToPlayerState()
                    .setEventCallback { playerState ->
                        handlePlayerState(playerState)
                    }
                    .setErrorCallback { error ->
                        AudiaryLog.w("SpotifyPlaybackController: PlayerState subscription error: ${error.message}", error)
                    }

                onConnected?.invoke()
            }

            override fun onFailure(throwable: Throwable) {
                AudiaryLog.w("SpotifyPlaybackController: Connection failed: ${throwable.message}", throwable)
                appRemote = null
                val errorMsg = when (throwable) {
                    is CouldNotFindSpotifyApp -> "Spotify app is not running or installed."
                    else -> throwable.message ?: "Could not connect to Spotify."
                }
                _state.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        error = errorMsg
                    )
                }
            }
        })
    }

    private fun handlePlayerState(playerState: PlayerState) {
        val track = playerState.track
        if (track == null) {
            _state.update {
                it.copy(
                    isPlaying = !playerState.isPaused,
                    isPaused = playerState.isPaused,
                    playbackPositionMs = playerState.playbackPosition
                )
            }
            return
        }

        val uri = track.uri.orEmpty()
        val trackId = if (uri.startsWith("spotify:track:")) uri.substringAfter("spotify:track:") else uri
        val artistName = track.artist?.name
            ?: track.artists?.joinToString(", ") { it.name }.orEmpty()
        val albumName = track.album?.name.orEmpty()
        val isPaused = playerState.isPaused
        val isPlaying = !isPaused
        val position = playerState.playbackPosition
        val duration = track.duration
        val restrictions = playerState.playbackRestrictions
        val canNext = restrictions?.canSkipNext ?: true
        val canPrev = restrictions?.canSkipPrev ?: true

        _state.update { prev ->
            prev.copy(
                isConnected = true,
                trackId = trackId,
                trackUri = uri,
                title = track.name ?: prev.title,
                artist = artistName.ifBlank { prev.artist },
                album = albumName.ifBlank { prev.album },
                isPlaying = isPlaying,
                isPaused = isPaused,
                playbackPositionMs = position,
                trackDurationMs = duration,
                canSkipNext = canNext,
                canSkipPrev = canPrev
            )
        }

        // Fetch album cover bitmap if imageUri exists
        val imageUri = track.imageUri
        if (imageUri != null) {
            appRemote?.imagesApi?.getImage(imageUri)?.setResultCallback { bitmap ->
                _state.update { it.copy(coverBitmap = bitmap) }
            }
        }
    }

    /**
     * Plays a track using Spotify App Remote.
     * @param trackIdOrUri the Spotify ID or URI (spotify:track:...)
     * @param title optional fallback title for instant UI feedback
     * @param artist optional fallback artist for instant UI feedback
     * @param coverUrl optional fallback cover URL for instant UI feedback
     */
    fun play(
        trackIdOrUri: String,
        title: String? = null,
        artist: String? = null,
        coverUrl: String? = null
    ) {
        val uri = if (trackIdOrUri.startsWith("spotify:track:")) trackIdOrUri else "spotify:track:$trackIdOrUri"
        val trackId = uri.substringAfter("spotify:track:")

        // Optimistically set track metadata for immediate UI feedback
        _state.update {
            it.copy(
                trackId = trackId,
                trackUri = uri,
                title = title ?: it.title,
                artist = artist ?: it.artist,
                coverUrl = coverUrl ?: it.coverUrl,
                error = null
            )
        }

        val remote = appRemote
        if (remote != null && remote.isConnected) {
            remote.playerApi.play(uri)
                .setResultCallback {
                    AudiaryLog.i("SpotifyPlaybackController: play($uri) success")
                }
                .setErrorCallback { error ->
                    AudiaryLog.w("SpotifyPlaybackController: play($uri) error: ${error.message}", error)
                    _state.update { it.copy(error = error.message) }
                }
        } else {
            connect(showAuthView = true) {
                appRemote?.playerApi?.play(uri)
                    ?.setResultCallback {
                        AudiaryLog.i("SpotifyPlaybackController: play($uri) success after connect")
                    }
                    ?.setErrorCallback { error ->
                        AudiaryLog.w("SpotifyPlaybackController: play($uri) error: ${error.message}", error)
                        _state.update { it.copy(error = error.message) }
                    }
            }
        }
    }

    /**
     * Toggles between play and pause.
     */
    fun togglePlayPause() {
        val remote = appRemote
        if (remote != null && remote.isConnected) {
            if (_state.value.isPlaying) {
                remote.playerApi.pause()
            } else {
                remote.playerApi.resume()
            }
        } else {
            val uri = _state.value.trackUri
            if (!uri.isNullOrBlank()) {
                connect(showAuthView = true) {
                    appRemote?.playerApi?.resume()
                }
            } else {
                connect(showAuthView = true)
            }
        }
    }

    fun pause() {
        appRemote?.playerApi?.pause()
    }

    fun resume() {
        appRemote?.playerApi?.resume()
    }

    fun skipNext() {
        appRemote?.playerApi?.skipNext()
    }

    fun skipPrevious() {
        appRemote?.playerApi?.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        appRemote?.playerApi?.seekTo(positionMs)
        _state.update { it.copy(playbackPositionMs = positionMs) }
    }

    /**
     * Fallback to open track directly in Spotify app or browser.
     */
    fun openExternal(context: Context, trackIdOrUri: String) {
        val trackId = trackIdOrUri.removePrefix("spotify:track:")
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:track:$trackId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/track/$trackId")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun disconnect() {
        progressTickerJob?.cancel()
        appRemote?.let {
            SpotifyAppRemote.disconnect(it)
            appRemote = null
            _state.update { s -> s.copy(isConnected = false) }
        }
    }
}

