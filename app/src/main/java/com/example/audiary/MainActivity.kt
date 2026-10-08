package com.example.audiary

import android.graphics.Color
import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.audiary.ui.theme.AudiaryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val app = application as AudiaryApp
        handleSpotifyRedirect(intent)
        setContent {
            AudiaryTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AudiaryNav(app)
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSpotifyRedirect(intent)
    }
    private fun handleSpotifyRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        intent.data = null // A configuration change must not exchange the same authorization code twice.
        com.example.audiary.spotify.AudiaryLog.i("INTENT_RECEIVED: deep link intent received with scheme=${uri.scheme}, host=${uri.host}, path=${uri.path}")
        (application as AudiaryApp).spotifyAuth.acceptRedirect(uri.toString())
    }
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            (application as AudiaryApp).playbackController.disconnect()
        }
    }
}
