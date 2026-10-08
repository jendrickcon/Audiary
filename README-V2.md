# Audiary V2

**Your music. Your memories.**

A local-first Kotlin / Jetpack Compose music companion. The existing project foundation and signature five-row discovery wall are preserved and enhanced with Spotify PKCE integration, discovery source selection, Room persistence, and an editorial dark UI theme.

---

## What changed in V2

- **Explore:**
  - Five responsive album-art rows with separate captions, alternating drift, immediate touch pause, delayed resume, and an explicit pause control.
  - **Spotify-Powered Wall:** Select between **"My Spotify Library"** and **"Demo Collection"** with discovery chips. Shuffles the local cached pool without spamming the Spotify API.
  - Graceful empty states and no silent mixing of demo and Spotify tracks.
  - Floating **"Shuffle the mood"** action below the music wall.
- **Design:** Charcoal/ivory/amber editorial palette, serif headings, readable sans-serif body text, shared spacing/shape tokens, and procedural artwork for demo tracks.
- **Navigation:** Explore, Library, Diary bottom navigation with preserved state and deep linking to song detail.
- **Library:** Songs, albums, artists, search filter, title/artist sorting, and Room-backed personal favorites.
- **Diary:** Chronological month-grouped timeline, retained song metadata, note editing, and confirmed deletion.
- **Note Editor:** Multiline keyboard-aware sheet with `SavedStateHandle` draft retention, error handling, and duplicate-tap protection.
- **Persistence:** SQLite Room database (`AudiaryDatabase`, version 1) with schema export, foreign keys, indices, and transactional seeding.
- **Spotify Integration:**
  - Modern Authorization Code with PKCE (no Client Secret).
  - Dual redirect support: Default custom scheme (`com.example.audiary://callback`) and optional HTTPS App Links (`https://...`).
  - Read-only `user-library-read` scope.
  - Encrypted token storage in Android Keystore AES-GCM.
  - Paged saved tracks and albums API with Coil image loading and official Spotify attribution.

---

## Verification & Testing

All test suites and lint checks pass cleanly:

```powershell
$env:JAVA_HOME='C:\Users\DOT36\.jdks\jbr-21.0.11'

# Build debug APK:
.\gradlew.bat :app:assembleDebug

# Run JVM Unit Tests (16 tests passing, including PKCE, Room repository, Explore arrangement, and Library filtering):
.\gradlew.bat :app:testDebugUnitTest

# Run Android Lint (0 errors):
.\gradlew.bat :app:lintDebug

# Run Connected Instrumentation Tests on Emulator / Device (4 connected UI/workflow/persistence tests passing):
.\gradlew.bat :app:connectedDebugAndroidTest
```

For Spotify Developer Dashboard configuration and user whitelist setup, see [SPOTIFY_SETUP.md](SPOTIFY_SETUP.md).
