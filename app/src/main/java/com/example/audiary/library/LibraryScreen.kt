package com.example.audiary.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiary.explore.DiscoverySource
import com.example.audiary.model.Song
import com.example.audiary.model.MusicSource
import com.example.audiary.spotify.*
import com.example.audiary.ui.*
import com.example.audiary.ui.theme.Space

@Composable fun LibraryScreen(
    vm: LibraryViewModel,
    favoritesVm: FavoritesViewModel,
    account: AccountViewModel,
    spotifyVm: SpotifyLibraryViewModel,
    onSong: (String) -> Unit,
    onExploreSource: (DiscoverySource) -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by favoritesVm.favorites.collectAsStateWithLifecycle()
    val favoriteSongs by favoritesVm.songs.collectAsStateWithLifecycle()
    val auth by account.state.collectAsStateWithLifecycle()
    var spotify by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(auth.connected) { if (!auth.connected) spotify = false }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.Title) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var sorting by remember { mutableStateOf(false) }
    val songs = remember(state.songs, favoriteSongs, query, tab, sort, favorites) {
        filterLibrary(if (tab == LibraryTab.Favorites) favoriteSongs else state.songs, query, favorites, tab == LibraryTab.Favorites, sort)
    }
    BackHandler(group != null) { group = null }
    Column(Modifier.fillMaxSize()) {
        PageHeading("YOUR COLLECTION", "The library.", "Good music, always within reach.")
        AccountPanel(account)
        if (auth.connected) Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            FilterChip(!spotify, onClick = { spotify = false }, label = { Text("Demo & favorites") })
            FilterChip(spotify, onClick = { spotify = true }, label = { Text("Spotify library") })
        }
        if (spotify && auth.connected) {
            SpotifyCollection(spotifyVm, favoritesVm, auth.sessionId, onSong, onExploreSource)
            return@Column
        }
        OutlinedTextField(query, onValueChange = { query = it; group = null },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page),
            placeholder = { Text("Songs, artists, albums") }, singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, "Search library") },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } },
            shape = MaterialTheme.shapes.large)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.page),
            horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            LibraryTab.entries.forEach { item ->
                FilterChip(selected = tab == item, onClick = { tab = item; group = null }, label = { Text(item.name) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.page), verticalAlignment = Alignment.CenterVertically) {
            Text(if (group != null) group!!.substringAfterLast(':') else if (tab == LibraryTab.Favorites) "Audiary favorites" else "Demo collection",
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (group != null) TextButton(onClick = { group = null }) { Text("All " + tab.name.lowercase()) }
            Box {
                TextButton(onClick = { sorting = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, null, Modifier.size(16.dp)); Text(" " + sort.label) }
                DropdownMenu(sorting, onDismissRequest = { sorting = false }) {
                    LibrarySort.entries.forEach { order ->
                        DropdownMenuItem(text = { Text(order.label) }, onClick = { sort = order; sorting = false })
                    }
                }
            }
        }
        when {
            state.loading -> Centered { CircularProgressIndicator() }
            state.error != null -> EmptyState("Couldn't open the collection.", state.error.orEmpty()) { Button(onClick = vm::load) { Text("Try again") } }
            songs.isEmpty() -> EmptyState(if (tab == LibraryTab.Favorites) "Keep your favorites close." else "No matches, yet.",
                if (tab == LibraryTab.Favorites) "Bookmark songs to keep them in your personal Audiary collection." else "Try another song, artist, or album.")
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Space.page)) {
                when {
                    tab == LibraryTab.Albums && group == null -> {
                        val albums = songs.groupBy { it.albumId.ifBlank { it.artist + ":" + it.album } }
                        items(albums.entries.toList(), key = { it.key }) { album ->
                            CollectionRow(album.value.first(), album.value.first().album, album.value.first().artist,
                                "${album.value.size} songs", onClick = { group = album.key })
                        }
                    }
                    tab == LibraryTab.Artists && group == null -> {
                        items(songs.groupBy { it.artist }.entries.toList(), key = { it.key }) { artist ->
                            CollectionRow(artist.value.first(), artist.key, "${artist.value.size} songs in your library",
                                "Artist", onClick = { group = artist.key })
                        }
                    }
                    else -> {
                        val visible = songs.filter { group == null || (if (tab == LibraryTab.Albums) it.albumId == group else it.artist == group) }
                        items(visible, key = { it.id }) { song ->
                            SongListRow(song, song.id in favorites, onClick = { onSong(song.id) }, onFavorite = { favoritesVm.toggle(song) })
                        }
                    }
                }
            }
        }
    }
}

@Composable fun SongListRow(song: Song, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = Space.page, end = Space.small, top = Space.small, bottom = Space.small),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(song, Modifier.size(56.dp))
        Spacer(Modifier.width(Space.medium))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(song.album, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (song.source == MusicSource.Spotify) song.externalUrl?.let { SpotifyAttribution(it) }
        }
        IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
            if (favorite) "Remove Audiary favorite" else "Add Audiary favorite", tint = MaterialTheme.colorScheme.primary) }
    }
}
@Composable private fun CollectionRow(song: Song, title: String, subtitle: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.page, vertical = Space.gap),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(song, Modifier.size(72.dp))
        Spacer(Modifier.width(Space.medium))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
