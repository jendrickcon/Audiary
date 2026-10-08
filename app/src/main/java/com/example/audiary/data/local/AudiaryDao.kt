package com.example.audiary.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiaryDao {
    @Query("SELECT * FROM notes WHERE songId = :songId ORDER BY createdAt DESC, id DESC")
    fun notes(songId: String): Flow<List<NoteEntity>>
    @Transaction
    @Query("SELECT * FROM notes ORDER BY createdAt DESC, id DESC")
    fun entries(): Flow<List<EntryWithSong>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNote(note: NoteEntity)
    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun note(id: String): NoteEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seedNotes(notes: List<NoteEntity>)
    @Query("UPDATE notes SET text = :text, updatedAt = :updatedAt, isSample = 0 WHERE id = :id")
    suspend fun updateNote(id: String, text: String, updatedAt: Long): Int
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: String)
    @Upsert
    suspend fun storeSongs(songs: List<SongEntity>)
    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun song(id: String): SongEntity?
    @Query("SELECT * FROM songs WHERE source = :source ORDER BY title COLLATE NOCASE")
    suspend fun songs(source: String): List<SongEntity>
    @Query("SELECT songId FROM favorites")
    fun favorites(): Flow<List<String>>
    @Query("SELECT songs.* FROM songs INNER JOIN favorites ON songs.id = favorites.songId ORDER BY favorites.createdAt DESC")
    fun favoriteSongs(): Flow<List<SongEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun favorite(favorite: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE songId = :id")
    suspend fun unfavorite(id: String)
    @Query("SELECT EXISTS(SELECT 1 FROM app_flags WHERE name = :name)")
    suspend fun hasFlag(name: String): Boolean
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun flag(flag: AppFlag)
}
