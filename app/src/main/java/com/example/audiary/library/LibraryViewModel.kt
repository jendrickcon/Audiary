package com.example.audiary.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiary.data.MusicRepository
import com.example.audiary.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryState(val songs: List<Song> = emptyList(), val loading: Boolean = true, val error: String? = null)
class LibraryViewModel(private val music: MusicRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    init { load() }
    fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try { _state.value = LibraryState(music.getSongs(), loading = false) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(loading = false, error = "Your library couldn't be loaded.") } }
        }
    }
}
enum class LibraryTab { Songs, Albums, Artists, Favorites }
enum class LibrarySort(val label: String) { Title("Title"), Artist("Artist") }
fun filterLibrary(songs: List<Song>, query: String, favoriteIds: Set<String>, favoritesOnly: Boolean, sort: LibrarySort): List<Song> {
    val text = query.trim()
    val filtered = songs.filter { song ->
        (!favoritesOnly || song.id in favoriteIds) &&
            (text.isBlank() || listOf(song.title, song.artist, song.album).any { it.contains(text, ignoreCase = true) })
    }
    return when (sort) {
        LibrarySort.Title -> filtered.sortedBy { it.title.lowercase() }
        LibrarySort.Artist -> filtered.sortedWith(compareBy<Song> { it.artist.lowercase() }.thenBy { it.title.lowercase() })
    }
}
