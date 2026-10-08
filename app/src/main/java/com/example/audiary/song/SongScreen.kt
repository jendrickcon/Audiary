package com.example.audiary.song

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiary.library.FavoritesViewModel
import com.example.audiary.model.*
import com.example.audiary.spotify.SpotifyAttribution
import com.example.audiary.spotify.openSpotify
import com.example.audiary.ui.*
import com.example.audiary.ui.theme.Space

@Composable fun SongScreen(vm: SongViewModel, editor: NoteEditorViewModel, favoritesVm: FavoritesViewModel,
    focusNoteId: String? = null, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by favoritesVm.favorites.collectAsStateWithLifecycle()
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = rememberLazyListState()
    var focused by rememberSaveable(focusNoteId) { mutableStateOf(false) }
    LaunchedEffect(state.notes, focusNoteId) {
        if (!focused && focusNoteId != null) {
            val index = state.notes.indexOfFirst { it.id == focusNoteId }
            if (index >= 0) { scroll.scrollToItem(index + 1); focused = true }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.small), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("THE SONG & THE STORY", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val song = state.song
            if (song != null) IconButton(onClick = { favoritesVm.toggle(song) }) {
                Icon(if (song.id in favorites) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (song.id in favorites) "Remove Audiary favorite" else "Add Audiary favorite",
                    tint = MaterialTheme.colorScheme.primary)
            } else Spacer(Modifier.width(Space.touch))
        }
        if (state.isLoading) Centered { CircularProgressIndicator() }
        else LazyColumn(state = scroll, contentPadding = PaddingValues(horizontal = Space.page, vertical = Space.medium),
            verticalArrangement = Arrangement.spacedBy(Space.medium), modifier = Modifier.fillMaxSize()) {
            item {
                val song = state.song
                if (song != null) SongHero(song)
                else EmptyState("The music stays with you.", state.error ?: "Music details are unavailable.") {
                    TextButton(onClick = vm::load) { Text("Try loading again") }
                }
                Spacer(Modifier.height(Space.section))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Space.page))
                Text("MY AUDIARY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Space.small))
                Text("A song, a thousand memories.", style = MaterialTheme.typography.headlineSmall)
                state.notesError?.let { ErrorNotice(it, vm::observeNotes) }
            }
            items(state.notes, key = { it.id }) { note ->
                NoteCard(note, onEdit = { editor.edit(note) }, onDelete = { deleteId = note.id })
            }
            if (state.notes.isEmpty() && state.notesError == null) item {
                Text("Some songs take you somewhere.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Space.small))
                Text("Write about the person, place, or moment this one brings back.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.song != null) item {
                Button(onClick = { editor.create(state.song!!.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch)) {
                    Icon(Icons.Outlined.EditNote, null)
                    Spacer(Modifier.width(Space.small))
                    Text("Write a memory")
                }
                Spacer(Modifier.height(Space.section))
            }
        }
    }
    NoteEditor(editor)
    deleteId?.let { id -> DeleteMemoryDialog(onDismiss = { deleteId = null },
        onConfirm = { vm.deleteNote(id); deleteId = null }) }
}

@Composable private fun SongHero(song: Song) {
    val context = LocalContext.current
    var linkError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Cover(song, Modifier.size(minOf(maxWidth * .72f, 280.dp)))
        }
        Spacer(Modifier.height(Space.page))
        Text(song.title.ifBlank { "Untitled track" }, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.small))
        Text(song.artist.ifBlank { "Artist unavailable" }, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Space.small))
        Text(listOf(song.album, song.releaseDate.take(4)).filter { it.isNotBlank() }.joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(song.durationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.medium))
        OutlinedButton(onClick = {
            try {
                val url = song.externalUrl ?: "https://open.spotify.com/search/" + Uri.encode(song.title + " " + song.artist)
                openSpotify(context, url)
                linkError = null
            } catch (e: Exception) { linkError = "No app could open this link. Install a browser or Spotify and try again." }
        }) {
            Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp))
            Spacer(Modifier.width(Space.small))
            Text(if (song.spotifyTrackId != null) "Open in Spotify" else "Search on Spotify")
        }
        if (song.source == MusicSource.Demo) Text("Demo track · Original Audiary artwork", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else song.externalUrl?.let { SpotifyAttribution(it) }
        linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
