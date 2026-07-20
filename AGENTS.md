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
- **Modules**: `mobile` (phone/tablet), `wear` (Wear OS), `server` (Next.js/Vercel account and incremental cloud sync service)

## Project Structure

```
Messenger/
├── .github/workflows/       # GitHub Actions CI/CD
│   └── android.yml          # Android build & release pipeline
├── gradle/
│   └── libs.versions.toml   # Version catalog for dependencies
├── llm-typewriter/           # Git submodule: source build of the llm-typewriter library (tracks upstream main)
├── mobile/                   # Mobile (phone/tablet) module
│   └── src/main/java/cc/ptoe/messenger/
│       ├── data/
│       │   ├── local/        # Room database, DataStore preferences
│       │   ├── cloud/        # Messenger account authentication and cloud synchronization
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
├── server/                   # Git submodule: Next.js account server + admin backend for incremental cloud sync
│   ├── app/                  # App Router pages and serverless API routes
│   │   ├── admin/            # Password-protected admin backend
│   │   └── api/              # Auth, entity sync, and avatar endpoints for Messenger clients
│   ├── components/           # Admin client components
│   ├── lib/                  # Auth, storage, validation, shared types
│   ├── package.json          # Node/Next.js manifest
│   └── README.md             # Server deployment and API notes
├── wear/                     # Wear OS companion module
│   └── src/main/java/cc/ptoe/messenger/
│       ├── data/             # Wear DataStore + phone bridge
│       ├── presentation/
│       │   ├── MainActivity.kt
│       │   ├── theme/
│       │   ├── ui/
│       │   │   ├── chat/         # Chat screen
│       │   │   ├── chatlist/     # Chat list screen
│       │   │   └── components/   # Shared bubbles/input
│       │   └── viewmodel/
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

- The `wear` module is a phone-backed companion experience with no settings UI (tiny mobile chat-only surface)
- Wear UI is a two-screen chat flow: **chat list** (mobile conversations with agent avatars + last-message previews) and **chat screen** (messages + input with agent/user avatars)
- Navigation is state-based (`WearScreen.ChatList` / `WearScreen.Chat`) without Navigation Compose
- **WebSocket sync over the watch's tether network** (phone → watch): the phone runs a foreground service `MobileHttpServer` that listens on TCP `18765` and registers an NSD (`_messenger._tcp`) mDNS service; the watch discovers it via `WearNetworkBridge` and opens a WebSocket using OkHttp. Wear OS watches tether their network to the phone via Bluetooth PAN, so the watch and phone are always on the same L2 network — no Bluetooth pairing or runtime permissions are required. The previous DataLayer and Bluetooth RFCOMM paths were abandoned because GMS for Wear OS is missing on Samsung China-region Galaxy Watches *and* the Bluetooth path was unreliable. The same line-delimited JSON protocol (`sync` / `chat` / `new_conversation`, all with `requestId` correlation) is spoken on top of WebSocket text frames
- Chat actions use the same WebSocket: the watch sends a `chat` or `new_conversation` JSON request and the phone replies inline. The same `MobileWearChatHandler` that used to back the DataLayer path is reused here, so business logic is identical
- Wear caches the latest synced snapshot in DataStore for a fast resume path

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
  - `MessageEntity` - Chat messages (`partsJson` column stores multimodal `ContentPart` payloads)
- Database version: 7 (with `fallbackToDestructiveMigration`)

### Navigation

- Uses Navigation Compose
- Routes defined in `Screen.kt` sealed class
- NavGraph defined in `NavGraph.kt`
- Bottom navigation routes defined in `BottomLevelRoutes.kt`
- **Important**: `ProviderEdit` and `AgentEdit` routes use optional parameter syntax (`provider_edit?providerId={providerId}`)
- `AgentMarket` and `AgentMarketDetail` are Cloud-authenticated routes entered from the Agent list FAB; the FAB exposes the market only while a Cloud user is signed in.

### API / Network

- OpenAI-compatible API via Retrofit
- SSE (Server-Sent Events) streaming for chat completions
- Auth header interceptor for API keys
- Gson converter for JSON serialization
- Wear chat requests are forwarded to the phone over a WebSocket on TCP `18765` (`MobileHttpServer` / `WearNetworkBridge`, discovered via NSD mDNS) instead of calling providers directly from the watch
- **Multimodal messages**: `ChatMessageDto.content` is a `JsonElement` so a single DTO carries both the legacy text-string shape and the OpenAI `[{type,text|image_url}]` array shape. `ApiRepositoryImpl` picks the wire format based on `Message.hasImages`. Picked images are downscaled to 1568px on the longest side, EXIF-rotated, cached as PNGs under `filesDir/chat_images/`, and the cache is reaped on message delete. The local DB keeps the bitmap path/URI stable so the cloud sync layer and the UI never depend on a content:// URI re-issuing.

### Cloud Account Server

- `server/` is a standalone Next.js App Router project in a git submodule, intended for Vercel deployment
- Messenger clients authenticate with email/password against serverless route handlers under `server/app/api/`
- MongoDB stores user documents plus versioned `agents`, `conversations`, and `providers` documents; conversations embed messages and providers embed models
- Public Agent Market entries live separately in `market_agents`; they contain only portable Agent snapshots (name, avatar, prompt, and sampling parameters), never providers, model bindings, or API keys.
- Each user has a monotonically increasing `syncVersion`. Entity writes atomically increment it and stamp the changed document's `version`; deletes are `deleted: true` tombstones returned by `GET /api/sync?since=N`
- Server registration seeds the one required default Agent in the same transaction as user creation, so the cloud data preserves the default-agent invariant
- Vercel Blob private storage is used only for user and agent avatars at
  `avatars/users/{userId}.{ext}` and `avatars/agents/{agentId}.{ext}`. Replacements snapshot the
  prior blob, remove prefix-matched files, and restore the prior avatar if the new upload fails.
  Authenticated avatar GET routes stream private blobs to mobile clients
- Authenticated entity APIs are `PUT`/`DELETE` `/api/agents/{id}`, `/api/conversations/{id}`, and
  `/api/providers/{id}`. Avatar APIs use `GET`/`PUT`/`DELETE` `/api/avatars/user` and
  `/api/avatars/agents/{agentId}`; GET requests authenticate the user and proxy private Blob content
- Account APIs include `PUT /api/auth/password` for authenticated password changes and `DELETE /api/auth/account` for permanent account deletion
- The admin backend lives under `server/app/admin/` and uses a separate password-based session cookie from app users
- MongoDB must be deployed as Atlas or a replica set because server writes use transactions to atomically advance the sync clock and update an entity
- The mobile `CloudSyncRepository` uses the session cookie and a per-account DataStore cursor to pull `GET /api/sync?since=N`; it applies tombstones transactionally, flattens provider models and conversation messages into Room, and pushes complete entity snapshots to the corresponding `PUT` endpoints
- Mobile local repository mutations are debounced into cloud synchronization requests; deleted entities are retained as account-scoped pending-delete markers until the server tombstone write succeeds
- User and agent avatars are uploaded as multipart `file` parts to the dedicated avatar endpoints;
  authenticated GET endpoints proxy private Blob content. Mobile avatar URLs are downloaded during
  Cloud Sync into `filesDir/cloud_avatars`; Room/DataStore store local avatar paths so `AgentAvatar`
  never depends on a network request
- Market list/detail, publish, update, and unpublish APIs require a Messenger session. Market avatars remain private Blobs and are streamed through authenticated `/api/market/agents/{id}/avatar` routes.
- Imported Agents retain their market entry/version link in Room and private cloud sync metadata, but always clear the model binding and follow-default flags. Market updates require explicit user confirmation and preserve the local model binding.

### Chat bubble rendering (mobile)

- AI message bubbles use [`llm-typewriter`](https://github.com/ECSDevs/llm-typewriter) for both the streaming reveal and the static rendering of completed messages.
- Messenger builds `llm-typewriter` directly from the checked-out `llm-typewriter/` git submodule via `includeBuild("llm-typewriter")` in `settings.gradle.kts`, so Gradle substitutes the source build instead of downloading `cc.ptoe:llm-typewriter` from Maven Central.
- The library ships progressive Markdown (bold, code fences with syntax highlighting, links) and inline / display LaTeX math, so the AI bubble is rendered via `StreamingTypewriter` + `rememberMarkdownTypewriterRenderer`.
- `ChatViewModel` owns a single `StreamingTypewriterState` and a `streamingMessageId: StateFlow<String?>`. Each SSE `Content` event calls `typewriterState.appendToken(...)`; on `Done` the view model calls `completeSource()`; on `Error` / stop it calls `stop()` (and `skipToEnd()` on stop so the partial content stays visible). The `streamingMessageId` is cleared at the same point so the bubble switches to the static (`baseDelayMs = 0` + `skipToEnd()`) path.
- The dependency pulls in [AndroidMath](https://github.com/gregcockroft/AndroidMath) transitively for LaTeX typography, which is hosted on JitPack. `settings.gradle.kts` already declares the `https://jitpack.io` repo.

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

5. **Versioning**: Version code is derived from git commit count (or `VERSION_CODE` env var). Version name is `v{yyyyMMdd}` from the latest commit's date for reproducibility (or `VERSION_NAME` env var). Release tags follow `v*` pattern.

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
2. Initialize submodules after cloning: `git submodule update --init --recursive`
3. For release builds, set up keystore in `keyring/messenger-release.jks`
4. Environment variables for signing: `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
5. Version code can be overridden with `VERSION_CODE` env var; version name with `VERSION_NAME` env var
6. For the account server, create `server/.env.local` from `server/.env.example` and provide `JWT_SECRET`, `ADMIN_PASSWORD`, `MONGODB_URI` (Atlas or replica set), and `BLOB_READ_WRITE_TOKEN`

### Server Commands

```bash
# Install server dependencies
cd server && pnpm install

# Run the account server locally
cd server && pnpm dev

# Type-check the server
cd server && pnpm typecheck

# Lint the server
cd server && pnpm lint
```

### R8 Troubleshooting

R8 is a possible investigation point for release-only failures involving
reflection, serialization, generated code, or manifest components. It is not a
routine verification step.

For daily tasks, maintain the affected module's `proguard-rules.pro` whenever a
R8-sensitive class, method, annotation, serialized model, generated callback,
or manifest component is added, removed, or changed. Remove stale rules when
the corresponding entry point is renamed or deleted, and keep rules as narrow
as practical.

Do not run R8 checks or R8-enabled `assembleRelease` for ordinary tasks. Those
builds are slow and have a high performance cost. Follow the steps below only
when an R8 issue is being investigated, the user explicitly requests
verification, or a release/R8 configuration change requires validation:

1. Reproduce the shrinker output from a clean task run:

   ```bash
   ./gradlew :mobile:assembleRelease :wear:assembleRelease --rerun-tasks
   ```

2. Check `mobile/build/outputs/mapping/release/` and
   `wear/build/outputs/mapping/release/`:
   - `mapping.txt` confirms that a class survived and shows its obfuscated name.
   - `usage.txt` lists removed classes and members; distinguish a fully removed class from an optimized-away member listed beneath a surviving class.
   - `seeds.txt` confirms that a keep rule matched, but is not by itself proof that the final APK contains the class.
   - `configuration.txt` confirms the effective rules, including consumer rules from dependencies.

3. Compare the reports with the final APK. Inspect the release APK DEX files
   with Android Studio APK Analyzer or `apkanalyzer`, and verify the relevant
   class plus generated or anonymous callback classes are present. For this
   project, pay special attention to Manifest components, Room's generated
   `MessengerDatabase_Impl` and DAO implementations, Retrofit API interfaces
   and Gson DTOs, the mobile `MobileHttpServer` WebSocket/NSD callbacks, and
   the Wear `WearNetworkBridge` WebSocket/NSD callbacks.

4. Identify the reflective or serialized entry point before adding a rule.
   Preserve only the smallest required scope. Keep runtime annotation and
   generic metadata when the library reads them:

   ```proguard
   -keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
   -keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
   -keepattributes Signature,InnerClasses,EnclosingMethod
   ```

5. Add the rule to the affected module's `proguard-rules.pro`, rebuild, and
   confirm that it appears in `configuration.txt`, the expected class is not
   fully removed according to `usage.txt`/`mapping.txt`, and it is present in
   the APK. Avoid blanket `-keep class ** { *; }` rules because they hide
   missing entry points and defeat shrinking.

6. If the root is unclear, temporarily add
   `-whyareyoukeeping class <fully.qualified.ClassName>` to the affected rules
   file, rebuild, and use the R8 reason chain to find the missing or unexpected
   entry point. Remove this diagnostic rule after the investigation.

7. Install the minified APK on a device or emulator and smoke-test the affected
   path. For crash reports, use the matching `mapping.txt` with Android's
   `retrace` tool before diagnosing the stack trace. A successful R8 build is
   not sufficient proof that runtime reflection or serialization works.

## CI/CD

GitHub Actions workflow (`.github/workflows/android.yml`):

- **Trigger**: Push to main, PRs to main, tags matching `v*`
- **Build job**: Compiles mobile and wear release APKs; computes `VERSION_CODE` from `git rev-list --count HEAD` (full checkout via `fetch-depth: 0`) and initializes git submodules recursively so the `llm-typewriter/` source build is present on CI
- **Release job**: Creates GitHub release with APK artifacts on tag push
- **Caching**: Gradle User Home cache + Kotlin/Native compiler cache
- **Signing**: Uses GitHub secrets for keystore and passwords

## Git Workflow

- Main branch: `main`
- Release tags: `v*` (e.g., `v123`, named after version code = commit count)
- Version code = number of commits (auto-calculated in CI)
- Version name = `v{yyyyMMdd}` from latest commit's date for reproducibility (e.g., `v20260711`)
- Each completed task should be committed locally; do not push unless explicitly requested

### Commit After Each Task

When a task is completed:

```bash
git add <changed-files>
git commit -m "<descriptive commit message>"
```

Write clear, concise commit messages describing what was changed and why. Push only when explicitly requested.

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
