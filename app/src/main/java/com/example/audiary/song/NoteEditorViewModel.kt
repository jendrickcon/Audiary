package com.example.audiary.song

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiary.data.DiaryRepository
import com.example.audiary.model.Note
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class EditorState(
    val open: Boolean = false,
    val draft: String = "",
    val noteId: String = "",
    val songId: String = "",
    val editing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null
)

class NoteEditorViewModel(private val diary: DiaryRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(EditorState(
        open = saved["open"] ?: false, draft = saved["draft"] ?: "",
        noteId = saved["noteId"] ?: "", songId = saved["songId"] ?: "", editing = saved["editing"] ?: false
    ))
    val state = _state.asStateFlow()
    private fun set(value: EditorState) {
        _state.value = value
        saved["open"] = value.open
        saved["draft"] = value.draft
        saved["noteId"] = value.noteId
        saved["songId"] = value.songId
        saved["editing"] = value.editing
    }
    fun create(songId: String) = set(EditorState(open = true, songId = songId, noteId = UUID.randomUUID().toString()))
    fun edit(note: Note) = set(EditorState(open = true, draft = note.text, noteId = note.id, songId = note.songId, editing = true))
    fun change(text: String) { if (!_state.value.saving) set(_state.value.copy(draft = text, error = null)) }
    fun cancel() { if (!_state.value.saving) set(EditorState()) }
    fun save() {
        val current = _state.value
        if (!current.open || current.saving || current.draft.isBlank()) return
        set(current.copy(saving = true, error = null))
        viewModelScope.launch {
            try {
                if (current.editing) diary.updateNote(current.noteId, current.draft.trim(), System.currentTimeMillis())
                else diary.addNote(Note(current.noteId, current.songId, current.draft.trim(), System.currentTimeMillis()))
                set(EditorState())
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                set(current.copy(error = "Your memory couldn't be saved. Your words are still here — try again."))
            }
        }
    }
}
