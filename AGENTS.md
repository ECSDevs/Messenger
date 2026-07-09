# Messenger AGENTS.md

## Project Overview

Messenger is a Material 3 designed LLM chat application for Android, focused on on-wrist experience and ease of use. It is fully open-source, free, and offline-capable, using a BYOK (Bring Your Own Key) model.

- **Package name**: `cc.ptoe.messenger`
- **Min SDK**: 30 (Android 11)
- **Target SDK**: 36
- **Compile SDK**: 37
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3) + Wear Compose
- **Architecture**: Clean Architecture (data/domain/presentation layers)
- **Modules**: `mobile` (phone/tablet), `wear` (Wear OS)

## Project Structure

```
Messenger/
├── .github/workflows/       # GitHub Actions CI/CD
│   └── android.yml          # Android build & release pipeline
├── gradle/
│   └── libs.versions.toml   # Version catalog for dependencies
├── mobile/                   # Mobile (phone/tablet) module
│   └── src/main/java/cc/ptoe/messenger/
│       ├── data/
│       │   ├── local/        # Room database, DataStore preferences
│       │   │   ├── dao/      # Data Access Objects
│       │   │   ├── entity/   # Room entities
│       │   │   ├── AppPreferences.kt
│       │   │   ├── DataStoreModule.kt
│       │   │   ├── MessengerDatabase.kt
│       │   │   └── ThemePreferences.kt
│       │   ├── remote/       # Network layer
│       │   │   ├── api/      # Retrofit API interfaces
│       │   │   ├── dto/      # Data Transfer Objects
│       │   │   ├── interceptor/ # OkHttp interceptors
│       │   │   ├── sse/      # Server-Sent Events parsing
│       │   │   └── NetworkClient.kt
│       │   └── repository/   # Repository implementations
│       ├── domain/
│       │   ├── model/        # Domain models (pure Kotlin data classes)
│       │   └── repository/   # Repository interfaces
│       ├── presentation/
│       │   ├── navigation/   # Navigation Compose setup
│       │   │   ├── BottomLevelRoutes.kt
│       │   │   ├── NavGraph.kt
│       │   │   └── Screen.kt
│       │   ├── theme/        # Material 3 theme
│       │   ├── ui/           # Compose screens & components
│       │   │   ├── agents/
│       │   │   ├── chat/
│       │   │   ├── components/
│       │   │   ├── conversations/
│       │   │   ├── providers/
│       │   │   └── settings/
│       │   ├── utils/        # Presentation utilities
│       │   └── viewmodel/    # ViewModels
│       ├── MainActivity.kt
│       └── MessengerApplication.kt
├── wear/                     # Wear OS companion module
│   └── src/main/java/cc/ptoe/messenger/
│       ├── data/             # Wear DataStore + phone bridge
│       ├── presentation/     # Wear Compose activity
│       ├── presentation/viewmodel/
│       └── WearMessengerApplication.kt
├── build.gradle.kts          # Root build file
├── settings.gradle.kts       # Project settings
├── gradle.properties         # Gradle configuration
└── licenserc.toml            # License checker config
```

## Architecture

### Layered Architecture

The project follows Clean Architecture with three main layers:

1. **Domain Layer** (`domain/`)
   - Pure Kotlin, no Android dependencies
   - Contains business models and repository interfaces
   - Domain models: `Agent`, `ChatModel`, `Conversation`, `Message`, `MessageRole`, `Provider`

2. **Data Layer** (`data/`)
   - Implements domain repository interfaces
   - Local data source: Room database + DataStore preferences
   - Remote data source: Retrofit + OkHttp + SSE
   - Entity mapping: Room entities (`*Entity.kt`) map to domain models

3. **Presentation Layer** (`presentation/`)
   - Jetpack Compose UI with Material 3
   - MVVM pattern with ViewModels
   - Navigation Compose for routing

### Wear Companion Architecture

- The `wear` module is a phone-backed companion experience with no settings UI
- Wear syncs lightweight agent metadata from `mobile` through the Google Play Services wearable message layer
- Wear sends chat requests back to `mobile`, and the phone resolves models/providers plus performs the API call using the existing repositories
- Wear persists synced agents, selected agent, and per-agent message history locally with DataStore for a fast resume path

### Dependency Injection

The project uses a manual dependency injection approach via `MessengerApplication`:
- All repositories and data sources are initialized in `MessengerApplication.onCreate()`
- Access via `MessengerApplication.instance` singleton pattern
- The `wear` module mirrors this approach with `WearMessengerApplication`

### Database

- **Room Database**: `MessengerDatabase` with 5 entities
  - `ProviderEntity` - API providers
  - `ModelEntity` - Available models per provider
  - `AgentEntity` - AI agent configurations
  - `ConversationEntity` - Chat conversations
  - `MessageEntity` - Chat messages
- Database version: 5 (with `fallbackToDestructiveMigration`)

### Navigation

- Uses Navigation Compose
- Routes defined in `Screen.kt` sealed class
- NavGraph defined in `NavGraph.kt`
- Bottom navigation routes defined in `BottomLevelRoutes.kt`
- **Important**: `ProviderEdit` and `AgentEdit` routes use optional parameter syntax (`provider_edit?providerId={providerId}`)

### API / Network

- OpenAI-compatible API via Retrofit
- SSE (Server-Sent Events) streaming for chat completions
- Auth header interceptor for API keys
- Gson converter for JSON serialization
- Wear chat requests are forwarded to the phone over the wearable message layer instead of calling providers directly from the watch

## Hard Constraints

These constraints MUST be followed at all times:

1. **Navigation route syntax**: `ProviderEdit` and `AgentEdit` routes in `Screen.kt` MUST use Navigation Compose optional parameter syntax: `provider_edit?providerId={providerId}` and `agent_edit?agentId={agentId}`. Do NOT use required parameter syntax `{parameter}`.

2. **Default Agent invariant**: The database MUST always contain exactly one Agent with `isDefault=true`. This default Agent CANNOT be deleted.

3. **Model-required chat flow**: When an Agent's `defaultModelId` is null (model not set), all chat operations (`sendMessage`, `retrySend`, `regenerateMessage`) MUST:
   - Prompt the user to set a model first
   - Abort the send flow (do NOT insert user message, do NOT modify message status, do NOT delete any message)

4. **Error visibility**: API errors MUST NOT be silently retried. Errors MUST be displayed via both:
   - Snackbar notification
   - AI message bubble showing error details

5. **IME behavior**: Chat screen requires `android:windowSoftInputMode="adjustResize"` in AndroidManifest to avoid IME input box position issues.

6. **Edge-to-edge**: The app implements edge-to-edge design:
   - `Theme.kt` calls `WindowCompat.setDecorFitsSystemWindows(window, false)`
   - `themes.xml` configures transparent status bar
   - `MainScaffold.kt` only applies bottom padding (for bottom nav), letting each screen handle top/left/right insets via its own Scaffold+TopAppBar

7. **Built-in Kotlin migration**: AGP 9 built-in Kotlin is enabled. Do not apply `org.jetbrains.kotlin.android` or `kotlin("android")` in Android modules. Prefer `com.google.devtools.ksp` for supported processors like Room; use `com.android.legacy-kapt` only if annotation processors cannot yet move to KSP.

8. **Wear companion scope**: The Wear app MUST stay focused on chat only. Agents are synced from mobile, and provider/model/settings management stays on mobile.

## Engineering Conventions

1. **API error handling**: All API calls must handle `HttpException` and extract error messages using `extractHttpErrorMessage()` from the response body's `error.message` field.

2. **SSE stream handling**: SSE stream processing must include a `hasFinished` flag. If the stream ends without receiving a Done or Error event, an Error event must be emitted proactively.

3. **Screen structure**: Each screen uses Material 3 `Scaffold` with `TopAppBar` to properly handle status bar insets. Do NOT add top-level padding in screens — the Scaffold handles it.

4. **Nested Scaffold caution**: Avoid nested Scaffold double-inset issues. The outer `MainScaffold` only applies bottom padding for the bottom navigation bar.

5. **Versioning**: Version code is derived from git commit count (or `VERSION_CODE` env var). Version name is `v{versionCode}`. Release tags follow `v*` pattern.

6. **Dependency management**: Use version catalog (`gradle/libs.versions.toml`) for all dependency versions.

## Editing Guidelines

### Adding a New Screen

1. Add route to `Screen.kt` sealed class with `createRoute()` helper
2. Add composable destination in `NavGraph.kt`
3. Create screen composable in `presentation/ui/<feature>/`
4. Create ViewModel in `presentation/viewmodel/` if needed
5. If it's a bottom-level route, add to `BottomLevelRoutes.kt`

### Adding a New Database Entity

1. Create entity in `data/local/entity/`
2. Create DAO in `data/local/dao/`
3. Add entity to `MessengerDatabase` entities array and add DAO abstract function
4. Create domain model in `domain/model/`
5. Create repository interface in `domain/repository/`
6. Create repository implementation in `data/repository/`
7. Initialize repository in `MessengerApplication.initRepositories()`

### Adding a New API Endpoint

1. Add DTOs in `data/remote/dto/`
2. Add method to `OpenAiApi` interface
3. Add repository method in domain repository interface
4. Implement in `ApiRepositoryImpl`
5. Handle `HttpException` and extract error messages properly

### Updating AGENTS.md

**AGENTS.md MUST be updated whenever structural changes are made to the project**, including but not limited to:

- Adding new modules or directories
- Adding new architectural layers or patterns
- Changing navigation structure significantly
- Adding new database entities or repositories
- Adding new features that affect the project structure
- Changing hard constraints or engineering conventions
- Updating build configuration or CI/CD pipelines

If a change makes any section of AGENTS.md outdated or incomplete, update it in the same commit as the structural change.

### Code Style

- Use Kotlin idiomatic patterns
- Follow Material 3 design guidelines
- Use `collectAsState()` for observing Flow in Compose
- Use `rememberCoroutineScope()` for launching coroutines from composables
- Prefer `Modifier` parameter with default value for composables
- Use descriptive naming for composables and functions

## Build & Run

### Prerequisites

- JDK 17
- Android Studio (or Android SDK)
- Gradle wrapper is included

### Build Commands

```bash
# Build debug APK
./gradlew :mobile:assembleDebug
./gradlew :wear:assembleDebug

# Build release APK
./gradlew :mobile:assembleRelease
./gradlew :wear:assembleRelease

# Run unit tests
./gradlew :mobile:testDebugUnitTest

# Run lint
./gradlew :mobile:lintDebug
```

### Local Development

1. Create a `local.properties` file with `sdk.dir=/path/to/android/sdk`
2. For release builds, set up keystore in `keyring/messenger-release.jks`
3. Environment variables for signing: `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
4. Version code can be overridden with `VERSION_CODE` env var

## CI/CD

GitHub Actions workflow (`.github/workflows/android.yml`):

- **Trigger**: Push to main, PRs to main, tags matching `v*`
- **Build job**: Compiles mobile and wear release APKs
- **Release job**: Creates GitHub release with APK artifacts on tag push
- **Caching**: Gradle User Home cache + Kotlin/Native compiler cache
- **Signing**: Uses GitHub secrets for keystore and passwords

## Git Workflow

- Main branch: `main`
- Release tags: `v*` (e.g., `v123`)
- Version code = number of commits (auto-calculated in CI)
- Each completed task should be committed and pushed

### Commit & Push After Each Task

When a task is completed:

```bash
git add <changed-files>
git commit -m "<descriptive commit message>"
git push
```

Write clear, concise commit messages describing what was changed and why.

## Key Files Reference

- [build.gradle.kts](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/build.gradle.kts) - Root build file with version calculation
- [settings.gradle.kts](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/settings.gradle.kts) - Module includes
- [libs.versions.toml](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/gradle/libs.versions.toml) - Dependency version catalog
- [MessengerApplication.kt](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/mobile/src/main/java/cc/ptoe/messenger/MessengerApplication.kt) - App entry point & DI
- [MainActivity.kt](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/mobile/src/main/java/cc/ptoe/messenger/MainActivity.kt) - Main activity
- [Screen.kt](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/mobile/src/main/java/cc/ptoe/messenger/presentation/navigation/Screen.kt) - Navigation routes
- [NavGraph.kt](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/mobile/src/main/java/cc/ptoe/messenger/presentation/navigation/NavGraph.kt) - Navigation graph
- [MessengerDatabase.kt](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/mobile/src/main/java/cc/ptoe/messenger/data/local/MessengerDatabase.kt) - Room database
- [MainScaffold.kt](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/mobile/src/main/java/cc/ptoe/messenger/presentation/ui/components/MainScaffold.kt) - Main app scaffold
- [android.yml](file:///c:/Users/deskt/AndroidStudioProjects/Messenger/.github/workflows/android.yml) - CI/CD workflow
