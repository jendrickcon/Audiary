package com.example.audiary.spotify

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.audiary.ui.theme.Space

/**
 * Compact mini-player displayed persistently above bottom navigation across tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    playbackState: SpotifyPlaybackState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenSong: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AnimatedVisibility(
        visible = playbackState.hasTrack,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable { isExpanded = true }
        ) {
            // Track progress bar on top edge of mini player
            LinearProgressIndicator(
                progress = { playbackState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album artwork thumbnail
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        playbackState.coverBitmap != null -> {
                            Image(
                                bitmap = playbackState.coverBitmap.asImageBitmap(),
                                contentDescription = playbackState.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        !playbackState.coverUrl.isNullOrBlank() -> {
                            AsyncImage(
                                model = playbackState.coverUrl,
                                contentDescription = playbackState.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Outlined.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Title and Artist
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = playbackState.title.ifBlank { "Spotify Playback" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playbackState.artist.ifBlank {
                            if (playbackState.isConnecting) "Connecting to Spotify..." else "Spotify App Remote"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                IconButton(
                    onClick = onSkipPrevious,
                    enabled = playbackState.canSkipPrev,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = "Previous track",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    enabled = playbackState.canSkipNext,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "Next track",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Expanded Player Bottom Sheet
    if (isExpanded) {
        ModalBottomSheet(
            onDismissRequest = { isExpanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.page, vertical = Space.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Artwork
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        playbackState.coverBitmap != null -> {
                            Image(
                                bitmap = playbackState.coverBitmap.asImageBitmap(),
                                contentDescription = playbackState.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        !playbackState.coverUrl.isNullOrBlank() -> {
                            AsyncImage(
                                model = playbackState.coverUrl,
                                contentDescription = playbackState.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Outlined.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Space.page))

                // Title, Artist, Album
                Text(
                    text = playbackState.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = playbackState.artist.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playbackState.album.isNotBlank()) {
                    Text(
                        text = playbackState.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(Space.medium))

                // Seek Slider & Timestamps
                Slider(
                    value = playbackState.progress,
                    onValueChange = { frac ->
                        if (playbackState.trackDurationMs > 0L) {
                            val targetMs = (frac * playbackState.trackDurationMs).toLong()
                            onSeekTo(targetMs)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = playbackState.positionFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = playbackState.durationFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(Space.medium))

                // Full Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        enabled = playbackState.canSkipPrev,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(68.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = onSkipNext,
                        enabled = playbackState.canSkipNext,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.height(Space.page))

                // Navigation & External fallback buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.small)
                ) {
                    val trackId = playbackState.trackId
                    if (!trackId.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                isExpanded = false
                                onOpenSong(trackId)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.EditNote, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(Space.small))
                            Text("Song Memory")
                        }
                    }

                    Button(
                        onClick = {
                            val uri = playbackState.trackUri ?: "spotify:track:$trackId"
                            val controller = (context.applicationContext as? com.example.audiary.AudiaryApp)?.playbackController
                            controller?.openExternal(context, uri)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(Space.small))
                        Text("Spotify App")
                    }
                }

                playbackState.error?.let { err ->
                    Spacer(Modifier.height(Space.small))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(Space.page))
            }
        }
    }
}
