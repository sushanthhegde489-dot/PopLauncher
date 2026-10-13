# PopLauncher - File Map & Architecture Overview

## Project Root Files
- [`.gitignore`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/.gitignore): Git ignore rules for Android build outputs, caches, IDE files, and temporary artifacts.
- [`build.gradle.kts`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/build.gradle.kts): Root project Gradle configuration.
- [`settings.gradle.kts`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/settings.gradle.kts): Gradle settings defining repository dependencies and module includes (`:app`).
- [`gradle.properties`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/gradle.properties): JVM flags and Android build configuration.
- [`gradle/libs.versions.toml`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/gradle/libs.versions.toml): Version catalog for Compose, Kotlin, AndroidX, and build plugins.
- [`CUSTOM_WIDGETS.md`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/CUSTOM_WIDGETS.md): Documentation on custom widgets and JSON schema specifications.
- [`PLAY_STORE_RELEASE.md`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/PLAY_STORE_RELEASE.md): Checklist and guidelines for Play Store compliance and production release.

---

## App Module (`app/`)
- [`app/build.gradle.kts`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/build.gradle.kts): Application Gradle build scripts, dependencies, composeCompiler configurations, and SDK versions.
- [`app/proguard-rules.pro`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/proguard-rules.pro): R8/ProGuard obfuscation rules.
- [`app/src/main/AndroidManifest.xml`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/AndroidManifest.xml): App manifest defining launcher intent filters, permissions, tile services, and activities.

---

## Kotlin Source Code (`app/src/main/java/com/sushanth/poplauncher/`)

### Main Activity
- [`MainActivity.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/MainActivity.kt): Core entry point, window insets, system gestures, and root Composable navigation setup.

### Bubble Physics & Floating UI (`bubble/`)
- [`BubblePhysicsEngine.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/BubblePhysicsEngine.kt): Single bubble motion, collision boundaries, and restitution.
- [`MultiBubblePhysicsEngine.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/MultiBubblePhysicsEngine.kt): Physics simulation handling inter-bubble collision and velocity damping.
- [`FloatingAppBubble.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/FloatingAppBubble.kt): Composable bubble rendering, touch handling, and drag gestures.
- [`MultiAppBubbleContainer.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/MultiAppBubbleContainer.kt): Surface hosting multiple floating app bubbles with continuous frame loop.
- [`GravityIconPhysicsEngine.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/GravityIconPhysicsEngine.kt): Accelerometer / tilt-driven gravity simulation for icons.
- [`PopParticles.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/PopParticles.kt): Particle effects triggered on bubble bursting and interactions.
- [`MazeChallengeOverlay.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/bubble/MazeChallengeOverlay.kt): Intentional friction maze challenge before opening distraction apps.

### Home & Navigation (`home/`)
- [`HomeScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/HomeScreen.kt): Main launcher workspace, app grid/list, swipe triggers, and gestures.
- [`HomeContextMenu.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/HomeContextMenu.kt): Long-press popups for apps (hide, pin, group, info, uninstall).
- [`OnboardingSearchSetupOverlay.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/home/OnboardingSearchSetupOverlay.kt): First-run onboarding for permissions and search engine setup.

### Search (`search/`)
- [`SearchOverlay.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchOverlay.kt): Fullscreen universal search overlay with fluid animations.
- [`SearchField.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchField.kt): Minimalist search input field with query debouncing.
- [`SearchResultsList.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchResultsList.kt): Sectioned list showing categorized query results.
- [`SearchResultItem.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/search/SearchResultItem.kt): Individual search item view with actionable deep links.

### Data Layer (`data/`)
- **Indexing & Providers (`data/indexing/`):**
  - [`SearchProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/SearchProvider.kt): Common interface for asynchronous entity search.
  - [`ContactSearchProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/ContactSearchProvider.kt): Contact indexing via Android ContactsProvider.
  - [`ConversationProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/ConversationProvider.kt) & [`ConversationSearchProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/ConversationSearchProvider.kt): Conversation and chat shortcut lookup.
  - [`DeepAppSearchProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/DeepAppSearchProvider.kt): App shortcut and deep link search.
  - [`FileSearchProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/FileSearchProvider.kt): MediaStore and local file search.
  - [`MediaSearchProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/MediaSearchProvider.kt): Image, video, and audio file search.
  - [`SettingsShortcutSearchProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/SettingsShortcutSearchProvider.kt): System settings shortcut indexing.
  - [`WebOverviewProvider.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/indexing/WebOverviewProvider.kt): Fallback web search results and suggestions.
- **Models (`data/model/`):**
  - [`AppGroup.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/model/AppGroup.kt): Category / folder groupings for apps.
  - [`BubbleConfig.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/model/BubbleConfig.kt): Physics parameters, size, and styling for bubbles.
  - [`GestureConfig.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/model/GestureConfig.kt): Mapping gestures (double-tap, swipes) to launcher actions.
  - [`SearchEntity.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/model/SearchEntity.kt): Unified domain model for search results.
- **Repositories (`data/repository/`):**
  - [`AppRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/AppRepository.kt): Installed apps enumeration, package updates, private space handling.
  - [`SearchRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SearchRepository.kt): Aggregates results across active search providers.
  - [`SearchIndexRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SearchIndexRepository.kt): Cache and index coordinator.
  - [`SettingsRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/data/repository/SettingsRepository.kt): User preferences via DataStore/SharedPreferences.

### Settings UI (`settings/`)
- [`SettingsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/SettingsScreen.kt): Launcher configuration hub.
- [`AppGroupsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/AppGroupsScreen.kt): App group creation and categorization management.
- [`ConversationAppsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/ConversationAppsScreen.kt): Configuration for conversation shortcuts.
- [`FrictionAppsScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/FrictionAppsScreen.kt): Configuration for intentional delay & friction challenges.
- [`HiddenAppsSetupScreen.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/HiddenAppsSetupScreen.kt): Hidden apps setup and pin management.
- [`HsvColorPicker.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/settings/HsvColorPicker.kt): Color picker dialog for custom UI accenting.

### Widgets (`widgets/`)
- [`WidgetManager.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/widgets/WidgetManager.kt): Manages native widget host lifecycle and state.
- [`LauncherAppWidgetHost.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/widgets/LauncherAppWidgetHost.kt): Android AppWidgetHost subclass.
- [`VisualWidgetPicker.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/widgets/VisualWidgetPicker.kt): Visual picker dialog to add widgets to launcher.
- [`WidgetPageView.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/widgets/WidgetPageView.kt): Scrollable widget surface/page.
- **Custom Widget Engine (`widgets/custom/`):**
  - [`CustomWidgetModels.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/widgets/custom/CustomWidgetModels.kt): Data structures for custom JSON widgets.
  - [`CustomWidgetCatalog.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/widgets/custom/CustomWidgetCatalog.kt): Built-in custom widget templates.
  - [`CustomWidgetRepository.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/widgets/custom/CustomWidgetRepository.kt): Asset loading and parsing.
  - [`CustomWidgetRenderer.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/widgets/custom/CustomWidgetRenderer.kt): Composable interpreter for declarative custom widgets.

### Utilities & Theming
- [`util/IconUtils.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/util/IconUtils.kt): Icon extraction, monochromatic tinting, and caching.
- [`util/PrivateSpaceHelper.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/util/PrivateSpaceHelper.kt): Android 15+ Private Space detection and management.
- [`tiles/MonochromeTileService.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/tiles/MonochromeTileService.kt): Quick Settings tile for monochrome toggle.
- [`tiles/WallpaperTileService.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/tiles/WallpaperTileService.kt): Quick Settings tile for wallpaper actions.
- [`ui/theme/`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/main/java/com/sushanth/poplauncher/ui/theme/): Dynamic themes, color palettes, typography, and dark/light modes.

### Unit Tests (`app/src/test/java/com/sushanth/poplauncher/`)
- [`BubblePhysicsEngineTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/BubblePhysicsEngineTest.kt): Bubble collision and movement tests.
- [`MultiBubblePhysicsEngineTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/MultiBubblePhysicsEngineTest.kt): Multi-body collision tests.
- [`GravityIconPhysicsEngineTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/GravityIconPhysicsEngineTest.kt): Tilt physics tests.
- [`CustomWidgetManifestTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/CustomWidgetManifestTest.kt): Manifest schema verification.
- [`FileAndMediaSearchProviderTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/FileAndMediaSearchProviderTest.kt): File/media search logic tests.
- [`PersonAndWebSearchTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/PersonAndWebSearchTest.kt): Contact and web fallback tests.
- [`SearchFrictionLogicTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/SearchFrictionLogicTest.kt): Friction and intentional delay tests.
- [`SmartSearchAndFrictionTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/SmartSearchAndFrictionTest.kt): End-to-end search tests.
- [`NewFeaturesUnitTest.kt`](file:///home/sushanth/AndroidStudioProjects/PopLauncher/app/src/test/java/com/sushanth/poplauncher/NewFeaturesUnitTest.kt): General regression unit tests.
