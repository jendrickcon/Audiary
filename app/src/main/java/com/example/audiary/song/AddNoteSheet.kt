package com.example.audiary.song

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(vm: NoteEditorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (!state.open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { !state.saving || it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = vm::cancel, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(if (state.editing) "Revisit this memory." else "What's this song to you?",
                style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("A person, a place, a moment. Keep it here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(value = state.draft, onValueChange = vm::change,
                label = { Text("Your memory") }, placeholder = { Text("I remember…") },
                minLines = 4, maxLines = 8, enabled = !state.saving,
                modifier = Modifier.fillMaxWidth())
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = vm::cancel, enabled = !state.saving) { Text("Cancel") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = vm::save, enabled = !state.saving && state.draft.isNotBlank()) {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.saving) "Saving…" else if (state.error != null) "Retry save" else "Save memory")
                }
            }
        }
    }
}
