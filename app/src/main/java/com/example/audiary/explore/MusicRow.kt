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

import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

private const val RESUME_DELAY_MS = 900L

@Composable fun MusicRow(
    songs: List<Song>, tileSize: Dp, gap: Dp, speedDpPerSec: Int,
    rightward: Boolean, startOffset: Int, onSongClick: (String) -> Unit,
    autoMove: Boolean = true, captionHeight: Dp = 30.dp, rowIndex: Int = 0,
    listState: LazyListState? = null,
    speedMultiplier: () -> Float = { 1f },
    motionBlur: () -> Float = { 0f },
    showCaptions: Boolean = true,
    cylinderProgress: Float = 0f,
    isSpherical: Boolean = false
) {
    if (songs.isEmpty()) return
    val size = songs.size
    val middle = Int.MAX_VALUE / 2
    val state = listState ?: rememberLazyListState(initialFirstVisibleItemIndex = middle - middle % size + startOffset)
    var touched by remember { mutableStateOf(false) }
    var isAutoScrolling by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val speed = with(density) { speedDpPerSec.dp.toPx() }
    val screenWidthPx = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state, speed, rightward, lifecycle, autoMove) {
        if (!autoMove) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceAtMost(.05f)
                last = now
                val uptime = SystemClock.uptimeMillis()
                if (touched || (!isAutoScrolling && state.isScrollInProgress)) {
                    lastInteraction = uptime
                } else if (uptime - lastInteraction >= RESUME_DELAY_MS) {
                    val multiplier = speedMultiplier()
                    val effectiveSpeed = speed * multiplier
                    try {
                        isAutoScrolling = true
                        state.scrollBy((if (rightward) -effectiveSpeed else effectiveSpeed) * dt)
                    } catch (e: CancellationException) {
                        if (!currentCoroutineContext().isActive) throw e
                        lastInteraction = SystemClock.uptimeMillis()
                    } finally {
                        isAutoScrolling = false
                    }
                }
            }
        }
    }
    LazyRow(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .height(tileSize + if (showCaptions && cylinderProgress < 0.5f) captionHeight else 0.dp)
            .testTag("music-row-$rowIndex")
            .graphicsLayer {
                val blur = motionBlur()
                if (blur > 0.01f) {
                    // Subtle horizontal speed elongation and softness
                    scaleX = 1f + (blur * 0.10f)
                    alpha = (1f - (blur * 0.04f)).coerceIn(0.85f, 1f)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        val blurPx = blur * 10f
                        if (blurPx > 0.5f) {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                blurPx,
                                0.1f,
                                android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        } else {
                            renderEffect = null
                        }
                    }
                } else {
                    scaleX = 1f
                    alpha = 1f
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        renderEffect = null
                    }
                }

                if (cylinderProgress > 0.01f) {
                    cameraDistance = 24f * density.density
                    if (isSpherical) {
                        val rowLatDeg = when (rowIndex) {
                            0 -> 14f
                            1 -> 7f
                            2 -> 0f
                            3 -> -7f
                            4 -> -14f
                            else -> 0f
                        } * cylinderProgress
                        rotationX = rowLatDeg
                    }
                }
            }
            .pointerInput(Unit) {
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            touched = event.changes.any { it.pressed }
                            lastInteraction = SystemClock.uptimeMillis()
                        }
                    }
                } finally { touched = false; lastInteraction = SystemClock.uptimeMillis() }
            },
        horizontalArrangement = Arrangement.spacedBy(gap),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        items(count = Int.MAX_VALUE, key = { it }, contentType = { "song" }) { index ->
            val song = songs[index % size]
            val itemInfo = remember(state.layoutInfo) {
                if (cylinderProgress > 0.01f) {
                    state.layoutInfo.visibleItemsInfo.find { it.index == index }
                } else null
            }
            val captionAlpha = if (showCaptions) (1f - cylinderProgress * 2.5f).coerceIn(0f, 1f) else 0f

            Column(
                Modifier
                    .width(tileSize)
                    .graphicsLayer {
                        if (cylinderProgress > 0.01f && itemInfo != null) {
                            cameraDistance = 22f * density.density
                            val viewportWidth = state.layoutInfo.viewportSize.width.takeIf { it > 0 } ?: screenWidthPx.toInt()
                            val viewportCenter = viewportWidth / 2f
                            val itemCenter = itemInfo.offset + itemInfo.size / 2f
                            val distFromCenter = itemCenter - viewportCenter
                            val relX = (distFromCenter / (viewportCenter * 0.85f)).coerceIn(-1.5f, 1.5f)

                            val angleDeg = (relX * 44f).coerceIn(-68f, 68f) * cylinderProgress
                            rotationY = -angleDeg
                            val rad = Math.toRadians(angleDeg.toDouble())
                            val depthScale = 1f - ((1.0 - Math.cos(rad)) * 0.28 * cylinderProgress).toFloat()

                            if (isSpherical) {
                                val latDeg = when (rowIndex) {
                                    0 -> 18f
                                    1 -> 9f
                                    2 -> 0f
                                    3 -> -9f
                                    4 -> -18f
                                    else -> 0f
                                } * cylinderProgress
                                rotationX = latDeg
                                val latScale = when (rowIndex) {
                                    0 -> 0.88f
                                    1 -> 0.95f
                                    2 -> 1.0f
                                    3 -> 0.95f
                                    4 -> 0.88f
                                    else -> 1.0f
                                }
                                val s = 1f - (1f - latScale) * cylinderProgress
                                scaleX = s * depthScale
                                scaleY = s * depthScale
                            } else {
                                scaleX = depthScale
                                scaleY = depthScale
                            }

                            val edgeDim = (kotlin.math.abs(relX) * 0.28f * cylinderProgress).coerceIn(0f, 0.4f)
                            alpha = (1f - edgeDim).coerceIn(0.6f, 1f)
                        }
                    }
                    .clickable(onClickLabel = "Open song") { onSongClick(song.id) }
            ) {
                Cover(song, Modifier.size(tileSize))
                if (showCaptions && captionAlpha > 0.02f && captionHeight > 0.dp) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        song.title,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = captionAlpha }
                    )
                    Text(
                        song.artist,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { alpha = captionAlpha }
                    )
                }
            }
        }
    }
}
