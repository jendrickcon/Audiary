package com.example.audiary.model

/** A personal note. Belongs to Audiary, not to any music service. */
data class Note(
    val id: String,
    val songId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val isSample: Boolean = false
)

data class DiaryEntry(val note: Note, val song: Song?)
