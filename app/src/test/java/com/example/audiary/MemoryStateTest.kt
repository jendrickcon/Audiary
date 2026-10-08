package com.example.audiary

import androidx.lifecycle.SavedStateHandle
import com.example.audiary.data.*
import com.example.audiary.model.*
import com.example.audiary.song.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryStateTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class Diary : DiaryRepository {
        var fail = false
        var writes = 0
        var updated: String? = null
        val notes = MutableStateFlow<List<Note>>(emptyList())
        override fun getNotesForSong(songId: String) = notes
        override fun getAllEntries() = notes.map { list -> list.map { DiaryEntry(it, null) } }
        override suspend fun addNote(note: Note) {
            writes++
            delay(100)
            if (fail) error("Disk unavailable")
            notes.value += note
        }
        override suspend fun updateNote(id: String, text: String, updatedAt: Long) { updated = text }
        override suspend fun deleteNote(id: String) {}
    }

    @Test fun failedSaveRetainsDraftAndRetryClosesOnlyAfterSuccess() = runTest(dispatcher) {
        val diary = Diary().apply { fail = true }
        val saved = SavedStateHandle()
        val vm = NoteEditorViewModel(diary, saved)
        vm.create("demo:nights")
        vm.change("  A meaningful memory  ")
        vm.save()
        assertTrue(vm.state.value.open)
        assertTrue(vm.state.value.saving)
        vm.save()
        advanceUntilIdle()
        assertEquals(1, diary.writes)
        assertEquals("  A meaningful memory  ", vm.state.value.draft)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.open)
        diary.fail = false
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.state.value.open)
        assertEquals(1, diary.notes.value.size)
        assertEquals("A meaningful memory", diary.notes.value.single().text)
    }

    @Test fun editorRestoresDraftAndUpdatesExistingMemory() = runTest(dispatcher) {
        val diary = Diary()
        val saved = SavedStateHandle()
        val first = NoteEditorViewModel(diary, saved)
        first.edit(Note("existing", "demo:nights", "Before", 100))
        first.change("After")
        val restored = NoteEditorViewModel(diary, saved)
        assertEquals("After", restored.state.value.draft)
        assertTrue(restored.state.value.editing)
        restored.save()
        advanceUntilIdle()
        assertEquals("After", diary.updated)
        assertEquals(0, diary.writes)
    }

    @Test fun blankDraftCannotSave() = runTest(dispatcher) {
        val diary = Diary()
        val vm = NoteEditorViewModel(diary, SavedStateHandle())
        vm.create("demo:nights")
        vm.change("  \n ")
        vm.save()
        advanceUntilIdle()
        assertEquals(0, diary.writes)
    }

    @Test fun failedMusicLookupDoesNotHideExistingMemories() = runTest(dispatcher) {
        val diary = Diary()
        diary.notes.value = listOf(Note("n", "missing", "Keep me", 1))
        val music = object : MusicRepository {
            override suspend fun getSongs(): List<Song> = error("Offline")
            override suspend fun getSong(id: String): Song? = error("Offline")
        }
        val vm = SongViewModel("missing", music, diary)
        runCurrent()
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.error)
        assertEquals("Keep me", vm.state.value.notes.single().text)
    }
}
