package com.example.audiary.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiary.data.DiaryRepository
import com.example.audiary.model.DiaryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DiaryState(val entries: List<DiaryEntry> = emptyList(), val loading: Boolean = true, val error: String? = null)
class DiaryViewModel(private val diary: DiaryRepository) : ViewModel() {
    private val _state = MutableStateFlow(DiaryState())
    val state = _state.asStateFlow()
    private var observer: Job? = null
    init { load() }
    fun load() {
        observer?.cancel()
        observer = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try { diary.getAllEntries().collect { _state.value = DiaryState(it, loading = false) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(loading = false, error = "Couldn't read your diary. Your data hasn't been removed.") } }
        }
    }
    fun delete(id: String) = viewModelScope.launch {
        try { diary.deleteNote(id) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.update { it.copy(error = "Couldn't delete this memory. Please try again.") } }
    }
}
