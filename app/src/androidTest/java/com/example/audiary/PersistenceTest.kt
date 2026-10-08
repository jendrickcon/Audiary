package com.example.audiary

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.audiary.data.*
import com.example.audiary.data.local.*
import com.example.audiary.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    @Test fun memoriesAndFavoritesSurviveReopenAndSeedDoesNotReturn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "audit-" + UUID.randomUUID() + ".db"
        fun open() = Room.databaseBuilder(context, AudiaryDatabase::class.java, name).build()
        var db = open()
        try {
            var repo = RoomDiaryRepository(db, LocalSeed(db))
            assertEquals(5, repo.getAllEntries().first().size)
            repo.deleteNote("sample:n1")
            repo.addNote(Note("personal", "demo:nights", "My real memory", 500, 500))
            RoomFavoritesRepository(db).setFavorite(FakeMusicRepository().getSongs().first(), true)
            db.close()
            db = open()
            repo = RoomDiaryRepository(db, LocalSeed(db))
            val entries = repo.getAllEntries().first()
            assertFalse(entries.any { it.note.id == "sample:n1" })
            assertEquals("My real memory", entries.first { it.note.id == "personal" }.note.text)
            assertEquals("Nights", entries.first { it.note.id == "personal" }.song?.title)
            assertEquals(setOf("demo:nights"), RoomFavoritesRepository(db).observe().first())
            repo.updateNote("personal", "Edited memory", 900)
            val edited = repo.getAllEntries().first().first { it.note.id == "personal" }.note
            assertEquals(500L, edited.createdAt)
            assertEquals(900L, edited.updatedAt)
            repo.deleteNote("personal")
            assertFalse(repo.getAllEntries().first().any { it.note.id == "personal" })
            repo.addNote(Note("orphan", "spotify:unavailable", "Still mine", 1000))
            assertEquals("Still mine", repo.getAllEntries().first().first { it.note.id == "orphan" }.note.text)
            assertNull(repo.getAllEntries().first().first { it.note.id == "orphan" }.song)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
