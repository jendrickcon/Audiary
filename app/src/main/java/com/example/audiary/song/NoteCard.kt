package com.example.audiary.song

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.audiary.model.Note
import com.example.audiary.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun memoryDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))

@Composable fun NoteCard(note: Note, modifier: Modifier = Modifier, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(memoryDate(note.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (note.isSample) Text("Example memory", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (note.updatedAt != note.createdAt) Text("Edited " + memoryDate(note.updatedAt),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "Memory options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit memory") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete memory") }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Text(note.text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Space.page))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
