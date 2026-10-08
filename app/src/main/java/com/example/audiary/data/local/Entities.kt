package com.example.audiary.data.local

import androidx.room.*
import com.example.audiary.model.*

@Entity(tableName = "notes", indices = [Index("songId"), Index("createdAt")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val songId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isSample: Boolean
) {
    fun domain() = Note(id, songId, text, createdAt, updatedAt, isSample)
}

fun Note.entity() = NoteEntity(id, songId, text, createdAt, updatedAt, isSample)

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val releaseDate: String,
    val artColor: Long,
    val artUrl: String?,
    val source: String,
    val spotifyTrackId: String?,
    val externalUrl: String?,
    val albumId: String,
    val addedAt: Long?
) {
    fun domain() = Song(id, title, artist, album, durationMs, releaseDate, artColor, artUrl,
        MusicSource.entries.firstOrNull { it.name == source } ?: MusicSource.Demo,
        spotifyTrackId, externalUrl, albumId, addedAt)
}

fun Song.entity() = SongEntity(id, title, artist, album, durationMs, releaseDate, artColor,
    artUrl, source.name, spotifyTrackId, externalUrl, albumId, addedAt)

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val songId: String, val createdAt: Long)

@Entity(tableName = "app_flags")
data class AppFlag(@PrimaryKey val name: String)

data class EntryWithSong(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "songId", entityColumn = "id") val song: SongEntity?
) {
    fun domain() = DiaryEntry(note.domain(), song?.domain())
}
