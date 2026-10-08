package com.example.audiary.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiary.data.FavoritesRepository
import com.example.audiary.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FavoritesViewModel(private val repository: FavoritesRepository) : ViewModel() {
    val songs = repository.songs().catch { _error.value = "Couldn't read favorite songs." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = repository.observe().catch { _error.value = "Couldn't read favorites." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val busy = mutableSetOf<String>()
    fun clearError() { _error.value = null }
    fun toggle(song: Song) {
        if (!busy.add(song.id)) return
        val selected = song.id !in favorites.value
        viewModelScope.launch {
            try { repository.setFavorite(song, selected) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _error.value = "Couldn't update this favorite. Please try again." }
            finally { busy.remove(song.id) }
        }
    }
}
