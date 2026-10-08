package com.example.audiary

import com.example.audiary.data.FakeMusicRepository
import com.example.audiary.explore.DiscoverySource
import com.example.audiary.explore.ExploreViewModel
import com.example.audiary.explore.ROW_COUNT
import com.example.audiary.model.MusicSource
import com.example.audiary.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun arrangeDistributesAcrossFiveRowsEvenly() = runTest(dispatcher) {
        val songs = FakeMusicRepository().getSongs()
        val vm = ExploreViewModel(FakeMusicRepository())
        val rows = vm.arrange(songs)

        assertEquals(ROW_COUNT, rows.size)
        rows.forEach { row ->
            assertTrue(row.isNotEmpty())
            assertTrue(row.size <= 16)
        }
    }

    @Test
    fun arrangeHandlesEmptyListGracefully() {
        val vm = ExploreViewModel(FakeMusicRepository())
        val rows = vm.arrange(emptyList())
        assertTrue(rows.isEmpty())
    }

    @Test
    fun arrangeSingleSongRepeatsWithoutIndexOutOfBounds() {
        val vm = ExploreViewModel(FakeMusicRepository())
        val single = listOf(
            Song(
                id = "s1",
                title = "Song 1",
                artist = "Artist",
                album = "Album",
                durationMs = 180000L,
                releaseDate = "2024",
                artColor = 0xFF121212,
                source = MusicSource.Spotify
            )
        )
        val rows = vm.arrange(single)
        assertEquals(ROW_COUNT, rows.size)
        rows.forEach { row ->
            assertTrue(row.isNotEmpty())
            row.forEach { assertEquals("s1", it.id) }
        }
    }

    @Test
    fun sourceSelectionFallsBackToDemoWhenSpotifyNotConnected() {
        val vm = ExploreViewModel(FakeMusicRepository())
        vm.selectSource(DiscoverySource.AllSavedSongs)
        // Since spotifyAuth is null / disconnected, it must fallback to Demo
        assertEquals(DiscoverySource.Demo, vm.state.value.source)
    }

    @Test
    fun shuffleKeepsRowCountConsistent() = runTest(dispatcher) {
        val vm = ExploreViewModel(FakeMusicRepository())
        advanceUntilIdle() // let initial init load() complete
        assertEquals(ROW_COUNT, vm.state.value.rows.size)
        vm.shuffle()
        assertEquals(ROW_COUNT, vm.state.value.rows.size)
    }
}

