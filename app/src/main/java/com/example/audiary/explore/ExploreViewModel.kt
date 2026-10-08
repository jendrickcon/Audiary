package com.example.audiary.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiary.data.MusicRepository
import com.example.audiary.data.local.AudiaryDatabase
import com.example.audiary.data.local.entity
import com.example.audiary.model.MusicSource
import com.example.audiary.model.Song
import com.example.audiary.spotify.SpotifyApi
import com.example.audiary.spotify.SpotifyAuthManager
import com.example.audiary.spotify.SpotifyFailure
import com.example.audiary.spotify.SpotifyPlaylist
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

const val ROW_COUNT = 5
private const val ROW_SIZE = 16

data class ExploreUiState(
    val rows: List<List<Song>> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val source: DiscoverySource = DiscoverySource.Demo,
    val isSpotifyConnected: Boolean = false,
    val songCount: Int = 0,
    val availablePlaylists: List<SpotifyPlaylist> = emptyList()
)

class ExploreViewModel(
    private val music: MusicRepository,
    private val spotifyApi: SpotifyApi? = null,
    private val spotifyAuth: SpotifyAuthManager? = null,
    private val db: AudiaryDatabase? = null,
    private val sourceManager: DiscoverySourceManager? = null
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    private var demoPool: List<Song> = emptyList()
    private var spotifyPool: MutableList<Song> = mutableListOf()
    private var playlistPool: List<Song> = emptyList()
    private var artistPool: List<Song> = emptyList()

    private var prefetchJob: Job? = null
    private var loadJob: Job? = null

    init {
        // Observe auth state
        if (spotifyAuth != null) {
            viewModelScope.launch {
                spotifyAuth.state.map { it.connected to it.sessionId }.distinctUntilChanged().collect { (connected, _) ->
                    _state.update { it.copy(isSpotifyConnected = connected) }
                    if (connected) {
                        loadPlaylistsForPicker()
                        val current = sourceManager?.currentSource?.value ?: DiscoverySource.AllSavedSongs
                        if (current is DiscoverySource.Demo) {
                            selectSource(DiscoverySource.AllSavedSongs)
                        } else {
                            selectSource(current)
                        }
                    } else {
                        selectSource(DiscoverySource.Demo)
                    }
                }
            }
        }

        // Observe source changes from sourceManager
        if (sourceManager != null) {
            viewModelScope.launch {
                sourceManager.currentSource.collect { source ->
                    if (_state.value.source != source) {
                        selectSource(source)
                    }
                }
            }
        } else {
            load()
        }
    }

    fun selectSource(source: DiscoverySource) {
        val targetSource = if (source !is DiscoverySource.Demo && _state.value.isSpotifyConnected) {
            source
        } else {
            DiscoverySource.Demo
        }
        _state.update { it.copy(source = targetSource) }
        sourceManager?.setSource(targetSource)
        load()
    }

    private fun loadPlaylistsForPicker() {
        if (spotifyApi == null) return
        viewModelScope.launch {
            try {
                val page = spotifyApi.playlists()
                _state.update { it.copy(availablePlaylists = page.items) }
            } catch (_: Exception) {
                // Silently ignore picker playlist load errors
            }
        }
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            when (val source = _state.value.source) {
                is DiscoverySource.AllSavedSongs -> loadSavedTracks()
                is DiscoverySource.SpotifyPlaylistSource -> loadPlaylistTracks(source)
                is DiscoverySource.ArtistSource -> loadArtistTracks(source)
                is DiscoverySource.Demo -> loadDemoTracks()
            }
        }
    }

    private suspend fun loadDemoTracks() {
        try {
            if (demoPool.isEmpty()) {
                demoPool = music.getSongs()
            }
            _state.update {
                it.copy(
                    rows = arrange(demoPool),
                    isLoading = false,
                    source = DiscoverySource.Demo,
                    songCount = demoPool.size,
                    error = null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "Couldn't load your music.") }
        }
    }

    private suspend fun loadSavedTracks() {
        try {
            // First: seed from DB cache for instant display
            if (db != null && spotifyPool.isEmpty()) {
                val dbCached = db.dao().songs(MusicSource.Spotify.name).map { it.domain() }
                if (dbCached.isNotEmpty()) {
                    spotifyPool.clear()
                    spotifyPool.addAll(dbCached)
                    _state.update {
                        it.copy(
                            rows = arrange(spotifyPool),
                            isLoading = false,
                            source = DiscoverySource.AllSavedSongs,
                            songCount = spotifyPool.size,
                            error = null
                        )
                    }
                }
            }

            if (spotifyApi != null && spotifyAuth?.state?.value?.connected == true) {
                val page = spotifyApi.tracks(limit = 40)
                val tracks = page.items
                if (tracks.isNotEmpty()) {
                    db?.dao()?.storeSongs(tracks.map { it.entity() })
                    val merged = (spotifyPool + tracks).distinctBy { it.id }
                    spotifyPool.clear()
                    spotifyPool.addAll(merged)
                }

                _state.update {
                    it.copy(
                        rows = arrange(spotifyPool),
                        isLoading = false,
                        source = DiscoverySource.AllSavedSongs,
                        songCount = spotifyPool.size,
                        error = null
                    )
                }

                // Progressive background prefetching: expand discovery pool up to 150-200 songs
                startProgressivePrefetch(page.next)
            } else {
                selectSource(DiscoverySource.Demo)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (spotifyPool.isNotEmpty()) {
                _state.update {
                    it.copy(
                        rows = arrange(spotifyPool),
                        isLoading = false,
                        source = DiscoverySource.AllSavedSongs,
                        songCount = spotifyPool.size,
                        error = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Couldn't load your Spotify library. Check your connection or switch to Demo."
                    )
                }
            }
        }
    }

    private fun startProgressivePrefetch(initialNext: String?) {
        var nextUrl = initialNext ?: return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            var fetchedPages = 0
            while (nextUrl.isNotBlank() && fetchedPages < 4 && isActive) {
                try {
                    delay(400) // Pacing to prevent API pressure
                    val page = spotifyApi?.tracks(nextUrl, limit = 40) ?: break
                    val newTracks = page.items
                    if (newTracks.isEmpty()) break
                    db?.dao()?.storeSongs(newTracks.map { it.entity() })

                    val merged = (spotifyPool + newTracks).distinctBy { it.id }
                    spotifyPool.clear()
                    spotifyPool.addAll(merged)

                    _state.update { it.copy(songCount = spotifyPool.size) }
                    nextUrl = page.next.orEmpty()
                    fetchedPages++
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private suspend fun loadPlaylistTracks(source: DiscoverySource.SpotifyPlaylistSource) {
        if (spotifyApi == null) {
            selectSource(DiscoverySource.Demo)
            return
        }
        try {
            val page = spotifyApi.playlistTracks(source.playlistId)
            playlistPool = page.items
            if (playlistPool.isNotEmpty()) {
                db?.dao()?.storeSongs(playlistPool.map { it.entity() })
                _state.update {
                    it.copy(
                        rows = arrange(playlistPool),
                        isLoading = false,
                        source = source,
                        songCount = playlistPool.size,
                        error = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        rows = emptyList(),
                        isLoading = false,
                        source = source,
                        songCount = 0,
                        error = "This playlist doesn't contain any accessible tracks."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpotifyFailure) {
            val msg = if (e.status == 403) {
                "Under Spotify's Developer Mode restrictions, tracks can only be read from playlists you own or collaborate on."
            } else {
                e.message
            }
            _state.update { it.copy(isLoading = false, error = msg) }
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "Couldn't load playlist tracks. Check your connection.") }
        }
    }

    private suspend fun loadArtistTracks(source: DiscoverySource.ArtistSource) {
        try {
            // Find songs from current loaded collection or database
            val dbSongs = db?.dao()?.songs(MusicSource.Spotify.name)?.map { it.domain() }.orEmpty()
            val allSources = (spotifyPool + dbSongs).distinctBy { it.id }
            val matching = allSources.filter {
                it.artist.split(",").any { a -> a.trim().equals(source.artistName.trim(), ignoreCase = true) }
            }
            artistPool = matching
            if (artistPool.isNotEmpty()) {
                _state.update {
                    it.copy(
                        rows = arrange(artistPool),
                        isLoading = false,
                        source = source,
                        songCount = artistPool.size,
                        error = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        rows = emptyList(),
                        isLoading = false,
                        source = source,
                        songCount = 0,
                        error = "No saved songs found for ${source.artistName}."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, error = "Couldn't load artist tracks.") }
        }
    }

    /** Shuffles the active in-memory cached pool without making any network API calls. */
    fun shuffle() {
        val currentPool = when (_state.value.source) {
            is DiscoverySource.AllSavedSongs -> spotifyPool
            is DiscoverySource.SpotifyPlaylistSource -> playlistPool
            is DiscoverySource.ArtistSource -> artistPool
            is DiscoverySource.Demo -> demoPool
        }
        if (currentPool.isEmpty()) return
        _state.update { it.copy(rows = arrange(currentPool)) }
    }

    /** Distribute songs across five rows with balanced distribution and graceful cycling. */
    fun arrange(songs: List<Song>): List<List<Song>> {
        if (songs.isEmpty()) return emptyList()
        val deck = songs.shuffled()
        val length = minOf(ROW_SIZE, maxOf(8, deck.size))
        val step = maxOf(1, deck.size / ROW_COUNT)
        return List(ROW_COUNT) { r ->
            List(length) { i -> deck[(r * step + i) % deck.size] }
        }
    }
}
