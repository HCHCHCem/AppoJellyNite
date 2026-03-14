# AppoJellyNite
# Unified Media & Gaming Frontend — Full Project Plan

---

## Project Overview

A native Android app (phone, tablet, and Google TV) that serves as a unified front end for a PC game library (Playnite), a media library (Jellyfin), and eventually local emulation — with remote game streaming via Apollo/Moonlight when away from the PC.

---

## System Architecture

### The Gaming PC (Server Side)

Three existing services run independently, unmodified:

| Service | Role | Interface Used |
|---------|------|----------------|
| **Playnite** | Master game library manager | Local database files at `%AppData%\Playnite\library` |
| **Playnite Web** | Exposes Playnite library over the network | GraphQL API + MQTT (backed by MongoDB) |
| **Jellyfin** | Media server (movies, shows, music) | REST API with official SDKs |
| **Apollo** | Game streaming host (Sunshine fork) | Moonlight protocol + Web API (`/api/apps/launch`, `/api/login`, `apps.json` config) |

A custom **sync tool** bridges Playnite and Apollo by reading the Playnite library and writing entries into Apollo's `apps.json`.

### The Client App (Android)

A Kotlin/Jetpack Compose app with these integration layers:

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Media browsing & playback | Jellyfin SDK (Kotlin/TypeScript) | Browse and stream movies/shows |
| Game library data | Playnite Web GraphQL API | Browse PC game collection |
| Game streaming | moonlight-common-c (C library via JNI) | Stream PC games from Apollo |
| Local emulation (Phase 4) | LibretroDroid (Gradle dependency) | Run retro games on-device |
| TV interface | Android Leanback library | D-pad/controller navigation for Google TV |

### Network Topology

```
[Android Device]
      |
  [Tailscale VPN / Local LAN]
      |
[Gaming PC]
 ├── Apollo (streaming host, port 47989/47984)
 ├── Jellyfin (media server, port 8096)
 ├── Playnite Web (GraphQL API, configurable port)
 └── Playnite (desktop app, no network exposure)
```

When local: device connects directly over LAN.
When remote: Tailscale creates a secure tunnel. No port forwarding needed.

---

## Component 1: The Sync Tool

### Purpose

Reads the Playnite game library and writes matching entries into Apollo's `apps.json` so every game is launchable and streamable via the Moonlight protocol.

### Technical Details

**Input — Playnite's Library**

Playnite stores its library as a collection of JSON/YAML files under:
```
%AppData%\Playnite\library\
├── games/
│   ├── {game-id}.json    # One file per game
├── platforms/
├── emulators/
└── ...
```

Each game file contains:
- `Name` — display name
- `GameId` — platform-specific ID (e.g., Steam AppID)
- `InstallDirectory` — path to the game install
- `Source` — which library it came from (Steam, GOG, Epic, etc.)
- `PlayAction` — how to launch it (exe path, URL, emulator config)
- `CoverImage`, `BackgroundImage` — artwork file references
- `Platforms` — what platform(s) it runs on
- `IsInstalled` — whether it's currently installed

If Playnite Web is running, you can alternatively query via its GraphQL API, but reading the local files directly is simpler for a tool running on the same PC.

**Output — Apollo's apps.json**

Located at `C:\Program Files\Apollo\config\apps.json`. Each entry needs:
```json
{
  "name": "Game Name",
  "output": "",
  "cmd": "",
  "detached": ["launch-command-here"],
  "image-path": "C:\\path\\to\\cover.png",
  "auto-detach": "true",
  "wait-all": "true",
  "exit-timeout": "5"
}
```

**Launch Command Mapping**

The sync tool maps Playnite's source to the correct Apollo launch command:

| Source | Launch Command Format | Example |
|--------|----------------------|---------|
| Steam | `steam://rungameid/{AppID}` | `steam://rungameid/1091500` |
| GOG | Direct exe path from `PlayAction` | `"C:\GOG Games\Cyberpunk 2077\bin\x64\Cyberpunk2077.exe"` |
| Epic | `com.epicgames.launcher://apps/{AppID}?action=launch&silent=true` | Epic launch URL |
| Emulated (via Playnite) | Emulator exe + ROM path from `PlayAction` | `"C:\RetroArch\retroarch.exe" -L cores\snes9x.dll "roms\game.sfc"` |
| Generic/manual | Direct exe path from `PlayAction.Path` | Any arbitrary executable |

**Artwork Handling**

Playnite stores artwork in its database cache. The sync tool:
1. Reads the `CoverImage` reference from the game JSON
2. Resolves it to the actual file in Playnite's files directory
3. Copies it to a directory Apollo can reference
4. Writes the absolute path into the `image-path` field

**Sync Behavior**

- Only syncs games where `IsInstalled` is `true`
- Generates a deterministic ID per game so re-runs update rather than duplicate
- Removes Apollo entries for games that have been uninstalled
- Can run as a scheduled task, or ideally as a Playnite extension triggered on library changes

**Starting Point**

Fork or reference the [Gamesphere Import Tool](https://github.com/trevlars/Gamesphere-Import-Tool) which already writes to Apollo's `apps.json` format for Steam/Epic/Xbox. Replace the library scanning logic with Playnite library reading.

**Language:** Python (matching the Gamesphere tool and Playnite's scripting ecosystem).

### Deliverable

A Python script (or Playnite PowerShell extension) that:
1. Scans the Playnite library directory
2. Maps each installed game to an Apollo app entry with correct launch command
3. Copies cover art to an Apollo-accessible directory
4. Writes/updates `apps.json`
5. Optionally restarts Apollo to pick up changes (or relies on Apollo's config reload)

---

## Component 2: The Client App — Foundation

### Project Setup

**Language & Framework:**
- Kotlin
- Jetpack Compose for phone/tablet UI
- Leanback + Compose for TV (AndroidX Leanback Compose interop)
- Minimum SDK: 24 (Android 7.0 — covers virtually all active devices and TV boxes)
- Target SDK: 35

**Modular Package Structure:**
```
com.yourapp/
├── core/                    # Shared models, networking, DI
│   ├── model/               # Unified content models (MediaItem, Game, etc.)
│   ├── network/             # HTTP client setup, auth management
│   ├── di/                  # Dependency injection (Hilt)
│   └── settings/            # User preferences, server configuration
├── feature/
│   ├── home/                # Unified home screen
│   ├── jellyfin/            # Jellyfin browsing and playback
│   │   ├── data/            # Jellyfin API calls, repository
│   │   ├── ui/              # Media browsing screens
│   │   └── player/          # Video player integration
│   ├── playnite/            # Playnite Web game library
│   │   ├── data/            # GraphQL client, repository
│   │   └── ui/              # Game browsing screens
│   ├── streaming/           # Apollo/Moonlight game streaming
│   │   ├── moonlight/       # moonlight-common-c JNI bridge
│   │   ├── apollo/          # Apollo API client (launch, pair)
│   │   └── ui/              # Stream view, connection screens
│   └── emulation/           # Phase 4: local emulation
│       ├── data/            # ROM scanning, save states
│       └── ui/              # Emulation screens
├── tv/                      # Google TV / Leanback layouts
│   ├── home/                # TV home screen (Leanback rows)
│   ├── browse/              # TV browsing screens
│   └── detail/              # TV detail screens
└── ui/
    ├── theme/               # Shared theming
    ├── components/          # Shared UI components
    └── navigation/          # Navigation graphs (phone + TV)
```

**Key Dependencies (build.gradle):**
```kotlin
// Jellyfin
implementation("org.jellyfin.sdk:jellyfin-core:<version>")
implementation("org.jellyfin.sdk:jellyfin-model:<version>")

// GraphQL (for Playnite Web)
implementation("com.apollographql.apollo3:apollo-runtime:<version>")

// Networking
implementation("com.squareup.okhttp3:okhttp:<version>")
implementation("com.squareup.retrofit2:retrofit:<version>")

// Video playback (for Jellyfin media)
implementation("androidx.media3:media3-exoplayer:<version>")
implementation("androidx.media3:media3-ui:<version>")

// TV support
implementation("androidx.leanback:leanback:<version>")
implementation("androidx.leanback:leanback-tab:<version>")

// DI
implementation("com.google.dagger:hilt-android:<version>")

// moonlight-common-c — added as git submodule, built via CMake/NDK
// LibretroDroid — Phase 4, added as Gradle dependency later
```

**Dependency Injection:**

Use Hilt to provide repository instances, API clients, and platform-specific implementations. Each feature module exposes its dependencies through Hilt modules, keeping them decoupled.

### Unified Content Model

The app needs a common way to represent content regardless of source:

```kotlin
sealed class ContentItem {
    abstract val id: String
    abstract val title: String
    abstract val imageUrl: String?
    abstract val source: ContentSource

    data class Media(
        override val id: String,
        override val title: String,
        override val imageUrl: String?,
        val mediaType: MediaType,      // MOVIE, SERIES, EPISODE
        val jellyfinItemId: String,
        val progress: Float?,           // 0.0-1.0 for continue watching
        val year: Int?,
        val overview: String?
    ) : ContentItem() {
        override val source = ContentSource.JELLYFIN
    }

    data class PcGame(
        override val id: String,
        override val title: String,
        override val imageUrl: String?,
        val apolloAppId: String,        // ID in Apollo's app list
        val platform: String,           // Steam, GOG, Epic, etc.
        val lastPlayed: Instant?,
        val playtime: Duration?
    ) : ContentItem() {
        override val source = ContentSource.PLAYNITE
    }

    data class LocalRom(                // Phase 4
        override val id: String,
        override val title: String,
        override val imageUrl: String?,
        val system: EmulationSystem,
        val romPath: String,
        val coreName: String,
        val saveStatePath: String?
    ) : ContentItem() {
        override val source = ContentSource.LOCAL_EMULATION
    }
}

enum class ContentSource { JELLYFIN, PLAYNITE, LOCAL_EMULATION }
```

### Settings & Configuration

First-run setup flow:
1. **Jellyfin server** — URL, username, password. Validate connection.
2. **Playnite Web server** — URL, credentials. Validate GraphQL endpoint.
3. **Apollo server** — IP address, initiate PIN pairing via moonlight-common-c.
4. **Tailscale** (optional) — Guidance on setup, detect Tailscale IP vs LAN IP.

Stored in encrypted SharedPreferences via AndroidX Security.

---

## Component 3: Jellyfin Integration

### Data Layer

**Authentication:**
```kotlin
// Using the Jellyfin SDK
val jellyfin = createJellyfin {
    clientInfo = ClientInfo(name = "YourApp", version = "1.0.0")
}
val api = jellyfin.createApi(baseUrl = serverUrl)
val authResult = api.userApi.authenticateUserByName(username, password)
// SDK stores auth token internally after this
```

**Key API Calls:**

| Purpose | Endpoint | SDK Method |
|---------|----------|------------|
| Get libraries | `/Users/{userId}/Views` | `userViewsApi.getUserViews()` |
| Browse library items | `/Users/{userId}/Items` | `itemsApi.getItems()` with parentId, filters |
| Get recently added | `/Users/{userId}/Items/Latest` | `userLibraryApi.getLatestMedia()` |
| Continue watching | `/Users/{userId}/Items/Resume` | `itemsApi.getResumeItems()` |
| Get item details | `/Users/{userId}/Items/{itemId}` | `userLibraryApi.getItem()` |
| Get image | `/Items/{itemId}/Images/Primary` | Direct URL construction |
| Get stream URL | `/Videos/{itemId}/stream` | `videosApi` with transcoding params |
| Report playback start | `/Sessions/Playing` | `playStateApi.reportPlaybackStart()` |
| Report playback progress | `/Sessions/Playing/Progress` | `playStateApi.reportPlaybackProgress()` |
| Report playback stopped | `/Sessions/Playing/Stopped` | `playStateApi.reportPlaybackStopped()` |

**Image URLs:**

Jellyfin serves images directly:
```
{serverUrl}/Items/{itemId}/Images/Primary?maxWidth=400&quality=90
```
Use Coil or Glide for image loading with these URLs.

### Video Playback

Use **ExoPlayer (Media3)** for video playback. Jellyfin can direct-play or transcode depending on the device's codec support.

```kotlin
// Basic Jellyfin playback setup
val mediaItem = MediaItem.Builder()
    .setUri(jellyfinStreamUrl)
    .build()

val player = ExoPlayer.Builder(context).build()
player.setMediaItem(mediaItem)
player.prepare()
player.play()
```

Considerations:
- Report playback position to Jellyfin periodically for "Continue Watching" support
- Handle subtitle selection (Jellyfin serves subtitles via its API)
- Support direct play when possible, fall back to transcoding
- On TV: use Media3's Leanback PlayerAdapter for native TV playback controls

### UI Screens (Jellyfin)

**Phone/Tablet:**
- Media library grid with filter tabs (Movies, Shows, Music)
- Detail screen with backdrop, metadata, play button, episode list for series
- Player screen (ExoPlayer with standard controls)

**TV (Leanback):**
- BrowseSupportFragment-style rows: "Continue Watching", "Recently Added Movies", "Recently Added Shows"
- DetailsSupportFragment for item details
- PlaybackSupportFragment for video playback with TV-appropriate controls

---

## Component 4: Playnite Web Integration

### Setup Requirements

On the PC, Playnite Web runs as a Docker Compose stack:
- **Playnite Web App** (Node.js) — serves the UI and GraphQL API
- **MongoDB** — stores synced game data
- **Eclipse Mosquitto** (MQTT broker) — communication bus between Playnite plugin and the web app
- **Playnite Web Plugin** — installed in Playnite, syncs library changes over MQTT

### Data Layer

**GraphQL Client:**

Use Apollo Kotlin (the GraphQL library, not the streaming server — confusing naming) to query Playnite Web's API.

```kotlin
// Example query to fetch games
query GetGames {
    games {
        id
        name
        description
        coverImage
        backgroundImage
        platforms {
            name
        }
        source
        isInstalled
        playCount
        lastActivity
    }
}
```

**Key Data Points to Fetch:**
- Game name, ID, and source platform
- Cover art and background images
- Installation status
- Platform metadata (for categorization)
- Play history (last played, play count, time played)

**Mapping to Apollo App IDs:**

The sync tool assigns each game a deterministic ID when writing to Apollo's `apps.json`. The client app needs to use the same ID derivation logic so it can map a Playnite game to its Apollo app entry for launching. A simple approach: hash of `{source}:{gameId}` truncated to a consistent format.

### UI Screens (Playnite)

**Phone/Tablet:**
- Game library grid with cover art
- Filter by platform/source (Steam, GOG, Epic, All)
- Detail screen with background art, metadata, "Stream" button
- Sort by name, last played, recently added

**TV (Leanback):**
- Rows organized by source or category: "Steam Games", "GOG Games", "Recently Played"
- Large cover art cards with focus animations
- Detail screen with "Stream" action prominent

---

## Component 5: Apollo Streaming Integration

### moonlight-common-c Integration

This is the most technically complex piece. moonlight-common-c is a C library that implements the Moonlight streaming protocol. The Moonlight Android app wraps it via JNI.

**Approach:**

Reference the [Moonlight Android](https://github.com/moonlight-stream/moonlight-android) source code directly. The key files:

```
moonlight-android/
├── app/src/main/java/com/limelight/
│   ├── binding/
│   │   ├── PlatformBinding.java      # JNI bridge to C library
│   │   └── video/
│   │       └── MediaCodecDecoderRenderer.java  # Hardware video decoding
│   ├── nvstream/
│   │   ├── NvConnection.java          # Main connection manager
│   │   └── mdns/                      # Server discovery
│   ├── Game.java                      # Streaming activity
│   └── PcView.java                    # Server browser (we replace this)
└── moonlight-common/                  # The C library (git submodule)
```

**What you take:**
- The entire `moonlight-common` C library as a git submodule
- The JNI binding layer (`PlatformBinding`, decoder renderer)
- The `NvConnection` connection manager
- The video/audio decoder setup

**What you replace:**
- The entire UI (PcView, AppView, etc.) — your unified UI handles browsing
- Server discovery — you already know the Apollo server address from settings
- App launching — you call Apollo's `/api/apps/launch` instead of browsing the app list

### Apollo API Client

Apollo inherits Sunshine's web API. Key endpoints:

```kotlin
class ApolloApiClient(
    private val baseUrl: String,    // e.g., "https://192.168.1.100:47990"
    private val httpClient: OkHttpClient
) {
    // Authenticate with Apollo's web UI
    suspend fun login(username: String, password: String): AuthToken {
        // POST /api/login
        // Body: { "username": "...", "password": "..." }
        // Returns auth cookie/token
    }

    // Launch a specific app by name or ID
    suspend fun launchApp(appId: String): Boolean {
        // POST /api/apps/launch
        // Body: { "id": appId } or { "name": appName }
        // Apollo starts the app on the PC
    }

    // Get list of registered apps (to verify sync worked)
    suspend fun getApps(): List<ApolloApp> {
        // GET /api/apps
        // Returns the apps.json content
    }
}
```

### Streaming Flow

When the user selects a PC game:

```
1. User taps game in the UI
2. App calls ApolloApiClient.login() if not already authenticated
3. App calls ApolloApiClient.launchApp(apolloAppId) 
   → Apollo starts the game on the PC
4. App creates NvConnection with Apollo's IP and the paired client cert
5. NvConnection.start() initiates the Moonlight protocol handshake
6. Video frames arrive via hardware decoder → rendered to a SurfaceView
7. Controller/touch input captured → sent back via NvConnection
8. On disconnect: NvConnection.stop(), Apollo handles cleanup
```

### Pairing

First-time pairing with Apollo uses the Moonlight PIN exchange:

1. App generates a client certificate and key pair (stored in Android Keystore)
2. App sends a pairing request to Apollo's HTTPS API
3. Apollo displays a 4-digit PIN on its web UI
4. User enters PIN in the app
5. App and Apollo exchange certificates
6. Future connections use the stored certificates — no re-pairing needed

This logic exists in Moonlight Android's `NvHTTP` and `PairingManager` classes. Port these directly.

### Stream View

The streaming screen is a fullscreen `SurfaceView` (or `TextureView`) that:
- Receives and renders decoded video frames
- Captures controller input (gamepad buttons, analog sticks, triggers)
- Captures touch input and translates to mouse events (if needed)
- Provides an overlay menu (accessed via a button combo or edge swipe) for:
  - Disconnect
  - Toggle on-screen controls
  - Adjust bitrate/resolution
  - Toggle virtual display settings

On TV: the overlay is accessed via a specific button combo (e.g., Guide + Select).

### Performance Considerations

- Use hardware H.265/HEVC decoding where available (most modern Android devices)
- Fall back to H.264 on older hardware
- Default to 1080p60 on WiFi, allow user to configure up to 4K60 if network supports it
- Use Apollo's virtual display to match client resolution — avoids scaling artifacts
- Low latency audio via AudioTrack in low-latency mode

---

## Component 6: Unified UI Design

### Home Screen Philosophy

One screen, all content. The user shouldn't think about which service something comes from — they just see their stuff.

### Phone/Tablet Home Screen

```
┌─────────────────────────────────┐
│  [Settings gear]    Your App    │
├─────────────────────────────────┤
│                                 │
│  Continue Watching              │
│  ┌─────┐ ┌─────┐ ┌─────┐      │
│  │Movie│ │Show │ │Game │ →     │
│  │ img │ │ img │ │ img │       │
│  └─────┘ └─────┘ └─────┘      │
│                                 │
│  Recently Added                 │
│  ┌─────┐ ┌─────┐ ┌─────┐      │
│  │     │ │     │ │     │ →     │
│  └─────┘ └─────┘ └─────┘      │
│                                 │
│  PC Games                       │
│  ┌─────┐ ┌─────┐ ┌─────┐      │
│  │     │ │     │ │     │ →     │
│  └─────┘ └─────┘ └─────┘      │
│                                 │
│  Movies                         │
│  ┌─────┐ ┌─────┐ ┌─────┐      │
│  │     │ │     │ │     │ →     │
│  └─────┘ └─────┘ └─────┘      │
│                                 │
│  TV Shows                       │
│  ┌─────┐ ┌─────┐ ┌─────┐      │
│  │     │ │     │ │     │ →     │
│  └─────┘ └─────┘ └─────┘      │
│                                 │
│  ┌─────┬──────┬──────┬─────┐   │
│  │Home │Games │Media │Search│   │
│  └─────┴──────┴──────┴─────┘   │
└─────────────────────────────────┘
```

**Row Data Sources:**
- "Continue Watching" — merges Jellyfin resume items + Playnite recently played, sorted by recency
- "Recently Added" — merges Jellyfin latest media + newly synced Playnite games
- "PC Games" — all games from Playnite Web, sorted by last played
- "Movies" — Jellyfin movie library
- "TV Shows" — Jellyfin series library
- Phase 4 adds: "Retro Games" rows organized by system

**Bottom Navigation:**
- **Home** — the unified view above
- **Games** — dedicated game library with filters (all from Playnite, later also local ROMs)
- **Media** — dedicated media browser with Jellyfin library tabs
- **Search** — unified search across all sources

### Google TV Home Screen (Leanback)

Same row-based structure, but using Leanback components:

```
┌──────────────────────────────────────────┐
│  ▓▓▓▓▓▓▓▓▓▓ FEATURED CONTENT ▓▓▓▓▓▓▓▓▓ │
│  ▓▓▓▓▓▓▓▓▓▓ (hero backdrop)  ▓▓▓▓▓▓▓▓▓ │
│  ▓▓▓▓▓▓▓▓▓▓                  ▓▓▓▓▓▓▓▓▓ │
├──────────────────────────────────────────┤
│                                          │
│  Continue Watching                       │
│  [▪ Movie ] [▪ Show  ] [▪ Game  ] →     │
│                                          │
│  PC Games                                │
│  [▪ Game1 ] [▪ Game2 ] [▪ Game3 ] →     │
│                                          │
│  Recently Added Movies                   │
│  [▪ Movie1] [▪ Movie2] [▪ Movie3] →     │
│                                          │
│  Recently Added Shows                    │
│  [▪ Show1 ] [▪ Show2 ] [▪ Show3 ] →     │
│                                          │
└──────────────────────────────────────────┘
```

Uses `BrowseSupportFragment` or Compose equivalents with:
- `ListRowPresenter` for each content row
- `CardPresenter` for individual items (large artwork, title below)
- Focus-based navigation — D-pad moves between cards, rows scroll horizontally
- Featured content banner at top cycles through highlighted items

### Shared Data Layer

Both UIs consume the same `HomeRepository`:

```kotlin
class HomeRepository @Inject constructor(
    private val jellyfinRepo: JellyfinRepository,
    private val playniteRepo: PlayniteRepository,
    // Phase 4: private val emulationRepo: EmulationRepository
) {
    fun getContinueWatching(): Flow<List<ContentItem>> {
        return combine(
            jellyfinRepo.getResumeItems(),
            playniteRepo.getRecentlyPlayed()
        ) { media, games ->
            (media + games).sortedByDescending { it.lastInteraction }
        }
    }

    fun getRecentlyAdded(): Flow<List<ContentItem>> { ... }
    fun getPcGames(): Flow<List<ContentItem.PcGame>> { ... }
    fun getMovies(): Flow<List<ContentItem.Media>> { ... }
    fun getTvShows(): Flow<List<ContentItem.Media>> { ... }
    fun search(query: String): Flow<List<ContentItem>> { ... }
}
```

### Navigation & Item Actions

When a user selects any `ContentItem`, the app routes to the appropriate action:

```kotlin
fun onContentSelected(item: ContentItem) {
    when (item) {
        is ContentItem.Media -> {
            if (item.mediaType == MediaType.SERIES) {
                navigateToSeriesDetail(item)
            } else {
                navigateToMediaDetail(item)
            }
        }
        is ContentItem.PcGame -> {
            navigateToGameDetail(item)
            // Detail screen has "Stream" button that triggers:
            // 1. apolloApi.launchApp(item.apolloAppId)
            // 2. Navigate to StreamView
        }
        is ContentItem.LocalRom -> {
            // Phase 4: launch LibretroDroid directly
        }
    }
}
```

---

## Component 7: Google TV Specifics

### Detection

```kotlin
fun isTvDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
```

Use this at the activity/navigation level to load TV vs phone layouts.

### Manifest Configuration

```xml
<!-- Support both phone and TV -->
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />

<!-- TV launcher banner -->
<application android:banner="@drawable/tv_banner">
    <!-- Phone launcher activity -->
    <activity android:name=".MainActivity">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <!-- TV launcher activity -->
    <activity android:name=".TvActivity">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

### Input Handling

| Input | Phone/Tablet | Google TV |
|-------|-------------|-----------|
| Navigation | Touch scroll, tap | D-pad focus, remote buttons |
| Game streaming | Touch overlay OR gamepad | Gamepad (required for most games) |
| Media playback | Touch controls | Remote play/pause/seek |
| Text entry | On-screen keyboard | TV keyboard or voice input |
| Settings | Touch forms | Focus-navigable forms |

### Reference Implementation

Study the [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv) client for:
- How they structure Leanback fragments
- Video playback integration on TV
- How they handle the settings UI on TV
- How they detect and adapt to different TV hardware

---

## Component 8: Networking & Security

### Tailscale Setup

Recommended for remote access:
1. Install Tailscale on the gaming PC and the Android device
2. Both devices join the same Tailnet
3. The PC gets a stable Tailscale IP (e.g., `100.x.y.z`)
4. The app stores both the LAN IP and the Tailscale IP for the server

**Auto-detection logic:**
```kotlin
suspend fun resolveServerAddress(): String {
    // Try LAN IP first (faster, lower latency)
    if (isReachable(lanIp, timeout = 1000)) return lanIp
    // Fall back to Tailscale IP
    if (isReachable(tailscaleIp, timeout = 3000)) return tailscaleIp
    throw ServerUnreachableException()
}
```

### Apollo/Moonlight Security

- Pairing uses client certificates stored in Android Keystore
- Streaming connections are encrypted via the Moonlight protocol
- Apollo's web API uses HTTPS (self-signed cert by default — the app should trust it after initial pairing)

### Jellyfin Security

- API key or user token stored in encrypted SharedPreferences
- All communication over HTTPS (Jellyfin supports it natively, or via reverse proxy)

---

## Update & Maintenance Strategy

### Server-Side Components

| Component | How to Update | Risk to Your Project |
|-----------|--------------|---------------------|
| Playnite | Standard installer update | Low — you read its file format, which is stable. Playnite 11 rewrite may change DB format. |
| Playnite Web | Docker image pull | Low — GraphQL schema changes would need client updates. |
| Apollo | Download new release from GitHub | Medium — `apps.json` format or API endpoints could change. Monitor release notes. |
| Jellyfin | Standard installer/Docker update | Low — SDK handles API versioning. |

### Client Dependencies

| Dependency | Consumed As | Update Method |
|-----------|------------|---------------|
| Jellyfin SDK | Gradle dependency | Bump version in `build.gradle` |
| Apollo Kotlin (GraphQL) | Gradle dependency | Bump version in `build.gradle` |
| moonlight-common-c | Git submodule | `git submodule update --remote`, rebuild |
| LibretroDroid (Phase 4) | Gradle dependency | Bump version in `build.gradle` |
| ExoPlayer/Media3 | Gradle dependency | Bump version in `build.gradle` |
| Leanback | Gradle dependency | Bump version in `build.gradle` |

### Monitoring for Breaking Changes

- Watch Apollo's GitHub releases for API/config format changes
- Watch Playnite Web's releases for GraphQL schema changes
- Watch moonlight-common-c for protocol changes (rare — the Moonlight protocol is mature)
- Pin dependency versions and update deliberately, not automatically

---

## Build Order — Detailed

### Phase 1: Foundation (Weeks 1–4)

**Step 1: Sync Tool (Week 1)**
- [ ] Set up Python project
- [ ] Write Playnite library reader (parse game JSON files from `%AppData%\Playnite\library\games\`)
- [ ] Implement launch command mapping (Steam, GOG, Epic, generic exe)
- [ ] Write Apollo `apps.json` writer
- [ ] Implement artwork copying
- [ ] Test: verify games appear in Apollo's web UI and Moonlight/Artemis client
- [ ] Add incremental sync (update/remove, not just append)
- [ ] Optional: package as a Playnite extension for auto-triggering on library changes

**Step 2: Scaffold the App (Week 2)**
- [ ] Create new Android project with Kotlin + Jetpack Compose
- [ ] Set up Hilt dependency injection
- [ ] Define the modular package structure
- [ ] Define `ContentItem` sealed class and `ContentSource` enum
- [ ] Set up the navigation graph (phone/tablet)
- [ ] Create a basic settings screen (server URLs, credentials)
- [ ] Add encrypted storage for credentials
- [ ] Add TV detection and separate `TvActivity` entry point
- [ ] Add Leanback dependencies and a placeholder TV home screen

**Step 3: Jellyfin Integration (Weeks 3–4)**
- [ ] Add Jellyfin SDK dependency
- [ ] Implement `JellyfinRepository` (auth, libraries, items, images, search)
- [ ] Build media browsing grid (movies, shows)
- [ ] Build media detail screen (backdrop, metadata, episode list)
- [ ] Integrate ExoPlayer for video playback
- [ ] Implement playback reporting (start, progress, stop) for "Continue Watching"
- [ ] Build TV equivalents: BrowseFragment rows, DetailFragment, PlaybackFragment
- [ ] Test: browse and play media on both phone and TV

### Phase 2: Game Streaming (Weeks 5–8)

**Step 4: Playnite Web Integration (Week 5)**
- [ ] Add Apollo Kotlin (GraphQL) dependency
- [ ] Define GraphQL queries matching Playnite Web's schema
- [ ] Implement `PlayniteRepository` (games, platforms, artwork, search)
- [ ] Build game library browsing grid with cover art
- [ ] Build game detail screen with metadata
- [ ] Add ID mapping logic so Playnite games resolve to Apollo app IDs
- [ ] Build TV equivalents: game rows, game detail
- [ ] Test: browse full game library on both phone and TV

**Step 5: Apollo Streaming (Weeks 6–7)**
- [ ] Add moonlight-common-c as a git submodule
- [ ] Set up NDK/CMake build for the C library
- [ ] Port JNI binding layer from Moonlight Android
- [ ] Port `NvConnection`, certificate management, and pairing logic
- [ ] Implement `ApolloApiClient` (login, launch app, list apps)
- [ ] Build the pairing flow UI (PIN entry, certificate exchange)
- [ ] Build the streaming `SurfaceView` / activity
- [ ] Implement controller input capture and forwarding
- [ ] Wire it all together: tap game → launch via API → start stream → render
- [ ] Build TV streaming view with appropriate overlay controls
- [ ] Test: stream a game from Apollo on both phone and TV

**Step 6: Unify the UI (Week 8)**
- [ ] Build `HomeRepository` that merges Jellyfin + Playnite data
- [ ] Build unified home screen with mixed content rows
- [ ] Implement "Continue Watching" that merges media progress + recent games
- [ ] Implement unified search across both sources
- [ ] Build TV home screen with merged Leanback rows
- [ ] Add bottom navigation (phone) with Home / Games / Media / Search tabs
- [ ] Test: full end-to-end flow — browse media, play it, browse games, stream them

### Phase 3: Polish (Weeks 9–10)

**Step 7: Refinement**
- [ ] Implement LAN vs Tailscale auto-detection
- [ ] Add per-game settings (preferred resolution, bitrate, codec)
- [ ] Refine controller mapping and input handling
- [ ] Add error handling and retry logic for all network calls
- [ ] Add loading states, empty states, and offline messaging
- [ ] Add a connection status indicator (local / remote / disconnected)
- [ ] Performance optimization: image caching, pagination for large libraries
- [ ] Handle edge cases: Apollo server offline, Jellyfin unreachable, game fails to launch
- [ ] Test on a variety of devices: phones, tablets, Google TV boxes, Android TV handhelds
- [ ] Add setup documentation and Tailscale configuration guidance

### Phase 4: Local Emulation (Later)

**Step 8: LibretroDroid Integration**
- [ ] Add LibretroDroid as a Gradle dependency
- [ ] Build `EmulationRepository` with ROM scanning logic (reference Lemuroid)
- [ ] Implement core downloading and management
- [ ] Build emulation launch flow: select ROM → load core → start LibretroDroid
- [ ] Implement save state management (save, load, auto-save)
- [ ] Implement controller mapping for emulated games
- [ ] Build on-screen touch controls for phone (reference Lemuroid's implementation)

**Step 9: ROM Management**
- [ ] Implement local ROM directory scanning
- [ ] Add network ROM fetching: SMB/SFTP client to pull ROMs from the PC
- [ ] Implement on-device ROM caching (download once, play offline)
- [ ] Build a ROM management screen (scan, delete cached, configure directories)

**Step 10: Integrate into Unified UI**
- [ ] Add `ContentItem.LocalRom` to the content model
- [ ] Add emulation rows to the home screen ("SNES Games", "GBA Games", etc.)
- [ ] Add the emulation tab to the Games section
- [ ] Implement per-game choice: run locally via LibretroDroid OR stream from PC via Apollo
- [ ] Build TV equivalents for all emulation screens
- [ ] Test full flow: browse ROMs alongside PC games and media, launch locally or stream

---

## Risk Assessment

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Apollo API changes in new releases | Sync tool and streaming break | Medium | Pin Apollo version, monitor releases, abstract API calls behind an interface |
| Playnite 11 rewrite changes DB format | Sync tool breaks | High (confirmed rewrite in progress) | Support both formats, or switch to Playnite Web's GraphQL as the sole data source |
| moonlight-common-c protocol changes | Streaming breaks | Low (protocol is stable) | Pin submodule version, update deliberately |
| Artemis becomes incompatible with Moonlight protocol | Need to switch libraries | Medium (developer has stated divergence plans) | moonlight-common-c is maintained separately from Artemis; if needed, evaluate Artemis's client library instead |
| Jellyfin SDK major version bump | Media integration breaks | Low | SDK is versioned and well-documented |
| Google TV Leanback library deprecated | TV UI needs rework | Low-Medium | Google is moving toward Compose for TV; plan eventual migration |
| LibretroDroid stops being maintained | Emulation layer stalls | Low | Library is stable; worst case, fork it |
| Playnite Web project abandoned | Game library data source lost | Medium | Fall back to reading Playnite's local files via the sync tool, expose them through a simple REST API |

---

## Technology Summary

| Purpose | Technology | License |
|---------|-----------|---------|
| App language | Kotlin | Apache 2.0 |
| Phone UI | Jetpack Compose | Apache 2.0 |
| TV UI | Leanback + Compose | Apache 2.0 |
| Media SDK | Jellyfin SDK | LGPL / MIT |
| GraphQL client | Apollo Kotlin | MIT |
| Video player | ExoPlayer / Media3 | Apache 2.0 |
| Streaming protocol | moonlight-common-c | GPL-3.0 |
| Local emulation (Phase 4) | LibretroDroid | LGPL-3.0 |
| Dependency injection | Hilt | Apache 2.0 |
| Image loading | Coil | Apache 2.0 |
| Networking | OkHttp + Retrofit | Apache 2.0 |
| Sync tool | Python | — |
| Remote access | Tailscale | BSD-3-Clause |

**Licensing note:** moonlight-common-c is GPL-3.0. Since you're linking it into your app via JNI, the app itself would need to be GPL-3.0 compatible (or you'd need to evaluate whether the system library exception applies to your distribution model). If this is a personal project, this is a non-issue. If you plan to distribute it, consult the GPL-3.0 terms.
