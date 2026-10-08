package com.example.audiary.data

import androidx.room.withTransaction
import com.example.audiary.data.local.*
import com.example.audiary.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** The flag and seed are committed together, including when two screens open at once. */
class LocalSeed(private val db: AudiaryDatabase) {
    suspend fun ensure() = db.withTransaction {
        if (!db.dao().hasFlag("demo-v1")) {
            db.dao().storeSongs(FakeMusicRepository().getSongs().map { it.entity() })
            fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            db.dao().seedNotes(listOf(
                Note("sample:n1", "demo:nights", "I first heard this during finals week. It felt like the whole city was asleep.", date(2025, 3, 12), isSample = true),
                Note("sample:n2", "demo:nights", "Played this on the bus home after the long day. Still love the ending.", date(2026, 5, 20), isSample = true),
                Note("sample:n3", "demo:nights", "Still somehow feels the same.", date(2026, 10, 6), isSample = true),
                Note("sample:n4", "demo:505", "Played this again after a really long time. It took me right back.", date(2026, 8, 2), isSample = true),
                Note("sample:n5", "demo:ivy", "This takes me back to that summer road trip with the windows down.", date(2026, 9, 18), isSample = true)
            ).map { it.entity() })
            db.dao().flag(AppFlag("demo-v1"))
        }
    }
}

class RoomDiaryRepository(private val db: AudiaryDatabase, private val seed: LocalSeed) : DiaryRepository {
    override fun getNotesForSong(songId: String): Flow<List<Note>> = flow {
        seed.ensure()
        emitAll(db.dao().notes(songId).map { notes -> notes.map { it.domain() } })
    }
    override fun getAllEntries(): Flow<List<DiaryEntry>> = flow {
        seed.ensure()
        emitAll(db.dao().entries().map { entries -> entries.map { it.domain() } })
    }
    override suspend fun addNote(note: Note) {
        require(note.text.isNotBlank())
        seed.ensure()
        db.withTransaction {
            val existing = db.dao().note(note.id)
            if (existing == null) db.dao().insertNote(note.copy(text = note.text.trim()).entity())
            else check(existing.songId == note.songId && existing.text == note.text.trim()) {
                "A different memory already uses this ID."
            }
        }
    }
    override suspend fun updateNote(id: String, text: String, updatedAt: Long) {
        require(text.isNotBlank())
        check(db.dao().updateNote(id, text.trim(), updatedAt) == 1) { "This memory no longer exists." }
    }
    override suspend fun deleteNote(id: String) = db.dao().deleteNote(id)
}

interface FavoritesRepository {
    fun observe(): Flow<Set<String>>
    fun songs(): Flow<List<Song>>
    suspend fun setFavorite(song: Song, favorite: Boolean)
}

class RoomFavoritesRepository(private val db: AudiaryDatabase) : FavoritesRepository {
    override fun observe() = db.dao().favorites().map { it.toSet() }
    override fun songs() = db.dao().favoriteSongs().map { songs -> songs.map { it.domain() } }
    override suspend fun setFavorite(song: Song, favorite: Boolean) = db.withTransaction {
        if (favorite) {
            db.dao().storeSongs(listOf(song.entity()))
            db.dao().favorite(FavoriteEntity(song.id, System.currentTimeMillis()))
        } else db.dao().unfavorite(song.id)
    }
}

class LocalMusicRepository(private val db: AudiaryDatabase, private val seed: LocalSeed) : MusicRepository {
    override suspend fun getSongs(): List<Song> {
        seed.ensure()
        return db.dao().songs(MusicSource.Demo.name).map { it.domain() }
    }
    override suspend fun getSong(id: String): Song? {
        seed.ensure()
        return db.dao().song(id)?.domain()
    }
}
