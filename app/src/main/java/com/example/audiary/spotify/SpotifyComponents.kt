package com.example.audiary.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiary.R
import com.example.audiary.ui.theme.Space

fun openSpotify(context: Context, url: String) {
    val uri = Uri.parse(url)
    require(uri.scheme == "https" && uri.host == "open.spotify.com")
    val appIntent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.spotify.music")
    try { context.startActivity(appIntent) }
    catch (e: android.content.ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

/** Official, unmodified Spotify paths; leave clear space around the mark. */
@Composable fun SpotifyAttribution(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failure by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(Modifier.heightIn(min = 40.dp).clickable(onClickLabel = "Open in Spotify") {
            try { openSpotify(context, url); failure = false } catch (e: Exception) { failure = true }
        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.spotify_logo), contentDescription = "Spotify — open in Spotify",
                modifier = Modifier.width(80.dp).height(22.dp))
        }
        if (failure) Text("Install Spotify or a browser to open this link.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
}

@Composable fun AccountPanel(vm: AccountViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var setup by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.page)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Link, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Space.small))
            Text(if (state.connected) "Spotify connected" else "Bring your own soundtrack", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            TextButton(enabled = !state.busy, onClick = {
                if (state.connected) disconnect = true
                else if (!state.configured) setup = true
                else vm.connect { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }) { Text(if (state.connected) "Disconnect" else "Connect Spotify") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    if (setup) AlertDialog(onDismissRequest = { setup = false }, title = { Text("Spotify needs setup.") },
        text = { Text("Spotify connection isn't configured in this build yet. Your demo library, favorites, and memories are ready to use. The project includes the setup instructions for enabling Spotify.") },
        confirmButton = { TextButton(onClick = { setup = false }) { Text("Keep exploring") } })
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect Spotify?") },
        text = { Text("Your Audiary memories and personal favorites will stay on this device. You can connect again at any time.") },
        confirmButton = { TextButton(onClick = { vm.disconnect(); disconnect = false }) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}
