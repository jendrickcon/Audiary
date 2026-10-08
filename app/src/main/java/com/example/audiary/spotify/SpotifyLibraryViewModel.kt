package com.example.audiary.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiary.data.local.AudiaryDatabase
import com.example.audiary.data.local.entity
import com.example.audiary.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SpotifyLibraryState(
    val tracks: List<Song> = emptyList(),
    val albums: List<MusicAlbum> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val artists: List<SpotifyArtist> = emptyList(),
    val tracksLoaded: Boolean = false,
    val albumsLoaded: Boolean = false,
    val playlistsLoaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMoreTracks: Boolean = false,
    val error: String? = null,
    val tracksNext: String? = null,
    val canLoadMoreTracks: Boolean = true,
    val totalTracks: Int = 0,
    val selectedAlbum: MusicAlbum? = null,
    val albumSongs: List<Song> = emptyList(),
    val albumSongsNext: String? = null,
    val albumSongsPage: Int = 1,
    val albumPage: Int = 1,
    val albumsNext: String? = null,
    val selectedPlaylist: SpotifyPlaylist? = null,
    val playlistSongs: List<Song> = emptyList(),
    val playlistError: String? = null,
    val selectedArtist: SpotifyArtist? = null,
    val isIndexingArtists: Boolean = false,
    val indexedArtistSongsCount: Int = 0,
    val isArtistIndexComplete: Boolean = false
)

class SpotifyLibraryViewModel(
    private val api: SpotifyApi,
    private val auth: SpotifyAuthManager,
    private val db: AudiaryDatabase
) : ViewModel() {
    private val _state = MutableStateFlow(SpotifyLibraryState())
    val state = _state.asStateFlow()

    private var job: Job? = null
    private var indexingJob: Job? = null
    private var retryBlock: (suspend () -> Unit)? = null
    private val albumPages = mutableListOf<String?>(null)
    private val albumSongPages = mutableListOf<String?>(null)

    init {
        viewModelScope.launch {
            auth.state.map { it.connected to it.sessionId }.distinctUntilChanged().collect { (connected, _) ->
                job?.cancel()
                indexingJob?.cancel()
                albumPages.clear(); albumPages.add(null)
                albumSongPages.clear(); albumSongPages.add(null)
                _state.value = SpotifyLibraryState()
                if (connected) {
                    loadTracks()
                    startIndexingArtists()
                }
            }
        }
    }

    fun ensureLoaded(tab: String) {
        if (!auth.state.value.connected) return
        when (tab) {
            "Songs" -> if (!_state.value.tracksLoaded) loadTracks()
            "Albums" -> if (!_state.value.albumsLoaded) loadAlbums()
            "Playlists" -> if (!_state.value.playlistsLoaded) loadPlaylists()
            "Artists" -> {
                if (_state.value.artists.isEmpty() && !_state.value.isIndexingArtists) {
                    startIndexingArtists()
                }
            }
        }
    }

    private fun request(block: suspend () -> Unit) {
        retryBlock = block
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AudiaryLog.e("LIBRARY_LOAD_ERROR: ${e.message}", e)
                val userMsg = if (e is SpotifyFailure) {
                    "Spotify connected, but the library could not be loaded. ${e.message}"
                } else if (e is java.io.IOException) {
                    "Connection failed due to network issues. Check your connection and try again."
                } else {
                    "Spotify connected, but the library could not be loaded: ${e.message ?: "Please try again."}"
                }
                _state.update { it.copy(loading = false, error = userMsg) }
            }
        }
    }

    fun retry() { retryBlock?.let { request(it) } }

    fun loadTracks(refresh: Boolean = false) = request {
        val result = api.tracks(null, limit = 40)
        db.dao().storeSongs(result.items.map { it.entity() })
        _state.update {
            it.copy(
                tracks = result.items,
                tracksLoaded = true,
                tracksNext = result.next,
                canLoadMoreTracks = result.next != null,
                totalTracks = result.total,
                error = null
            )
        }
    }

    fun loadMoreTracks() {
        val nextUrl = _state.value.tracksNext ?: return
        if (_state.value.loadingMoreTracks || !_state.value.canLoadMoreTracks) return

        _state.update { it.copy(loadingMoreTracks = true) }
        viewModelScope.launch {
            try {
                val result = api.tracks(nextUrl, limit = 40)
                db.dao().storeSongs(result.items.map { it.entity() })
                _state.update { current ->
                    val combined = (current.tracks + result.items).distinctBy { it.id }
                    current.copy(
                        tracks = combined,
                        tracksNext = result.next,
                        canLoadMoreTracks = result.next != null,
                        loadingMoreTracks = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AudiaryLog.e("LOAD_MORE_TRACKS_ERROR: ${e.message}", e)
                _state.update { it.copy(loadingMoreTracks = false) }
            }
        }
    }

    fun loadAlbums(page: Int = _state.value.albumPage) = request {
        val result = api.albums(albumPages.getOrNull(page - 1))
        _state.update { it.copy(albums = result.items, albumsLoaded = true, albumsNext = result.next, albumPage = page) }
        while (albumPages.size > page) albumPages.removeAt(albumPages.lastIndex)
        result.next?.let { albumPages.add(it) }
    }

    fun openAlbum(album: MusicAlbum) {
        albumSongPages.clear(); albumSongPages.add(null)
        _state.update { it.copy(selectedAlbum = album, albumSongs = emptyList(), albumSongsPage = 1, albumSongsNext = null) }
        loadAlbumSongs()
    }

    fun closeAlbum() {
        job?.cancel()
        _state.update { it.copy(selectedAlbum = null, albumSongs = emptyList(), error = null, loading = false) }
    }

    fun loadAlbumSongs(page: Int = _state.value.albumSongsPage) {
        val album = _state.value.selectedAlbum ?: return
        request {
            val result = api.albumTracks(album, albumSongPages.getOrNull(page - 1))
            db.dao().storeSongs(result.items.map { it.entity() })
            _state.update { it.copy(albumSongs = result.items, albumSongsNext = result.next, albumSongsPage = page) }
            while (albumSongPages.size > page) albumSongPages.removeAt(albumSongPages.lastIndex)
            result.next?.let { albumSongPages.add(it) }
        }
    }

    fun loadPlaylists() = request {
        try {
            val result = api.playlists()
            _state.update { it.copy(playlists = result.items, playlistsLoaded = true, playlistError = null) }
        } catch (e: SpotifyFailure) {
            val msg = if (e.status == 403) {
                "Spotify requires playlist permissions. Please disconnect and reconnect your Spotify account to grant playlist access."
            } else {
                e.message
            }
            _state.update { it.copy(playlists = emptyList(), playlistsLoaded = true, playlistError = msg) }
        }
    }

    fun openPlaylist(playlist: SpotifyPlaylist) {
        _state.update { it.copy(selectedPlaylist = playlist, playlistSongs = emptyList(), playlistError = null) }
        request {
            try {
                val result = api.playlistTracks(playlist.id)
                db.dao().storeSongs(result.items.map { it.entity() })
                _state.update { it.copy(playlistSongs = result.items, playlistError = null) }
            } catch (e: SpotifyFailure) {
                val errorMsg = if (e.status == 403) {
                    "Under Spotify's Developer Mode, this playlist cannot be accessed. Only playlists you own or collaborate on can be read."
                } else {
                    e.message
                }
                _state.update { current ->
                    val updatedPlaylists = current.playlists.map {
                        if (it.id == playlist.id) it.copy(isAccessible = false) else it
                    }
                    current.copy(
                        playlists = updatedPlaylists,
                        selectedPlaylist = current.selectedPlaylist?.copy(isAccessible = false),
                        playlistError = errorMsg
                    )
                }
            }
        }
    }

    fun closePlaylist() {
        job?.cancel()
        _state.update { it.copy(selectedPlaylist = null, playlistSongs = emptyList(), playlistError = null) }
    }

    fun openArtist(artist: SpotifyArtist) {
        _state.update { it.copy(selectedArtist = artist) }
    }

    fun closeArtist() {
        _state.update { it.copy(selectedArtist = null) }
    }

    fun startIndexingArtists() {
        indexingJob?.cancel()
        indexingJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isIndexingArtists = true, indexedArtistSongsCount = 0, isArtistIndexComplete = false) }
            val artistMap = mutableMapOf<String, MutableList<Song>>()
            var nextUrl: String? = null
            var totalCount = 0
            var pageIndex = 0

            try {
                do {
                    val page = api.tracks(nextUrl, limit = 50)
                    nextUrl = page.next
                    totalCount += page.items.size
                    db.dao().storeSongs(page.items.map { it.entity() })

                    for (song in page.items) {
                        val artistNames = song.artist.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val distinctNames = if (artistNames.isEmpty()) listOf(song.artist) else artistNames
                        for (name in distinctNames) {
                            val list = artistMap.getOrPut(name) { mutableListOf() }
                            if (list.none { it.id == song.id }) {
                                list.add(song)
                            }
                        }
                    }

                    val artistsList = artistMap.map { (name, songs) ->
                        SpotifyArtist(name = name, songs = songs, songCount = songs.size)
                    }.sortedByDescending { it.songCount }

                    _state.update {
                        it.copy(
                            artists = artistsList,
                            indexedArtistSongsCount = totalCount,
                            isIndexingArtists = nextUrl != null,
                            isArtistIndexComplete = nextUrl == null
                        )
                    }

                    pageIndex++
                    if (nextUrl != null) {
                        delay(200) // gentle rate-limit pacing between pages
                    }
                } while (nextUrl != null && isActive)

                _state.update { it.copy(isIndexingArtists = false, isArtistIndexComplete = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AudiaryLog.w("ARTIST_INDEXING_WARNING: stopped after $totalCount tracks: ${e.message}")
                _state.update { it.copy(isIndexingArtists = false) }
            }
        }
    }
}
