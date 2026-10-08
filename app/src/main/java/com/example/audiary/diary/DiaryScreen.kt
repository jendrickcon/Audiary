package com.example.audiary.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiary.song.*
import com.example.audiary.model.MusicSource
import com.example.audiary.spotify.SpotifyAttribution
import com.example.audiary.ui.*
import com.example.audiary.ui.theme.Space
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun DiaryScreen(vm: DiaryViewModel, editor: NoteEditorViewModel, onMemory: (String, String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(state.entries) { state.entries.groupBy {
        YearMonth.from(Instant.ofEpochMilli(it.note.createdAt).atZone(ZoneId.systemDefault()))
    } }
    Column(Modifier.fillMaxSize()) {
        PageHeading("YOUR AUDIARY", "Life, through music.", "For the songs that became a part of you.")
        when {
            state.loading -> Centered { CircularProgressIndicator() }
            state.entries.isEmpty() && state.error == null -> EmptyState("Every memory starts somewhere.",
                "Open a song in Explore or Library and write your first memory.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.page, vertical = Space.medium)) {
                state.error?.let { message -> item { ErrorNotice(message, vm::load) } }
                groups.forEach { (month, entries) ->
                    item(key = "month:$month") {
                        Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).uppercase(),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(Space.page))
                    }
                    items(entries, key = { it.note.id }) { entry ->
                        val song = entry.song
                        Row(Modifier.fillMaxWidth().clickable { onMemory(entry.note.songId, entry.note.id) },
                            verticalAlignment = Alignment.CenterVertically) {
                            if (song != null) { Cover(song, Modifier.size(48.dp)); Spacer(Modifier.width(Space.gap)) }
                            Column {
                                Text(song?.title ?: "Music unavailable", style = MaterialTheme.typography.titleMedium)
                                Text(song?.artist ?: "Your memory is safely kept here", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (song?.source == MusicSource.Spotify) song.externalUrl?.let { SpotifyAttribution(it) }
                        NoteCard(entry.note, onEdit = { editor.edit(entry.note) }, onDelete = { deleteId = entry.note.id })
                        Spacer(Modifier.height(Space.section))
                    }
                }
            }
        }
    }
    NoteEditor(editor)
    deleteId?.let { id -> DeleteMemoryDialog(onDismiss = { deleteId = null }, onConfirm = { vm.delete(id); deleteId = null }) }
}
