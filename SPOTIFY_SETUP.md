# Connect Spotify to Audiary V2

Audiary works seamlessly both with and without Spotify. The application uses modern Authorization Code Flow with PKCE (Proof Key for Code Exchange) requiring **zero client secrets** embedded in the client application.

---

## 1. Spotify Developer Dashboard Setup

1. Log in to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard).
2. Create an application (or open your existing Audiary app).
3. In **Settings**:
   - Copy your **Client ID**. (Never expose or use a Client Secret).
   - In **Redirect URIs**, add your redirect URI:
     - **Option A (Default & Recommended - Custom Scheme):**
       `com.example.audiary://callback`
       *(Requires no external website or domain verification; works out of the box on Android devices).*
     - **Option B (HTTPS Android App Link):**
       `https://YOUR_DOMAIN/oauth/callback`
       *(Requires hosting a `.well-known/assetlinks.json` file on an HTTPS web server you control).*
   - Select **APIs used**: Web API.
   - Save changes.
4. **IMPORTANT — User Management (Development Mode):**
   - Spotify Developer apps in Development mode restrict API access to accounts explicitly registered in the **User Management** tab.
   - Click the **User Management** tab in your Spotify app dashboard.
   - Add the **Name** and **Email address** of your Spotify account.
   - If an account is not on this list, Spotify will return an authorization error (`403 Forbidden / Not registered in Developer Dashboard`).

---

## 2. Local Configuration in Audiary

Open `local.properties` in the project root directory (this file is ignored by version control):

```properties
spotify.clientId=ee00127f046c4e51a00358f9f58aec40
spotify.redirectUri=com.example.audiary://callback
```

*(Alternatively, environment variables `SPOTIFY_CLIENT_ID` and `SPOTIFY_REDIRECT_URI` are supported).*

### Dual Redirect Mechanism
- **Custom URI Scheme (`com.example.audiary://callback`):**
  Audiary's AndroidManifest handles this intent filter automatically. When Spotify's web authorization completes, it redirects back to the app seamlessly without needing external hosting.
- **HTTPS App Link (`https://...`):**
  If you specify an HTTPS redirect URI, Audiary's build script automatically injects the domain and path into the manifest's `android:autoVerify="true"` intent filter. To use App Links, host your SHA-256 fingerprint in `https://YOUR_DOMAIN/.well-known/assetlinks.json`.

---

## 3. Features & User Experience

### Explore Screen (Moving Music Wall)
- **Discovery Source Selector:** When connected to Spotify, interactive filter chips appear at the top of the Explore screen:
  - **My Spotify Library:** Shuffles and displays your real saved Spotify tracks across the five moving rows with real album artwork.
  - **Demo Collection:** Instantly switches back to the curated 25-song demo catalog.
- **Offline / Cached Exploration:** Saved tracks fetched from Spotify are cached locally in the Room database (`MusicSource.Spotify`). Shuffling does **not** make unnecessary network requests, preserving Spotify API quota and battery life.
- **Graceful Empty State:** If your Spotify account has no saved tracks, Audiary shows a helpful empty state with a 1-tap option to switch to the Demo collection. Demo songs are never silently mixed into your Spotify collection.

### Library Screen
- **Paging & Saved Tracks:** View your Spotify saved tracks, albums, and artists with 20 items per page and explicit Previous/Next controls.
- **One-Touch Connect/Disconnect:** Tap "Connect Spotify" to authenticate in Chrome; tap "Disconnect" to remove tokens while preserving all your notes and favorites.

### Diary & Memories
- Audiary memories, notes, and favorites are 100% stored on-device in Room Database and survive app restarts, disconnections, and Spotify token updates.

---

## 4. Verification & Testing

To compile and verify all tests:

```powershell
$env:JAVA_HOME='C:\Users\DOT36\.jdks\jbr-21.0.11'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```
