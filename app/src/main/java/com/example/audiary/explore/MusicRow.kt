package com.example.audiary.explore

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.audiary.model.Song
import com.example.audiary.ui.Cover
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

private const val RESUME_DELAY_MS = 900L

@Composable fun MusicRow(
    songs: List<Song>, tileSize: Dp, gap: Dp, speedDpPerSec: Int,
    rightward: Boolean, startOffset: Int, onSongClick: (String) -> Unit,
    autoMove: Boolean = true, captionHeight: Dp = 30.dp, rowIndex: Int = 0,
    listState: LazyListState? = null
) {
    if (songs.isEmpty()) return
    val size = songs.size
    val middle = Int.MAX_VALUE / 2
    val state = listState ?: rememberLazyListState(initialFirstVisibleItemIndex = middle - middle % size + startOffset)
    var touched by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    val speed = with(LocalDensity.current) { speedDpPerSec.dp.toPx() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(speed, rightward, lifecycle, autoMove) {
        if (!autoMove) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(.05f)
                last = now
                val uptime = SystemClock.uptimeMillis()
                if (touched || state.isScrollInProgress) lastInteraction = uptime
                else if (uptime - lastInteraction >= RESUME_DELAY_MS) {
                    try { state.scrollBy((if (rightward) -speed else speed) * dt) }
                    catch (e: CancellationException) {
                        if (!currentCoroutineContext().isActive) throw e
                        lastInteraction = SystemClock.uptimeMillis()
                    }
                }
            }
        }
    }
    LazyRow(state = state, modifier = Modifier.fillMaxWidth().height(tileSize + captionHeight)
        .testTag("music-row-$rowIndex").pointerInput(Unit) {
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        touched = event.changes.any { it.pressed }
                        lastInteraction = SystemClock.uptimeMillis()
                    }
                }
            } finally { touched = false; lastInteraction = SystemClock.uptimeMillis() }
        }, horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(horizontal = 24.dp)) {
        items(count = Int.MAX_VALUE, key = { it }, contentType = { "song" }) { index ->
            val song = songs[index % size]
            Column(Modifier.width(tileSize).clickable(onClickLabel = "Open song") { onSongClick(song.id) }) {
                Cover(song, Modifier.size(tileSize))
                Spacer(Modifier.height(3.dp))
                Text(song.title, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
