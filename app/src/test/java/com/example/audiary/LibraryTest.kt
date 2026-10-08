package com.example.audiary

import com.example.audiary.data.FakeMusicRepository
import com.example.audiary.library.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryTest {
    @Test fun searchIncludesAlbumArtistAndFavoritesRemainSeparate() = runBlocking {
        val songs = FakeMusicRepository().getSongs()
        assertEquals(2, filterLibrary(songs, "blonde", emptySet(), false, LibrarySort.Title).size)
        assertEquals(2, filterLibrary(songs, "frank OCEAN", emptySet(), false, LibrarySort.Title).size)
        assertEquals(listOf("demo:nights"), filterLibrary(songs, "", setOf("demo:nights"), true, LibrarySort.Title).map { it.id })
        assertTrue(songs.all { it.id.startsWith("demo:") })
        assertEquals(songs.size, songs.map { it.id }.distinct().size)
    }
}
