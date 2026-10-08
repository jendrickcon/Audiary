package com.example.audiary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.audiary.ui.theme.Space

@Composable fun PageHeading(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.medium)) {
        Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Space.small))
        Text(title, style = MaterialTheme.typography.displaySmall)
        if (subtitle != null) {
            Spacer(Modifier.height(Space.small))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(Space.page), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.AutoStories, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Space.medium))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.small))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.let { Spacer(Modifier.height(Space.medium)); it() }
    }
}
@Composable fun ErrorNotice(message: String, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.small), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
@Composable fun DeleteMemoryDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete this memory?") },
        text = { Text("This will permanently remove the memory from your Audiary.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep memory") } })
}
