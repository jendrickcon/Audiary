package com.example.audiary.data

import com.example.audiary.model.Note
import com.example.audiary.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

interface DiaryRepository {
    fun getNotesForSong(songId: String): Flow<List<Note>>
    suspend fun addNote(note: Note)
    fun getAllEntries(): Flow<List<DiaryEntry>>
    suspend fun updateNote(id: String, text: String, updatedAt: Long)
    suspend fun deleteNote(id: String)
}
