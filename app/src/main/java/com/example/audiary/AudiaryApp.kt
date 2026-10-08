package com.example.audiary

import android.app.Application
import com.example.audiary.data.DiaryRepository
import com.example.audiary.data.*
import com.example.audiary.data.local.AudiaryDatabase
import com.example.audiary.spotify.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the repositories so they survive screen rotation. */
class AudiaryApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val spotifyHttp by lazy { SpotifyHttp() }
    val spotifyAuth by lazy { SpotifyAuthManager(SecureSessionStore(this), spotifyHttp, applicationScope) }
    val spotifyApi by lazy { SpotifyApi(spotifyAuth, spotifyHttp) }
    val database by lazy { AudiaryDatabase.create(this) }
    private val seed by lazy { LocalSeed(database) }
    val music: MusicRepository by lazy { LocalMusicRepository(database, seed) }
    val diary: DiaryRepository by lazy { RoomDiaryRepository(database, seed) }
    val favorites: FavoritesRepository by lazy { RoomFavoritesRepository(database) }
    val discoverySourceManager by lazy { com.example.audiary.explore.DiscoverySourceManager(this) }
    val playbackController by lazy { SpotifyPlaybackController(this) }
}
