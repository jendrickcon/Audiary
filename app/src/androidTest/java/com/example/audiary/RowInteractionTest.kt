package com.example.audiary

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.audiary.explore.MusicRow
import com.example.audiary.model.Song
import com.example.audiary.ui.theme.AudiaryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RowInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stationaryTouchStopsDriftAndReleaseResumesAfterDelay() {
        compose.mainClock.autoAdvance = false
        lateinit var list: LazyListState
        val song = Song("demo:test", "Test song", "Artist", "Album", 1000, "2026", 0xFF557755)
        compose.setContent {
            val state = rememberLazyListState(initialFirstVisibleItemIndex = 1000)
            SideEffect { list = state }
            AudiaryTheme {
                MusicRow(listOf(song), 80.dp, 8.dp, 40, false, 0, {}, listState = state)
            }
        }
        compose.mainClock.advanceTimeBy(64)
        fun position(): Int { var value = 0; compose.runOnIdle { value = list.firstVisibleItemIndex * 10000 + list.firstVisibleItemScrollOffset }; return value }
        val before = position()
        compose.mainClock.advanceTimeBy(400)
        assertNotEquals(before, position())
        compose.onNodeWithTag("music-row-0").performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        val held = position()
        compose.mainClock.advanceTimeBy(400)
        assertEquals("A stationary finger must pause the row", held, position())
        compose.onNodeWithTag("music-row-0").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(100)
        assertEquals("Resume must have a deliberate delay", held, position())
        Thread.sleep(1000)
        compose.mainClock.advanceTimeBy(300)
        assertNotEquals("The row must resume after release", held, position())
    }
}
