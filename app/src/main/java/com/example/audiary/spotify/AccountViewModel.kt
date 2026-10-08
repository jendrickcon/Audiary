package com.example.audiary.spotify

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AccountViewModel(private val auth: SpotifyAuthManager) : ViewModel() {
    val state = auth.state
    fun connect(openBrowser: (String) -> Unit) = viewModelScope.launch {
        try { openBrowser(auth.begin()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { auth.fail("Couldn't open Spotify sign-in. Check that a browser is installed and try again.") }
    }
    fun disconnect() = viewModelScope.launch {
        try { auth.disconnect() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { auth.fail("Couldn't disconnect Spotify. Please try again.") }
    }
}
