package com.example.audiary.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiary.data.DiaryRepository
import com.example.audiary.data.MusicRepository
import com.example.audiary.model.Note
import com.example.audiary.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SongUiState(
    val song: Song? = null,
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val notesError: String? = null,
    val deleting: Boolean = false
)

class SongViewModel(
    private val songId: String,
    private val music: MusicRepository,
    private val diary: DiaryRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SongUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var notesJob: Job? = null
    init { load(); observeNotes() }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val song = if (songId.isBlank()) null else music.getSong(songId)
                _state.update { it.copy(song = song, isLoading = false,
                    error = if (song == null) "Music details are unavailable. Your memories are still here." else null) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Couldn't load this song. Please try again.") }
            }
        }
    }

    fun observeNotes() {
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            _state.update { it.copy(notesError = null) }
            try {
                diary.getNotesForSong(songId).collect { notes -> _state.update { it.copy(notes = notes) } }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.update { it.copy(notesError = "Couldn't read your memories. Try again.") }
            }
        }
    }

    fun deleteNote(id: String) {
        if (_state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            try {
                diary.deleteNote(id)
                _state.update { it.copy(deleting = false, notesError = null) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.update { it.copy(deleting = false, notesError = "Couldn't delete this memory. Please try again.") }
            }
        }
    }
}
