# Fix Plan for AppoJellyNite

## Phase 1: Critical — Won't Compile

### 1.1 Add .gitignore
Create `android-app/.gitignore` with standard Android entries:
- `build/`, `.gradle/`, `.idea/`, `local.properties`, `*.apk`, `*.aab`
- Also add root `.gitignore` for `.idea/`, `*.pyc`, etc.

### 1.2 Fix Apollo GraphQL codegen
**File:** `android-app/app/build.gradle.kts`

The `apollo {}` block has `schemaFiles.from(...)` but is missing `operationFiles`. However, Apollo 4.x auto-discovers `.graphql` files under `src/main/graphql/` by default when the schema is also there. The real issue is that the `schemaFiles` path may conflict with auto-discovery.

**Fix:** Change the apollo config to use `srcDir` which tells Apollo where both schema and operations live:
```kotlin
apollo {
    service("playniteWeb") {
        packageName.set("com.appojellyapp.feature.playnite.graphql")
        srcDir("src/main/graphql")
    }
}
```

Also verify that `schema.graphqls` and `GetGames.graphql` are both in `src/main/graphql/` (they are).

The generated classes (`GetGamesQuery`, `GameFilter`, `GetGameQuery`) will be placed in `com.appojellyapp.feature.playnite.graphql` — matching what `PlayniteRepository.kt` references.

### 1.3 Add Gradle wrapper scripts
Generate `gradlew` and `gradlew.bat` in `android-app/`. These are standard boilerplate scripts that invoke `gradle-wrapper.jar`. We also need to add `gradle/wrapper/gradle-wrapper.jar` since just having `gradle-wrapper.properties` isn't sufficient.

**Approach:** Run `cd android-app && gradle wrapper --gradle-version 8.11.1` if Gradle is available, otherwise create the standard `gradlew` and `gradlew.bat` scripts directly (they're always the same content).

---

## Phase 2: High Priority — Won't Run Correctly

### 2.1 Acknowledge moonlight-common-c as stub (document, don't implement)
The `StreamManager.kt` is already documented as a stub (lines 111-119 say "requires porting the JNI bridge"). This is an architectural decision that requires significant native development.

**Fix:** Add a clear `TODO` and documentation comment at the class level. Do NOT attempt to implement the full native bridge — that's a separate project. Ensure the streaming flow degrades gracefully with a clear error message when actual streaming is attempted.

No code changes needed beyond what's already there — the stub is honest about what it does.

### 2.2 TV Leanback improvements
**Files to create/modify:**
- **`ContentCardPresenter`** (in `TvHomeFragment.kt`): Add Coil image loading in `onBindViewHolder`
- **Create `TvDetailFragment.kt`**: New fragment for TV item detail view with play action
- **Create `TvPlaybackFragment.kt`**: Wrap Media3 ExoPlayer with Leanback `VideoSupportFragment` for TV playback controls
- Update `TvHomeFragment.onItemViewClickedListener` to navigate to `TvDetailFragment` for media items

**Won't implement (too large for this pass):**
- TV settings screen — requires full Leanback GuidedStepFragment implementation
- TV pairing flow — depends on moonlight-common-c integration

### 2.3 Add episode list for series in MediaDetailScreen
**File:** `feature/jellyfin/ui/MediaDetailScreen.kt` and `MediaDetailViewModel.kt`

- Add `getEpisodes(seriesId: String)` to `JellyfinRepository` — query items with `parentId` and `BaseItemKind.EPISODE`
- Add episodes state to `MediaDetailViewModel`
- Show episode list in `MediaDetailScreen` when the item is a `SERIES` type, with season grouping

### 2.4 Add transcoding fallback to player
**Files:** `JellyfinRepository.kt`, `PlayerViewModel.kt`

- Add `getTranscodingStreamUrl(itemId)` that returns a URL without `?static=true` (uses Jellyfin's built-in transcoding)
- Add device capability detection (check supported codecs via `MediaCodecList`)
- `PlayerViewModel` should try direct play first, fall back to transcoding URL on player error

### 2.5 Stub subtitle support
**Files:** `JellyfinRepository.kt`, `PlayerScreen.kt`, `PlayerViewModel.kt`

- Add `getSubtitleTracks(itemId)` to `JellyfinRepository`
- Add subtitle track selection UI in `PlayerScreen` (simple dropdown/dialog)
- Pass selected subtitle to ExoPlayer via `MediaItem.SubtitleConfiguration`

---

## Phase 3: Medium Priority — Polish

### 3.1 Per-game stream settings UI
**File:** Create `feature/streaming/ui/StreamSettingsDialog.kt`

- Composable dialog that lets user pick resolution (720p/1080p/4K), FPS (30/60), bitrate, codec (H264/H265)
- Call `StreamManager.updateConfig()` with the selection
- Show this dialog from `GameDetailScreen` before launching stream

### 3.2 Connection status indicator
**Files:** `core/network/NetworkHelper.kt`, create `ui/components/ConnectionStatusBar.kt`

- Add a `connectionState: StateFlow<ConnectionState>` to `NetworkHelper` (enum: LAN, TAILSCALE, DISCONNECTED)
- Composable status bar that observes the state and shows a colored indicator
- Include in `HomeScreen` layout

### 3.3 Error handling and retry logic
**Files:** Multiple repository and ViewModel files

- Wrap repository calls in a `Result<T>` or sealed class `UiState<T>` (Loading/Success/Error)
- Add `retryWhen` or manual retry (1-2 attempts with backoff) in repository Flows
- ViewModels expose `UiState<T>` instead of raw data
- Screens show error messages with retry buttons

### 3.4 Loading/empty/offline states
**Files:** All screen composables (`HomeScreen`, `MediaBrowseScreen`, `GameBrowseScreen`, `SearchScreen`)

- Create a shared `UiState<T>` sealed class in `core/model/`
- Each screen handles: Loading (shimmer/spinner), Empty ("No content available"), Error/Offline ("Can't reach server. Check your connection." + retry)

### 3.5 Image caching and pagination
**File:** `build.gradle.kts` dependencies already include Coil. For caching:
- Configure `ImageLoader` in `AppoJellyApp.kt` with explicit disk cache size
- For pagination: add `limit`/`offset` parameters to repository methods and use Paging 3 library for `MediaBrowseScreen` and `GameBrowseScreen`

### 3.6 Add isTvDevice() utility
**File:** Create `core/util/DeviceUtils.kt`

```kotlin
fun isTvDevice(context: Context): Boolean {
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}
```

Use this in `MainActivity` to optionally redirect to `TvActivity`.

---

## Phase 4: Low Priority — Missing Project Files

### 4.1 ProGuard rules for remaining libraries
**File:** `app/proguard-rules.pro`

Add rules for:
- Jellyfin SDK (keep model classes)
- Coil (OkHttp integration)
- Media3/ExoPlayer

### 4.2 Add placeholder launcher icon
Create `app/src/main/res/mipmap-*` directories with a basic adaptive icon (ic_launcher.xml foreground + background) so the build doesn't fail on missing resource.

### 4.3 Fix tv_banner.xml
Replace the purple rectangle with a proper branded banner (320x180dp as required by Leanback).

---

## Phase 5: Structural Code Fixes

### 5.1 Fix JellyfinRepository unused imports
**File:** `feature/jellyfin/data/JellyfinRepository.kt`

Remove unused imports:
- `org.jellyfin.sdk.api.client.extensions.imageApi`
- `org.jellyfin.sdk.api.client.extensions.userViewsApi`
- `org.jellyfin.sdk.model.api.ImageType`

### 5.2 Fix SearchScreen unused imports
**File:** `feature/home/ui/SearchScreen.kt`

Remove unused imports:
- `Row`
- `width`

### 5.3 Note on CertificateManager
The issue description mentions `CertificateManager.generateSelfSignedCert()` has a broken cert flow. This is part of the moonlight-common-c integration (Phase 2.1) and won't matter until native streaming is implemented. **Skip for now** — document the issue in a TODO comment if the file exists.

---

## Implementation Order

1. **Phase 1** (Critical): .gitignore → Apollo codegen fix → Gradle wrapper
2. **Phase 5** (Quick wins): Remove unused imports, fix compile errors
3. **Phase 2.3-2.5** (Functional): Episode list, transcoding fallback, subtitles
4. **Phase 2.2** (TV): TvDetailFragment, TvPlaybackFragment, image loading
5. **Phase 4** (Project files): Icons, ProGuard, banner
6. **Phase 3** (Polish): UiState, error handling, connection status, settings UI, pagination
7. **Phase 2.1** (Native): Document moonlight stub status
