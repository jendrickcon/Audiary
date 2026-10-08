package com.example.audiary.explore

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.example.audiary.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiary.ui.*
import com.example.audiary.ui.theme.Space

import android.os.SystemClock
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExploreScreen(vm: ExploreViewModel, onSongClick: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var paused by rememberSaveable { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var cylinderMode by rememberSaveable { mutableStateOf(false) }
    var isSphericalMode by rememberSaveable { mutableStateOf(false) }
    val cylinderProgress by animateFloatAsState(
        targetValue = if (cylinderMode) 1f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "cylinderProgress"
    )
    val motion = motionAllowed()
    val rushState = remember { ShuffleRushState() }
    val coroutineScope = rememberCoroutineScope()

    val middle = Int.MAX_VALUE / 2
    val rowStates = remember(state.source) {
        List(ROW_COUNT) { index ->
            val songs = state.rows.getOrNull(index).orEmpty()
            val size = songs.size.coerceAtLeast(1)
            val startOffset = index * 3
            LazyListState(firstVisibleItemIndex = middle - middle % size + startOffset)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = "Audiary Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(Space.small))
                    Text("Audiary", style = MaterialTheme.typography.displaySmall)
                }
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

        // Listening Room Header & Source Selector & 3D Toggle
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 3D View Toggle Button
                Surface(
                    onClick = { cylinderMode = !cylinderMode },
                    shape = MaterialTheme.shapes.small,
                    color = if (cylinderMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = Space.small, vertical = Space.tiny),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ViewInAr,
                            null,
                            Modifier.size(14.dp),
                            tint = if (cylinderMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            if (cylinderMode) "3D Active" else "3D Cylinder",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (cylinderMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

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
        }

        // Five animated rows (Listening Room) with Pinch-to-Cylinder and 3D Rotation
        Box(
            Modifier.weight(1f)
        ) {
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
                    val gap = if (cylinderProgress > 0.5f) 4.dp else Space.small
                    val tile = minOf(
                        maxWidth * (0.28f + 0.04f * cylinderProgress),
                        (maxHeight - gap * 4) / ROW_COUNT - if (cylinderProgress > 0.35f) 0.dp else captionHeight
                    ).coerceAtLeast(56.dp)

                    Column(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(cylinderMode) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (!cylinderMode && zoom < 0.88f) {
                                        cylinderMode = true
                                    } else if (cylinderMode && zoom > 1.15f) {
                                        cylinderMode = false
                                    } else if (cylinderMode) {
                                        // Synchronous horizontal drag rotation of all 5 rows in 3D mode
                                        coroutineScope.launch {
                                            rowStates.forEach { it.scrollBy(-pan.x * 1.3f) }
                                        }
                                    }
                                }
                            },
                        verticalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        state.rows.forEachIndexed { index, songs ->
                            MusicRow(
                                songs = songs,
                                tileSize = tile,
                                gap = if (cylinderProgress > 0.5f) Space.tiny else Space.gap,
                                speedDpPerSec = listOf(12, 19, 15, 23, 17)[index],
                                rightward = index % 2 == 0,
                                startOffset = index * 3,
                                onSongClick = onSongClick,
                                autoMove = motion && !paused,
                                captionHeight = captionHeight,
                                rowIndex = index,
                                listState = rowStates.getOrNull(index),
                                speedMultiplier = { rushState.speedMultipliers[index].floatValue },
                                motionBlur = { rushState.blurAmounts[index].floatValue },
                                showCaptions = cylinderProgress < 0.35f,
                                cylinderProgress = cylinderProgress,
                                isSpherical = isSphericalMode
                            )
                        }
                    }

                    // Floating 3D Controls overlay in Cylinder Mode
                    if (cylinderProgress > 0.05f) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .graphicsLayer { alpha = cylinderProgress },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                                tonalElevation = 4.dp,
                                shadowElevation = 6.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Cylinder mode chip
                                    FilterChip(
                                        selected = !isSphericalMode,
                                        onClick = { isSphericalMode = false },
                                        label = { Text("Cylinder") },
                                        leadingIcon = { Icon(Icons.Outlined.Layers, null, Modifier.size(16.dp)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )

                                    // Globe mode chip
                                    FilterChip(
                                        selected = isSphericalMode,
                                        onClick = { isSphericalMode = true },
                                        label = { Text("Globe") },
                                        leadingIcon = { Icon(Icons.Outlined.Public, null, Modifier.size(16.dp)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )

                                    FilledTonalIconButton(
                                        onClick = { cylinderMode = false },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Outlined.Close, "Exit 3D View", Modifier.size(16.dp))
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Swipe to rotate · Pinch out or tap ✕ to exit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
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
                onClick = {
                    if (rushState.isRushing) return@FilledTonalButton
                    if (!motion) {
                        vm.shuffle()
                    } else {
                        coroutineScope.launch {
                            rushState.executeRush(onMidRushRemix = { vm.shuffle() })
                        }
                    }
                },
                enabled = state.rows.isNotEmpty() && !rushState.isRushing,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Outlined.Shuffle,
                    null,
                    modifier = Modifier.size(18.dp).graphicsLayer {
                        if (rushState.isRushing) {
                            rotationZ = rushState.rushProgress * 360f
                        }
                    }
                )
                Spacer(Modifier.width(Space.small))
                Text(
                    if (rushState.isRushing) "Shuffling..."
                    else if (state.songCount > 30) "Shuffle ${state.songCount} songs"
                    else "Shuffle the mood",
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

class ShuffleRushState {
    var isRushing by mutableStateOf(false)
        private set

    var rushProgress by mutableFloatStateOf(0f)
        private set

    val speedMultipliers = List(ROW_COUNT) { mutableFloatStateOf(1f) }
    val blurAmounts = List(ROW_COUNT) { mutableFloatStateOf(0f) }

    suspend fun executeRush(onMidRushRemix: () -> Unit) {
        if (isRushing) return
        isRushing = true
        rushProgress = 0f
        try {
            coroutineScope {
                val totalDurationMs = 1600L
                val remixTimeMs = 650L // Peak velocity remix
                var remixed = false
                val startTime = SystemClock.uptimeMillis()

                while (true) {
                    val now = SystemClock.uptimeMillis()
                    val elapsed = now - startTime
                    if (elapsed >= totalDurationMs) break

                    rushProgress = (elapsed.toFloat() / totalDurationMs).coerceIn(0f, 1f)

                    // Stage 2 midpoint: Swap songs at maximum conveyor velocity
                    if (!remixed && elapsed >= remixTimeMs) {
                        remixed = true
                        onMidRushRemix()
                    }

                    // Compute velocity and blur per row with staggered onset
                    for (i in 0 until ROW_COUNT) {
                        val rowDelay = i * 28L // Stagger acceleration
                        val rowElapsed = (elapsed - rowDelay).coerceAtLeast(0L)
                        val (mult, blur) = evaluateRushCurve(rowElapsed, totalDurationMs - rowDelay, i)
                        speedMultipliers[i].floatValue = mult
                        blurAmounts[i].floatValue = blur
                    }

                    withFrameNanos { }
                }

                if (!remixed) onMidRushRemix()
            }
        } finally {
            for (i in 0 until ROW_COUNT) {
                speedMultipliers[i].floatValue = 1f
                blurAmounts[i].floatValue = 0f
            }
            rushProgress = 1f
            isRushing = false
        }
    }

    private fun evaluateRushCurve(elapsed: Long, duration: Long, rowIndex: Int): Pair<Float, Float> {
        val peakMultiplier = listOf(11.5f, 13.0f, 10.5f, 14.0f, 12.0f)[rowIndex % ROW_COUNT]
        val accelEnd = 300f
        val peakEnd = 950f
        val total = duration.toFloat().coerceAtLeast(1400f)

        return when {
            elapsed <= 0 -> 1f to 0f
            elapsed < accelEnd -> {
                // Stage 1: Sharp acceleration (0..300ms)
                val t = elapsed / accelEnd
                val eased = t * t
                val mult = 1f + (peakMultiplier - 1f) * eased
                val blur = (t * 0.85f).coerceIn(0f, 1f)
                mult to blur
            }
            elapsed < peakEnd -> {
                // Stage 2: Full Shuffle Rush conveyor belt (300..950ms)
                val progress = (elapsed - accelEnd) / (peakEnd - accelEnd)
                val wave = kotlin.math.sin(progress * Math.PI.toFloat() * 2f) * 0.8f
                val mult = peakMultiplier + wave
                mult to 1f
            }
            elapsed < total -> {
                // Stage 3: Deceleration to normal drift (950..1600ms) with cubic easing
                val t = (elapsed - peakEnd) / (total - peakEnd)
                val inv = 1f - t
                val eased = 1f - (inv * inv * inv)
                val mult = peakMultiplier - (peakMultiplier - 1f) * eased
                val blur = (1f - eased).coerceIn(0f, 1f)
                mult to blur
            }
            else -> 1f to 0f
        }
    }
}
