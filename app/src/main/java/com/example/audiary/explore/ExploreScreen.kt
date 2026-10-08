package com.example.audiary.explore

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiary.ui.*
import com.example.audiary.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExploreScreen(vm: ExploreViewModel, onSongClick: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var paused by rememberSaveable { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    val motion = motionAllowed()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Audiary", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Your music. Your memories.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(onClick = { paused = !paused }, enabled = motion) {
                Icon(
                    if (paused || !motion) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    if (paused) "Resume moving rows" else "Pause moving rows"
                )
            }
        }

        // Listening Room Header & Source Selector
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page).padding(bottom = Space.small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "THE LISTENING ROOM",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // Interactive source selector button
            Surface(
                onClick = { showSourceSheet = true },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = Space.small, vertical = Space.tiny),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val icon = when (state.source) {
                        is DiscoverySource.AllSavedSongs -> Icons.Outlined.FavoriteBorder
                        is DiscoverySource.SpotifyPlaylistSource -> Icons.Outlined.QueueMusic
                        is DiscoverySource.ArtistSource -> Icons.Outlined.Person
                        is DiscoverySource.Demo -> Icons.Outlined.Album
                    }
                    Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        state.source.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Outlined.ArrowDropDown, "Change source", Modifier.size(16.dp))
                }
            }
        }

        // Five animated rows (Listening Room)
        Box(Modifier.weight(1f)) {
            when {
                state.isLoading -> Centered { CircularProgressIndicator() }
                state.error != null -> EmptyState("A quiet moment.", state.error.orEmpty(), Modifier.align(Alignment.Center)) {
                    Button(onClick = vm::load) { Text("Try again") }
                }
                state.rows.isEmpty() -> {
                    EmptyState(
                        "No songs on the wall.",
                        "Your available tracks from ${state.source.displayName} will appear here.",
                        Modifier.align(Alignment.Center)
                    ) {
                        Button(onClick = { vm.selectSource(DiscoverySource.Demo) }) {
                            Text("Switch to Demo Collection")
                        }
                    }
                }
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val captionHeight = 28.dp * LocalDensity.current.fontScale
                    val gap = Space.small
                    val tile = minOf(maxWidth * .28f, (maxHeight - gap * 4) / ROW_COUNT - captionHeight).coerceAtLeast(56.dp)
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        state.rows.forEachIndexed { index, songs ->
                            AnimatedContent(
                                targetState = songs,
                                transitionSpec = {
                                    fadeIn(tween(if (motion) 240 else 0, if (motion) index * 25 else 0)) togetherWith
                                        fadeOut(tween(if (motion) 120 else 0))
                                },
                                label = "wall-row-$index"
                            ) { row ->
                                MusicRow(
                                    row, tile, Space.gap, listOf(12, 19, 15, 23, 17)[index],
                                    index % 2 == 0, index * 3, onSongClick, autoMove = motion && !paused,
                                    captionHeight = captionHeight, rowIndex = index
                                )
                            }
                        }
                    }
                }
            }
        }

        // Shuffle button and instructions
        Column(
            Modifier.fillMaxWidth().padding(vertical = Space.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalButton(
                onClick = vm::shuffle,
                enabled = state.rows.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Outlined.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Space.small))
                Text(
                    if (state.songCount > 30) "Shuffle ${state.songCount} songs" else "Shuffle the mood",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Hold a row to pause · Tap a song to remember",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Modal Bottom Sheet for Music Source Selection
    if (showSourceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSourceSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Space.page).padding(bottom = Space.section)
            ) {
                Text(
                    "Listening Room Source",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Select which music collection flows through the five-row discovery wall.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.medium))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.small)
                ) {
                    if (state.isSpotifyConnected) {
                        item {
                            SourceOptionItem(
                                title = "All Saved Songs",
                                subtitle = "Your entire saved Spotify music library",
                                icon = Icons.Outlined.Favorite,
                                isSelected = state.source is DiscoverySource.AllSavedSongs,
                                onClick = {
                                    vm.selectSource(DiscoverySource.AllSavedSongs)
                                    showSourceSheet = false
                                }
                            )
                        }

                        if (state.availablePlaylists.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(Space.small))
                                Text(
                                    "SPOTIFY PLAYLISTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            items(state.availablePlaylists, key = { it.id }) { playlist ->
                                val isSelected = state.source is DiscoverySource.SpotifyPlaylistSource &&
                                    (state.source as DiscoverySource.SpotifyPlaylistSource).playlistId == playlist.id
                                val subtitleText = "${playlist.formattedTrackCount()} · by ${playlist.ownerName}" +
                                    if (!playlist.isOwnerOrCollaborator) " · Owner access required" else ""
                                SourceOptionItem(
                                    title = playlist.name,
                                    subtitle = subtitleText,
                                    icon = Icons.Outlined.QueueMusic,
                                    isSelected = isSelected,
                                    onClick = {
                                        vm.selectSource(
                                            DiscoverySource.SpotifyPlaylistSource(
                                                playlist.id,
                                                playlist.name,
                                                playlist.artUrl
                                            )
                                        )
                                        showSourceSheet = false
                                    }
                                )
                            }
                        }
                    }

                    item {
                        if (state.isSpotifyConnected) Spacer(Modifier.height(Space.small))
                        SourceOptionItem(
                            title = "Demo Collection",
                            subtitle = "Local offline sample music catalog",
                            icon = Icons.Outlined.Album,
                            isSelected = state.source is DiscoverySource.Demo,
                            onClick = {
                                vm.selectSource(DiscoverySource.Demo)
                                showSourceSheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceOptionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = Space.medium, vertical = Space.gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Space.medium))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Outlined.Check,
                    "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
