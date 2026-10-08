package com.example.audiary.spotify

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiary.explore.DiscoverySource
import com.example.audiary.library.FavoritesViewModel
import com.example.audiary.library.LibrarySort
import com.example.audiary.model.Song
import com.example.audiary.ui.*
import com.example.audiary.ui.theme.Space

enum class ArtistSort(val label: String) {
    NameAsc("A–Z"),
    NameDesc("Z–A"),
    MostSaved("Most saved"),
    FewestSaved("Fewest saved")
}

@Composable
fun ColumnScope.SpotifyCollection(
    vm: SpotifyLibraryViewModel,
    favoritesVm: FavoritesViewModel,
    sessionId: Long,
    onSong: (String) -> Unit,
    onExploreSource: (DiscoverySource) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by favoritesVm.favorites.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Songs") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.Title) }
    var artistSort by rememberSaveable { mutableStateOf(ArtistSort.MostSaved) }
    val context = LocalContext.current

    LaunchedEffect(tab, sessionId) {
        vm.ensureLoaded(tab)
    }

    BackHandler(state.selectedAlbum != null || state.selectedPlaylist != null || state.selectedArtist != null) {
        vm.closeAlbum()
        vm.closePlaylist()
        vm.closeArtist()
    }

    // Detail Views (Album, Playlist, Artist)
    when {
        state.selectedAlbum != null -> {
            AlbumDetailView(
                album = state.selectedAlbum!!,
                songs = state.albumSongs,
                loading = state.loading,
                error = state.error,
                favorites = favorites,
                onSong = onSong,
                onFavorite = { favoritesVm.toggle(it) },
                onBack = { vm.closeAlbum() }
            )
            return@SpotifyCollection
        }
        state.selectedPlaylist != null -> {
            PlaylistDetailView(
                playlist = state.selectedPlaylist!!,
                songs = state.playlistSongs,
                loading = state.loading,
                error = state.playlistError,
                favorites = favorites,
                onSong = onSong,
                onFavorite = { favoritesVm.toggle(it) },
                onExplore = {
                    onExploreSource(
                        DiscoverySource.SpotifyPlaylistSource(
                            playlistId = state.selectedPlaylist!!.id,
                            playlistName = state.selectedPlaylist!!.name,
                            artUrl = state.selectedPlaylist!!.artUrl
                        )
                    )
                },
                onBack = { vm.closePlaylist() }
            )
            return@SpotifyCollection
        }
        state.selectedArtist != null -> {
            ArtistDetailView(
                artist = state.selectedArtist!!,
                favorites = favorites,
                onSong = onSong,
                onFavorite = { favoritesVm.toggle(it) },
                onExplore = {
                    onExploreSource(
                        DiscoverySource.ArtistSource(
                            artistName = state.selectedArtist!!.name,
                            artistId = state.selectedArtist!!.spotifyArtistId
                        )
                    )
                },
                onBack = { vm.closeArtist() }
            )
            return@SpotifyCollection
        }
    }

    // Main 4-Tab Navigation: Songs, Albums, Artists, Playlists
    Row(
        Modifier.padding(horizontal = Space.page).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.small)
    ) {
        listOf("Songs", "Albums", "Artists", "Playlists").forEach { label ->
            FilterChip(
                selected = tab == label,
                onClick = { tab = label; query = "" },
                label = { Text(label) }
            )
        }
    }

    // Search bar
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        placeholder = {
            Text(
                when (tab) {
                    "Artists" -> "Search artists..."
                    "Playlists" -> "Search playlists..."
                    "Albums" -> "Search albums..."
                    else -> if (state.totalTracks > 0) "Search ${state.tracks.size} of ${state.totalTracks} songs..." else "Search songs..."
                }
            )
        },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { query = "" }) {
                    Icon(Icons.Outlined.Close, "Clear search")
                }
            }
        },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.tiny)
    )

    // Secondary Header / Sorting row
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.tiny),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when (tab) {
                "Songs" -> if (state.totalTracks > 0) "${state.totalTracks} saved tracks" else "Your saved tracks"
                "Albums" -> "${state.albums.size} albums"
                "Artists" -> if (state.isArtistIndexComplete) "${state.artists.size} artists in your library" else "${state.artists.size} artists found"
                "Playlists" -> "${state.playlists.size} playlists"
                else -> ""
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (tab == "Songs") {
            TextButton(onClick = { sort = if (sort == LibrarySort.Title) LibrarySort.Artist else LibrarySort.Title }) {
                Text("Sort: ${sort.label}")
            }
            TextButton(onClick = { vm.loadTracks(refresh = true) }) {
                Text("Refresh")
            }
        } else if (tab == "Artists") {
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    Text("Sort: ${artistSort.label}")
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    ArtistSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { artistSort = option; showSortMenu = false }
                        )
                    }
                }
            }
        }
    }

    if (state.loading && !state.loadingMoreTracks) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }

    // Tab content
    when (tab) {
        "Songs" -> {
            CompactSongsTab(
                tracks = state.tracks,
                query = query,
                sort = sort,
                favorites = favorites,
                loadingMore = state.loadingMoreTracks,
                canLoadMore = state.canLoadMoreTracks,
                error = state.error,
                onSong = onSong,
                onFavorite = { favoritesVm.toggle(it) },
                onLoadMore = { vm.loadMoreTracks() },
                onRetry = { vm.retry() }
            )
        }
        "Albums" -> {
            AlbumsTab(
                albums = state.albums,
                query = query,
                sort = sort,
                loading = state.loading,
                onOpen = { vm.openAlbum(it) }
            )
        }
        "Artists" -> {
            ArtistsTab(
                artists = state.artists,
                isIndexing = state.isIndexingArtists,
                indexedCount = state.indexedArtistSongsCount,
                query = query,
                sort = artistSort,
                onOpen = { vm.openArtist(it) },
                onRetryIndexing = { vm.startIndexingArtists() }
            )
        }
        "Playlists" -> {
            PlaylistsTab(
                playlists = state.playlists,
                query = query,
                loading = state.loading,
                error = state.playlistError,
                onOpen = { vm.openPlaylist(it) },
                onRefresh = { vm.loadPlaylists() }
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// COMPACT SONGS TAB (PHASE 5 & 6)
// -------------------------------------------------------------------------------------------------
@Composable
private fun ColumnScope.CompactSongsTab(
    tracks: List<Song>,
    query: String,
    sort: LibrarySort,
    favorites: Set<String>,
    loadingMore: Boolean,
    canLoadMore: Boolean,
    error: String?,
    onSong: (String) -> Unit,
    onFavorite: (Song) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    val filtered = remember(tracks, query, sort) {
        val list = if (query.isBlank()) tracks else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
        when (sort) {
            LibrarySort.Title -> list.sortedBy { it.title.lowercase() }
            LibrarySort.Artist -> list.sortedBy { it.artist.lowercase() }
        }
    }

    val listState = rememberLazyListState()

    // Infinite scroll detection: fetch next batch when approaching bottom
    LaunchedEffect(listState.canScrollForward, query) {
        if (query.isBlank() && canLoadMore && !loadingMore) {
            snapshotFlow {
                val layout = listState.layoutInfo
                val totalItems = layout.totalItemsCount
                val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisible >= totalItems - 6
            }.collect { shouldLoad ->
                if (shouldLoad) {
                    onLoadMore()
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = Space.section)
    ) {
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    title = "No songs found.",
                    message = if (query.isNotBlank()) "No saved tracks match \"$query\"." else "Save tracks to your Spotify library to see them here."
                )
            }
        }

        items(filtered, key = { it.id }) { song ->
            CompactSongRow(
                song = song,
                isFavorite = song.id in favorites,
                onClick = { onSong(song.id) },
                onFavorite = { onFavorite(song) }
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = Space.page)
            )
        }

        // Pagination status at the bottom
        if (loadingMore) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(Space.medium),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.small))
                    Text("Loading more songs...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (error != null && tracks.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(Space.medium),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(Space.small))
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
    }
}

/**
 * Text-focused compact song row (~58dp) without album artwork thumbnails.
 */
@Composable
fun CompactSongRow(
    song: Song,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.page, vertical = 10.dp)
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (song.album.isNotBlank() && song.album != song.title) {
                    Text(
                        " · ${song.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        IconButton(
            onClick = onFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                if (isFavorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                if (isFavorite) "Remove favorite" else "Save favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// ARTISTS TAB (PHASE 7 - LIBRARY-WIDE INDEX)
// -------------------------------------------------------------------------------------------------
@Composable
private fun ColumnScope.ArtistsTab(
    artists: List<SpotifyArtist>,
    isIndexing: Boolean,
    indexedCount: Int,
    query: String,
    sort: ArtistSort,
    onOpen: (SpotifyArtist) -> Unit,
    onRetryIndexing: () -> Unit
) {
    val filtered = remember(artists, query, sort) {
        val list = if (query.isBlank()) artists else artists.filter {
            it.name.contains(query, ignoreCase = true)
        }
        when (sort) {
            ArtistSort.NameAsc -> list.sortedBy { it.name.lowercase() }
            ArtistSort.NameDesc -> list.sortedByDescending { it.name.lowercase() }
            ArtistSort.MostSaved -> list.sortedByDescending { it.songCount }
            ArtistSort.FewestSaved -> list.sortedBy { it.songCount }
        }
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = Space.section)
    ) {
        if (isIndexing) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.tiny)
                ) {
                    Row(
                        Modifier.padding(horizontal = Space.medium, vertical = Space.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Space.small))
                        Text(
                            "Indexing your saved artists... $indexedCount songs checked so far",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty() && !isIndexing) {
            item {
                EmptyState(
                    title = "No artists found.",
                    message = if (query.isNotBlank()) "No artist matches \"$query\"." else "Artists from your saved songs will appear here."
                ) {
                    Button(onClick = onRetryIndexing) { Text("Scan library") }
                }
            }
        }

        items(filtered, key = { it.name }) { artist ->
            ArtistRow(
                artist = artist,
                isIndexing = isIndexing,
                onClick = { onOpen(artist) }
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = Space.page)
            )
        }
    }
}

@Composable
private fun ArtistRow(
    artist: SpotifyArtist,
    isIndexing: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.page, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Monogram / Avatar fallback
        ArtistMonogram(artist.name, Modifier.size(44.dp))
        Spacer(Modifier.width(Space.medium))
        Column(Modifier.weight(1f)) {
            Text(
                artist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val songText = if (isIndexing) {
                "${artist.songCount} ${if (artist.songCount == 1) "song" else "songs"} found so far"
            } else {
                "${artist.songCount} saved ${if (artist.songCount == 1) "song" else "songs"}"
            }
            Text(
                songText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Outlined.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ArtistMonogram(name: String, modifier: Modifier = Modifier) {
    val initials = name.trim().split(" ")
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// -------------------------------------------------------------------------------------------------
// ARTIST DETAIL VIEW (PHASE 7)
// -------------------------------------------------------------------------------------------------
@Composable
private fun ColumnScope.ArtistDetailView(
    artist: SpotifyArtist,
    favorites: Set<String>,
    onSong: (String) -> Unit,
    onFavorite: (Song) -> Unit,
    onExplore: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(Modifier.weight(1f)) {
        // Back navigation
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Artists", Modifier.size(18.dp))
                Spacer(Modifier.width(Space.small))
                Text("Artists")
            }
        }

        // Artist Header
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArtistMonogram(artist.name, Modifier.size(80.dp))
            Spacer(Modifier.height(Space.medium))
            Text(
                artist.name,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${artist.songs.size} saved ${if (artist.songs.size == 1) "song" else "songs"} in your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Space.medium))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                Button(onClick = onExplore) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.small))
                    Text("Explore Artist")
                }
                OutlinedButton(onClick = {
                    val url = "https://open.spotify.com/search/${Uri.encode(artist.name)}"
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.small))
                    Text("Open in Spotify")
                }
            }
        }

        Spacer(Modifier.height(Space.small))
        Text(
            "YOUR SAVED SONGS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.small)
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Space.section)
        ) {
            items(artist.songs, key = { it.id }) { song ->
                CompactSongRow(
                    song = song,
                    isFavorite = song.id in favorites,
                    onClick = { onSong(song.id) },
                    onFavorite = { onFavorite(song) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = Space.page)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// PLAYLISTS TAB & PLAYLIST DETAIL (PHASE 3)
// -------------------------------------------------------------------------------------------------
@Composable
private fun ColumnScope.PlaylistsTab(
    playlists: List<SpotifyPlaylist>,
    query: String,
    loading: Boolean,
    error: String? = null,
    onOpen: (SpotifyPlaylist) -> Unit,
    onRefresh: () -> Unit
) {
    val filtered = remember(playlists, query) {
        if (query.isBlank()) playlists else playlists.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.ownerName.contains(query, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = Space.section)
    ) {
        if (filtered.isEmpty() && !loading) {
            item {
                EmptyState(
                    title = if (error != null) "Playlist access needed" else "No playlists found.",
                    message = error ?: if (query.isNotBlank()) "No playlist matches \"$query\"." else "Create or follow playlists on Spotify to explore them here."
                ) {
                    Button(onClick = onRefresh) { Text("Refresh playlists") }
                }
            }
        }

        items(filtered, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist) }
                    .padding(horizontal = Space.page, vertical = Space.gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(playlist.artworkSong(), Modifier.size(60.dp))
                Spacer(Modifier.width(Space.medium))
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    val subtitle = "${playlist.formattedTrackCount()} · by ${playlist.ownerName}" +
                        if (!playlist.isOwnerOrCollaborator) " · Owner access required" else ""
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = Space.page)
            )
        }
    }
}

@Composable
private fun ColumnScope.PlaylistDetailView(
    playlist: SpotifyPlaylist,
    songs: List<Song>,
    loading: Boolean,
    error: String?,
    favorites: Set<String>,
    onSong: (String) -> Unit,
    onFavorite: (Song) -> Unit,
    onExplore: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(Modifier.weight(1f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Playlists", Modifier.size(18.dp))
                Spacer(Modifier.width(Space.small))
                Text("Playlists")
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Cover(playlist.artworkSong(), Modifier.size(120.dp))
            Spacer(Modifier.height(Space.medium))
            Text(
                playlist.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            playlist.description?.let {
                if (it.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${playlist.formattedTrackCount()} · by ${playlist.ownerName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Space.medium))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                Button(
                    onClick = onExplore,
                    enabled = playlist.isOwnerOrCollaborator && songs.isNotEmpty() && error == null
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.small))
                    Text("Explore this playlist")
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playlist.externalUrl)))
                }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.small))
                    Text("Open in Spotify")
                }
            }
        }

        if (!playlist.isOwnerOrCollaborator && error == null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.small)
            ) {
                Text(
                    "Under Spotify's Developer Mode restrictions, tracks can only be accessed from playlists you own or collaborate on. You can still open this playlist directly in Spotify.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.medium)
                )
            }
        }

        if (error != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.small)
            ) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(Space.medium)
                )
            }
        }

        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Text(
            "TRACKLIST",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.small)
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Space.section)
        ) {
            items(songs, key = { it.id }) { song ->
                CompactSongRow(
                    song = song,
                    isFavorite = song.id in favorites,
                    onClick = { onSong(song.id) },
                    onFavorite = { onFavorite(song) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = Space.page)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// ALBUMS TAB & ALBUM DETAIL
// -------------------------------------------------------------------------------------------------
@Composable
private fun ColumnScope.AlbumsTab(
    albums: List<MusicAlbum>,
    query: String,
    sort: LibrarySort,
    loading: Boolean,
    onOpen: (MusicAlbum) -> Unit
) {
    val filtered = remember(albums, query, sort) {
        val list = if (query.isBlank()) albums else albums.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
        if (sort == LibrarySort.Title) list.sortedBy { it.title.lowercase() } else list.sortedBy { it.artist.lowercase() }
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = Space.section)
    ) {
        if (filtered.isEmpty() && !loading) {
            item {
                EmptyState("No albums found.", "Save albums in Spotify to see them in your Audiary.")
            }
        }

        items(filtered, key = { it.id }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(album) }
                    .padding(horizontal = Space.page, vertical = Space.gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cover(album.artworkSong(), Modifier.size(60.dp))
                Spacer(Modifier.width(Space.medium))
                Column(Modifier.weight(1f)) {
                    Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${album.totalTracks} tracks · ${album.releaseDate.take(4)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = Space.page)
            )
        }
    }
}

@Composable
private fun ColumnScope.AlbumDetailView(
    album: MusicAlbum,
    songs: List<Song>,
    loading: Boolean,
    error: String?,
    favorites: Set<String>,
    onSong: (String) -> Unit,
    onFavorite: (Song) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(Modifier.weight(1f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to Albums", Modifier.size(18.dp))
                Spacer(Modifier.width(Space.small))
                Text("Albums")
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Cover(album.artworkSong(), Modifier.size(120.dp))
            Spacer(Modifier.height(Space.medium))
            Text(album.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${album.totalTracks} tracks · ${album.releaseDate.take(4)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(Space.medium))
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(album.externalUrl)))
            }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(Space.small))
                Text("Open in Spotify")
            }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        Text(
            "TRACKLIST",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.small)
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Space.section)
        ) {
            items(songs, key = { it.id }) { song ->
                CompactSongRow(
                    song = song,
                    isFavorite = song.id in favorites,
                    onClick = { onSong(song.id) },
                    onFavorite = { onFavorite(song) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = Space.page)
                )
            }
        }
    }
}
