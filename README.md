# Audiary

> *Your music. Your memories.*

Audiary is an editorial, high-aesthetic Android music discovery and journaling companion built with Jetpack Compose. Integrating seamlessly with the **Spotify Web API** via RFC 7636 PKCE OAuth, Audiary reimagines your personal streaming library into an ambient, tactile **Listening Room**—surfacing forgotten gems, curating focused artist sessions, and anchoring your memories to the songs that soundtrack your life.

---

## Preview

### 3D Music Globe & Ambient Listening Room
<p align="center">
  <img src="docs/screenshots/explore_screen.png" width="30%" alt="Explore Listening Room" />
  <img src="docs/screenshots/cylinder_globe_active.png" width="30%" alt="3D Cylinder Globe" />
</p>

### Spotify Playback & Mini-Player
<p align="center">
  <img src="docs/screenshots/mini_player_explore.png" width="30%" alt="Mini Player in Explore" />
  <img src="docs/screenshots/expanded_player.png" width="30%" alt="Expanded Now Playing Sheet" />
  <img src="docs/screenshots/song_detail.png" width="30%" alt="Song Detail & Memories" />
</p>

### High-Density Library & Curated Sources
<p align="center">
  <img src="docs/screenshots/spotify_songs_compact.png" width="30%" alt="Compact Songs Tab" />
  <img src="docs/screenshots/spotify_playlists.png" width="30%" alt="Spotify Playlists" />
  <img src="docs/screenshots/source_sheet.png" width="30%" alt="Listening Room Source Picker" />
</p>

---

## Features

### 1. The Listening Room & 3D Music Globe
- **Five-Row Animated Discovery Wall**: Continuously flowing horizontal music rows showcasing high-resolution album artwork from your real Spotify library.
- **Pinch-to-Cylinder (3D Music Globe)**: Pinch gesture dynamically curves the five music rows into an interactive cylindrical perspective wall with 3D projection, depth shading, and horizontal drag inertia.
- **Shuffle Rush**: Tapping shuffle unleashes a high-velocity burst of album covers that smoothly slows down and transitions into the newly randomized collection.
- **Dynamic Source Selector**: Toggle your Listening Room's source on the fly between **All Saved Songs**, specific **Spotify Playlists**, individual **Artists**, or the **Offline Demo Collection**.
- **Progressive Background Prefetching**: Automatically expands the discovery pool to hundreds of tracks in the background with graceful rate-limit pacing.
- **Instant In-Memory Shuffle**: Shuffles across hundreds of in-memory cached songs instantaneously without triggering redundant network requests.

### 2. Spotify Playback & Persistent Controls
- **Spotify App Remote Integration**: Direct playback control for playing, pausing, skipping, and seeking tracks via Spotify IPC.
- **Persistent Mini-Player**: Bottom-docked mini-player with live artwork, real-time progress bar, play/pause controls, and quick expansion.
- **Expanded Now-Playing Sheet**: Full-screen modal with large album art, track details, interactive scrub slider, prev/next buttons, and quick navigation to Song Memory.
- **Graceful Fallbacks**: "Open App" action deep-links directly into the Spotify client or web app if App Remote is unavailable.

### 3. High-Density Library & Infinite Scrolling
- **Compact Song Rows**: Optimized row spacing (~56dp) displaying title, artist, album, and bookmark actions with high information density.
- **Smooth Infinite Scrolling**: Eliminates pagination buttons by automatically prefetching and appending 40-track batches as you scroll near the bottom.
- **Playlist Hub**: Browse your personal Spotify playlists, view track totals, and launch playlist-specific Listening Room sessions.
- **Library-Wide Artist Indexing**: Indexes your entire saved music library into a local SQLite database, computing exact saved counts for every artist with sorting options (**Most saved**, **Fewest saved**, **A–Z**, **Z–A**).

### 4. Musical Diary & Memory Journal
- **Song-Linked Notes**: Record reflections, memories, and personal thoughts anchored to individual songs.
- **Room Database Persistence**: Fully offline, crash-resilient storage ensuring your personal journal entries remain permanent.
- **Editorial Typography**: Styled with **Fraunces** for headings and branding, and **DM Sans** for metadata and reading comfort.

---

## Tech Stack & Architecture

- **UI & Design**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material Design 3, custom typography (`Fraunces` variable serif and `DM Sans`), and dark editorial theming.
- **Architecture**: MVVM with unidirectional data flow (UDF), Kotlin Coroutines, and `StateFlow` reactive streams.
- **Local Persistence**: [Android Room Database](https://developer.android.com/training/data-storage/room) (SQLite) with relational DAOs for cached tracks, favorites, and diary notes.
- **Networking & Authentication**:
  - [OkHttp](https://square.github.io/okhttp/) HTTP engine.
  - **Spotify OAuth 2.0 PKCE** (RFC 7636): Secure code challenge and verifier generation, custom deep-link redirect processing (`com.example.audiary://callback`), and automatic token refresh with zero stored secrets.
  - **Spotify App Remote SDK** (v0.8.0): Inter-process communication with Spotify client for local device playback.
- **Image Pipeline**: [Coil Compose](https://coil-kt.github.io/coil/) for asynchronous, cached image loading.
- **Testing**: JUnit 4, Kotlin Coroutines Test (`runTest`, `StandardTestDispatcher`), MockWebServer, and Android interaction tests.

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 21
- Android SDK Platform 34+ (Minimum SDK: 26)
- A Spotify Developer account and the official Spotify Android app installed

### Spotify Setup
1. Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) and create a new application.
2. In your application settings, configure the following:
   - **Redirect URI**: `com.example.audiary://callback`
   - **APIs Used**: Web API, App Remote
   - **Android Package**: `com.example.audiary`
   - **SHA-1 Fingerprint**: Your debug keystore SHA-1 (run `./gradlew :app:signingReport` to view)
3. In your project's `local.properties` file, add your Client ID and redirect URI:
   ```properties
   spotify.clientId=YOUR_SPOTIFY_CLIENT_ID
   spotify.redirectUri=com.example.audiary://callback
   ```
4. Build and install the application:
   ```bash
   ./gradlew assembleDebug
   ```

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
