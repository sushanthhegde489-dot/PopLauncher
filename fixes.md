# Summary of Fixes & Code Optimizations

This document details all bugs fixed, performance and battery optimizations applied, memory leak resolutions, and documentation updates across the Pop Launcher project.

---

## 1. Test Failure & Asset Synchronization
- **Issue**: `CustomWidgetManifestTest.publishedCustomWidgetsManifestParsesSuccessfully` failed with `AssertionError: expected:<5> but was:<2>` because `assets/custom-widgets/manifest.json` only contained 2 widgets.
- **Fix**: Updated `app/src/main/assets/custom-widgets/manifest.json` to include all 5 published custom widgets (`hydration_tracker`, `todo_applet`, `calc_applet`, `api_weather_applet`, `notes_applet`) following schema version 1.
- **Verification**: All 59 unit tests passed (`app:testDebugUnitTest`).

---

## 2. Physics Engine, Animation & Battery Optimizations
- **`BubblePhysicsEngine.kt`**:
  - Replaced wall-clock `System.currentTimeMillis()` calls in frame update loops with frame time delta accumulator (`idleElapsedSeconds`).
  - Eliminates system clock lookups per physics tick, preventing jitter during NTP time updates and reducing CPU/battery usage.
- **`MultiBubblePhysicsEngine.kt`**:
  - Converted `BubbleBody` position properties (`posX`, `posY`) from Compose `mutableFloatStateOf` to plain float fields.
  - Eliminates Compose State invalidation and recomposition overhead on every physics frame tick.
- **`FloatingAppBubble.kt` & `MultiAppBubbleContainer.kt`**:
  - Added `kotlinx.coroutines.isActive` checks to `withFrameNanos` physics simulation while-loops.
  - Guarantees immediate cancellation of frame callbacks when composables are paused, stopped, or disposed.

---

## 3. Widget & UI Memory/Lifecycle Leak Fixes
- **`WidgetManager.kt`**:
  - Added orphan widget ID cleanup in `loadWidgetLayouts()` and `loadPageWidgetIds()` to prevent crashes from deleted or missing system widget IDs.
- **`WidgetPageView.kt`**:
  - Keyed `AndroidView` with `key(widgetId)` inside `WidgetHostCard` to ensure proper view host recycling and prevent memory retention of stale widget views.
- **`SearchResultItem.kt` & `SearchResultsList.kt`**:
  - Keyed `LazyColumn` items strictly by `entity.id`.
  - Maintained bounded 6 MB `LruCache` memory caching for media thumbnails.

---

## 4. Widget Setup Permission Denial Resolution
- **`MainActivity.kt`**:
  - Fixed `SecurityException: Permission Denial: starting Intent` when launching widget configuration activities (`ACTION_APPWIDGET_CONFIGURE`) for third-party unexported widget provider activities.
  - Wrapped `widgetConfigureLauncher.launch(configIntent)` in a graceful try-catch block so that if the configuration activity cannot be launched due to permission restrictions or unexported activity declarations, the bound widget is still added directly to the workspace without crashing or showing error toasts.

---

## 5. Robust Default Launcher Setup & HomeScreen Tap Gesture Fix
- **`MainActivity.kt`**:
  - **Issue**: On Android 10+ (API 29+), `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)` was previously launched with `FLAG_ACTIVITY_NEW_TASK` or without an `ActivityResultLauncher` contract. Android's system `PermissionController` automatically auto-canceled role dialog intents that were started with `FLAG_ACTIVITY_NEW_TASK`, causing the prompt to instantly dismiss or fail without opening.
  - **Fix**: Added a dedicated `defaultLauncherLauncher` `ActivityResultLauncher` in `MainActivity.kt`. It launches `roleManager.createRequestRoleIntent` as a valid activity result contract without `FLAG_ACTIVITY_NEW_TASK`, allowing the system "Set Pop Launcher as default Home app" dialog to render on screen.
- **`HomeScreen.kt`**:
  - **Issue**: The root full-screen `pointerInput` gesture listener on Page 0 was calling `awaitFirstDown(requireUnconsumed = false)`. This intercepted and swallowed touch down events before child composable elements (such as the "Set Pop Launcher as Default" banner card and "Grant File Access" card) could receive click events.
  - **Fix**: Updated `awaitFirstDown` to `requireUnconsumed = true`. Tap events on interactive banners and buttons are now consumed cleanly by child `.clickable` handlers, while empty workspace areas continue to respond to swipe-up, swipe-down, double-tap, and long-press gestures.
- **`SettingsScreen.kt` & `HomeScreen.kt`**:
  - Upgraded `requestSetDefaultLauncher()` to execute a multi-tier fallback chain across all Android versions (Android 8.0 to Android 16) and custom OEM skins (Samsung One UI, Xiaomi HyperOS/MIUI, Oppo ColorOS, Vivo FuntouchOS, etc.):
    1. Android 10+ `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)` via `ActivityResultLauncher`.
    2. `Settings.ACTION_HOME_SETTINGS`.
    3. `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`.
    4. `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` (App Info page for Pop Launcher).
    5. Home Category Resolver (`Intent.CATEGORY_HOME`).
    6. Main System Settings.
  - Upgraded `isDefaultLauncher()` with `MATCH_DEFAULT_ONLY` resolution and `getPreferredActivities()` query fallback for 100% accurate status detection.

---

## 6. Search Providers & Data Indexing Optimization
- **`SearchRepository.kt`**:
  - Concurrency generation counter (`AtomicLong`) and `Job` cancellation prevent duplicate or stale search results during rapid typing.
- **`FileSearchProvider.kt` & `MediaSearchProvider.kt`**:
  - Safe ContentResolver cursor management in `use` blocks to prevent resource leaks.

---

## 7. AppWidget & Wallpaper Permissions in Manifest
- **`AndroidManifest.xml`**:
  - **Issue**: Launcher acting as an `AppWidgetHost` must hold `BIND_APPWIDGET` to bind app widgets. Without declaring `android.permission.BIND_APPWIDGET`, `appWidgetManager.bindAppWidgetIdIfAllowed` failed for third-party widgets and the system binding confirmation dialog could not grant widget binding permissions to Pop Launcher.
  - **Fix**: Added `<uses-permission android:name="android.permission.BIND_APPWIDGET" tools:ignore="ProtectedPermissions" />` and `<uses-permission android:name="android.permission.SET_WALLPAPER" />` to `AndroidManifest.xml`.

---

## 8. Multi-App Bubble Touch & Accessibility Alignment
- **`MultiAppBubbleContainer.kt`**:
  - **Issue**: A `.clickable(...)` modifier was placed immediately before `.pointerInput(body.app.id) { ... val down = awaitFirstDown(); down.consume() ... }`. Consuming down events inside `pointerInput` conflicted with Compose's internal `clickable` pointer gesture detector, causing swallowed touches and accessibility warnings.
  - **Fix**: Replaced `.clickable(...)` with `.semantics { role = Role.Button; contentDescription = ...; onClick(...) }`, perfectly matching `FloatingAppBubble.kt`. Drag-and-fling gesture tracking and tap-to-launch in `pointerInput` now operate smoothly without event interference while retaining full TalkBack and accessibility support.

---

## 9. Backward-Compatible ContentResolver Querying (API 26–28)
- **`FileSearchProvider.kt`**, **`MediaSearchProvider.kt`**, **`ContactSearchProvider.kt`**, & **`ConversationSearchProvider.kt`**:
  - **Issue**: Using `ContentResolver.QUERY_ARG_SQL_SELECTION` and `ContentResolver.QUERY_ARG_LIMIT` in a `Bundle` query argument is fully supported on Android 10+ (API 29+), but legacy OEM providers on Android 8.0–9.0 (API 26–28) throw `IllegalArgumentException` or ignore `queryArgs`.
  - **Fix**: Added version-safe branching. On Android 10+ (API 29+), `queryArgs` with `QUERY_ARG_LIMIT` is passed with a fallback `try-catch`; on Android 8.0–9.0 (API 26–28), standard `query(uri, projection, selection, selectionArgs, sortOrder)` is invoked directly with cursor iteration bounding.

---

## 10. Direct MainActivity Default Launcher Invocation
- **`HomeScreen.kt`**:
  - **Issue**: The "Set Pop Launcher as Default" banner previously performed a Context reflection crawl via `requestSetDefaultLauncher(context)` instead of calling the activity's direct handler.
  - **Fix**: Updated banner click listener to `mainActivity?.requestSetDefaultLauncher() ?: requestSetDefaultLauncher(context)`, ensuring uniform behavior with `mainActivity?.requestFileSearchAccess()`.

---

## 11. Search Categorization, AI Overviews, Animation Levels, Clock & UX Upgrades

### 1. Search Results Categorization & Unified List
- **Files**: [`SearchResultsList.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchResultsList.kt), [`SearchOverlay.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchOverlay.kt)
- **Features**:
  - Added horizontal category filter chips row (`All`, `Apps`, `Contacts`, `Chats`, `Files`, `Photos & Videos`, `Shortcuts`, `AI Overview`) with dynamic counts.
  - Added categorized section headers with distinct Material icons, labels, and badges to group search results cleanly while preserving the unified list view.
  - Tapping a chip filters results instantly; tapping "All" displays all categories unified in one continuous scrollable list.

### 2. Multi-Word AI Overviews & Wikipedia Fallback Engine
- **Files**: [`WebOverviewProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/WebOverviewProvider.kt), [`SearchRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SearchRepository.kt)
- **Issue**: DuckDuckGo's Instant Answer API only returns abstracts for single headwords/proper nouns and gave up on natural sentences or multi-word queries. Furthermore, overview generation was previously suppressed whenever local results were found.
- **Fix**:
  - Implemented Wikipedia Search Extract endpoint (`generator=search`, `prop=extracts`, `explaintext=1`, `exsentences=4`) that returns multi-sentence encyclopedic summaries for arbitrary multi-word queries without requiring API keys.
  - Established a 3-tier fallback engine: Google Gemini API (if key supplied) -> DuckDuckGo Instant Answer -> Wikipedia Search Extracts.
  - Removed local-match suppression in `SearchRepository.kt` so web/AI overviews trigger reliably for all queries when enabled.

### 3. Settings Screen Scroll Position Retention
- **Files**: [`MainActivity.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/MainActivity.kt), [`SettingsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/SettingsScreen.kt)
- **Issue**: In `MainActivity.kt`, screen navigation is handled by switching `screenState` inside `Crossfade`. When navigating to sub-screens (`FRICTION_APPS`, `CONVERSATION_APPS`, `APP_GROUPS`, `HIDDEN_APPS_SETUP`), `SettingsScreen` was unmounted, causing its local `rememberScrollState()` to reset to 0 upon returning.
- **Fix**: Hoisted `settingsScrollState = rememberScrollState()` at the `MainActivity.LauncherApp` level and passed `scrollState = settingsScrollState` to `SettingsScreen`. `SettingsScreen` now retains its exact scroll position when returning from any sub-screen.

### 4. Global Animation Levels with User Toggle
- **Files**: [`BubbleConfig.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/model/BubbleConfig.kt), [`SettingsRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SettingsRepository.kt), [`SearchResultsList.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchResultsList.kt), [`SearchOverlay.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchOverlay.kt), [`SettingsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/SettingsScreen.kt)
- **Features**:
  - Added `AnimationLevel` enum (`OFF` (0.0x), `REDUCED` (0.5x), `STANDARD` (1.0x), `EXPRESSIVE` (1.5x)) persisted in `SettingsRepository`.
  - Scaled search item entrance animations (`AnimatedSearchItem`) and overlay fade/slide transitions by `animationLevel.scale` (instant rendering when `OFF`).
  - Added user-facing SegmentedPreference in `SettingsScreen` under Appearance.

### 5. Home Screen Status Bar Hiding Option
- **Files**: [`BubbleConfig.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/model/BubbleConfig.kt), [`SettingsRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SettingsRepository.kt), [`HomeScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/HomeScreen.kt), [`MainActivity.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/MainActivity.kt), [`SettingsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/SettingsScreen.kt)
- **Features**:
  - Added `hideStatusBarOnHomeScreen` boolean in `LauncherSettings` and `SettingsRepository`.
  - Added `DisposableEffect(screenState, settings.hideStatusBarOnHomeScreen)` in `MainActivity.kt` using `WindowCompat.getInsetsController(window, window.decorView)` to hide status bars with transient swipe behavior exclusively on `HomeScreen`.
  - Conditionally removed `statusBarsPadding()` in `HomeScreen.kt` for an edge-to-edge layout.
  - Added toggle preference under Home Screen settings in `SettingsScreen.kt`.

### 6. Search Button Long-Press to Open AI Assistant (Gemini, ChatGPT, Claude)
- **Files**: [`BubbleConfig.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/model/BubbleConfig.kt), [`SettingsRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SettingsRepository.kt), [`HomeScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/HomeScreen.kt), [`SettingsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/SettingsScreen.kt)
- **Features**:
  - Upgraded `FloatingSearchButton` to `@OptIn(ExperimentalFoundationApi::class) combinedClickable` supporting `onLongClick`.
  - Created `launchAiAssistant` supporting target AI packages: Google Gemini (`com.google.android.apps.bard`), OpenAI ChatGPT (`com.openai.chatgpt`), Anthropic Claude (`com.anthropic.claude`), Perplexity (`ai.perplexity.app.android`), Microsoft Copilot (`com.microsoft.copilot`), or any custom installed app.
  - Added `AiLaunchMode` support (`VOICE_DICTATION` for voice dictation box vs `TEXT_CHAT` for standard text chat).
  - Added interactive AI Assistant picker dialog in `SettingsScreen.kt`.

### 7. Reactive Clock, Calendar Deep-Links & Independent Toggles
- **Files**: [`HomeScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/HomeScreen.kt), [`SettingsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/SettingsScreen.kt)
- **Features**:
  - Replaced the fixed 60-second delay loop with `timeTick` and `remember(timeTick, timeFormatter)` so switching between 12-hour and 24-hour formats updates immediately.
  - Clock text tap opens system Alarms (`AlarmClock.ACTION_SHOW_ALARMS`).
  - Date text tap opens system Calendar (`Intent.CATEGORY_APP_CALENDAR`).
  - Unnested "Show date" from "Show clock" in `SettingsScreen.kt` so users can independently control date and time display.

### 8. AI Overview Search History (Last 10 Searches)
- **Files**: [`BubbleConfig.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/model/BubbleConfig.kt), [`SettingsRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SettingsRepository.kt), [`SearchRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SearchRepository.kt), [`SettingsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/SettingsScreen.kt), [`SearchAndUxFeaturesTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/SearchAndUxFeaturesTest.kt)
- **Features**:
  - Created `AiOverviewHistoryItem` data model storing query, summary, and timestamp.
  - Added persistent LRU storage in `SettingsRepository` automatically capped at 10 items with deduplication.
  - Automatically records generated summaries in `SearchRepository.kt`.
  - Added "AI Overview History" preference and dialog in `SettingsScreen.kt` showing formatted timestamps, search queries, generated summaries, and a "Clear History" button.

---

## 12. Google Play Store Compliance & Production Readiness

### 1. Sensitive Permissions & Prominent Disclosures
- **Opt-in Contacts Search**: Removed `SearchType.PERSON` from out-of-the-box defaults in [`BubbleConfig.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/model/BubbleConfig.kt) and [`SettingsRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SettingsRepository.kt). Contacts search now strictly requires explicit user opt-in accompanied by the in-app `ContactDisclosureDialog` prominent disclosure before the `READ_CONTACTS` system dialog is requested.
- **Eliminated Repetitive Permission Prompts on Launch**: Removed automatic `requestSearchPermissions()` invocation from `MainActivity.onCreate()` lifecycle block. Permissions are now requested exclusively upon intentional user action (onboarding finish or settings toggle), preventing permission spamming and policy warnings.
- **Restricted Permissions Audit**: Confirmed zero usage of restricted `READ_SMS`, `READ_CALL_LOG`, and `MANAGE_EXTERNAL_STORAGE` permissions. Scoped storage (`READ_EXTERNAL_STORAGE` capped with `maxSdkVersion="32"`, plus modern media store permissions) is cleanly implemented.
- **Launcher Package Query Exception**: `QUERY_ALL_PACKAGES` is declared with explicit home launcher categories (`CATEGORY_HOME` and `CATEGORY_LAUNCHER`), fully compliant with Google Play's Home screen replacement policy exception.

### 2. Network Security & Data Safety
- **Cleartext Traffic Disabled**: `android:usesCleartextTraffic="false"` strictly enforced in [`AndroidManifest.xml`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/AndroidManifest.xml).
- **100% HTTPS Endpoints**: All external communications (Wikipedia extracts, DuckDuckGo instant answers, Google Gemini API, and custom widgets manifest) use secure TLS/HTTPS.
- **Zero Third-Party Trackers**: No advertising SDKs, analytics frameworks, or remote profiling services.
- **Exported Component Security**: All activities and services declare `android:exported` explicitly; Quick Settings tiles are protected by `android.permission.BIND_QUICK_SETTINGS_TILE`.
- **Created Production Privacy Policy**: Generated [`privacy-policy.html`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/privacy-policy.html) in the root directory covering all Play Store User Data disclosures, local processing guarantees, and user data deletion instructions.

### 3. Release Build & R8 Minification Verification
- **`assembleRelease` & `bundleRelease`**: Successfully built with R8 code minification, resource shrinking, and `lintVitalRelease` checks passing with 0 errors. Verified generation of release artifact [`app-release.aab`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/build/outputs/bundle/release/app-release.aab).

---

## 13. UX, Physics Collider, Search Ergonomics & Assistant Enhancements

### 1. Clock Synchronization & Zero Lag
- **Issue**: Clock on the home screen displayed inaccurate time and took up to a minute to update upon waking or resuming the app.
- **Fix**: Added dynamic `BroadcastReceiver` in [`HomeScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/HomeScreen.kt) listening for `Intent.ACTION_TIME_TICK`, `Intent.ACTION_TIME_CHANGED`, and `Intent.ACTION_TIMEZONE_CHANGED`. Implemented immediate clock update on resume and registration, along with a 1-second resolution check loop, guaranteeing instant synchronization.

### 2. Direct Home-Screen Minimalistic Dots Maze Friction Challenge
- **Issue**: Maze challenge opened a separate modal dialog card with grey corridor corridors.
- **Fix**: Rebuilt [`MazeChallengeOverlay.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/MazeChallengeOverlay.kt) to render directly on the home screen with a transparent scrim and a minimalist top prompt pill. Replaced grey corridor paths with sampled minimalistic interactive dots that light up and produce progressive haptic feedback as the user traces the app icon to the finish target.

### 3. Search Results Entrance & Placement Animations
- **Issue**: Search results appeared abruptly without entrance animation.
- **Fix**: Implemented `AnimatedSearchItem` in [`SearchResultsList.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchResultsList.kt) featuring staggered spring bounce, scaling (0.95 to 1.0), and alpha fade-in keyed by item index. Enabled `Modifier.animateItem()` on all `LazyColumn` items for smooth layout transitions.

### 4. On-Demand AI Overview in Search Categories
- **Issue**: AI overview was suppressed or inaccessible via category filter chips when device matches existed.
- **Fix**: Added `SearchResultCategory.AI_OVERVIEW` chip in `SearchCategoriesBar` within [`SearchResultsList.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchResultsList.kt) whenever the search query is non-empty. Users can tap the AI Overview chip anytime to view web synthesis regardless of device match count.

### 5. Back Button Navigation to Home
- **Issue**: Pressing back on the search overlay or widget screen (page 1) did not consistently return to the home screen (page 0).
- **Fix**: Integrated `BackHandler` in [`HomeScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/HomeScreen.kt) handling open bubble dismissal, search overlay exit, and `pagerState.animateScrollToPage(0)` when on the dedicated widget page.

### 6. Dynamic App Group Bubble Gradients
- **Issue**: Group bubbles used flat monochromatic colors without reflecting individual app branding.
- **Fix**: Implemented `extractDominantColor()` in [`IconUtils.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/util/IconUtils.kt) using saturation-weighted color sampling. Integrated radial and linear gradient rendering in [`MultiAppBubbleContainer.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/MultiAppBubbleContainer.kt) blending each app's dominant icon color with the launcher's accent color.

### 7. Bubble Boundary Protection & Solid Kinematic Search Obstacle Collider
- **Issue**: Bubble labels clipped near screen edges, and the search button lacked physical presence to deflect bouncing app bubbles.
- **Fix**:
  - Adjusted boundary margins in [`FloatingAppBubble.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/FloatingAppBubble.kt) and [`MultiAppBubbleContainer.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/MultiAppBubbleContainer.kt) to prevent label clipping.
  - Implemented rigid kinematic obstacle collision in [`BubblePhysicsEngine.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/BubblePhysicsEngine.kt) and [`MultiBubblePhysicsEngine.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/MultiBubblePhysicsEngine.kt) via `updateObstacle` and elastic deflection physics.
  - Streamed real-time search button coordinates, radius, and velocity from `FloatingSearchButton` in [`HomeScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/HomeScreen.kt) into bubble physics containers.

### 8. Ergonomic Bottom-Anchored Search Layout
- **Issue**: Search field was at the top of the screen, distant from one-handed thumb reach and keyboard.
- **Fix**: Reordered [`SearchOverlay.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchOverlay.kt) Column layout: Search Results at the top (`weight(1f)`), Search Categories bar in the middle, and Search Field at the bottom with `.imePadding()` so it rests directly above the soft keyboard.

### 9. Assistant Voice & Text Overlay Intent on Search Button Long-Press
- **Issue**: Long-pressing the search button launched the full standalone AI app activity instead of summoning the assistant ("Ok Google") dictation & text prompt sheet.
- **Fix**: Updated `launchAiAssistant()` in [`HomeScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/HomeScreen.kt) under `AiLaunchMode.VOICE_DICTATION` to invoke `Intent(Intent.ACTION_ASSIST)` and `Intent(Intent.ACTION_VOICE_COMMAND)`, cleanly triggering the system assistant voice and text prompt overlay.



