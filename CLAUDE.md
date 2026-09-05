# FrameReady — Project Context

This file orients any agent (or human) picking up this repo cold. It documents *current
architecture and state*, not history — for history, use `git log`. Keep it updated: see
"Keeping this file current" at the bottom.

## What this repo is

`FrameReady` is a startup-performance library for Android that defers heavy initializer work
until after the first frame is drawn, instead of blocking `Application.onCreate()` /
`androidx.startup` the way traditional approaches do. See `README.md` for the full
public-facing pitch, API, and benchmark numbers — that file is what library consumers read.

## Module map

| Module | Purpose |
|---|---|
| `frameready/` | The core library. **Kotlin Multiplatform** (Android + iOS) as of branch `feat/kmp-ios-support`. |
| `shared-ui/` | The main showcase/demo screen's UI, **shared verbatim between Android and iOS via Compose Multiplatform** — `MainScreen.kt`, `MainViewModel.kt`, `SampleInitializers.kt` (A/B/C demo initializers), and the `ui/theme/*` package all live here in commonMain. This is the single source of truth for that screen — never fork it per-platform. |
| `app/` | Thin Android host for `:shared-ui`'s `MainScreen` — `MainActivity.kt` is ~30 lines, just `ComponentActivity` + `setContent { MainScreen(viewModel) }`. Also owns Android-only illustrative-only files not wired into the UI (`BenchmarkSamples.kt`, `DependencyInjectionIntegration.kt`, `HeavyInitializer.kt`) and `SplashActivity.kt` (the trampoline demo). |
| `sample-ios/` | Thin iOS host for `:shared-ui`'s `MainScreen` — `MainViewController.kt` wraps it in `ComposeUIViewController` and registers factories + `install` (iOS has no manifest auto-discovery). First-frame is `LaunchedEffect` inside shared `MainScreen` (`FrameReady.signalCompositionReady()`), not the iOS host. No Xcode project checked in — see "Verifying on the iOS Simulator" below. |
| `sample-standard/`, `sample-hilt/`, `sample-appstartup/`, `sample-trampoline/`, `sample-notification/` | Focused single-scenario demo apps referenced in the README's scenario table. Independent of `:shared-ui` — not touched by the M3 theme or Compose Multiplatform work. |
| `sample-baseline/`, `sample-metrics-only/`, `sample-appcls-init/` | Benchmark-only comparison apps (no FrameReady, or FrameReady with 0 initializers) — used by `benchmark/`. |
| `benchmark/` | Macrobenchmark suite (`androidx.benchmark.macro`) — see README's "Verified Macrobenchmark Results". |

## `frameready/` — KMP architecture

Converted from a plain Android library into genuine Kotlin Multiplatform, shared across Android
and iOS. Source sets:

- **`src/commonMain/kotlin/com/frameready/FrameReady.kt`** — the single shared engine, `object
  FrameReady`. Public API is **entirely `KClass`-based** (`FrameReady.await(BInitializer::class)`,
  not `::class.java`). Do not reintroduce a parallel `Class<C>`-based overload set in commonMain —
  Kotlin member functions always shadow same-named extension functions regardless of
  type-applicability, so a `Class<C>` overload next to the `KClass<C>` member causes hard
  type-inference failures at call sites. This was tried and reverted during the KMP migration.
- **`src/androidMain/`** — `FrameReadyAndroidBridge.kt` holds the one legacy-shaped entry point,
  `FrameReady.install(context: Context, initClasses: List<Class<Any>>)` (kept because its param
  type doesn't collide with the KClass members), plus everything genuinely Android-only:
  trampoline threshold/activities (default threshold 0; list splash activities), notification-origin
  heuristics, lifecycle-callback wiring. `FrameReadyProvider` records `Process.getStartUptimeMillis()`
  into `processStartMs`, stores manifest class **names**, and `Class.forName`s them at first frame.
- **`src/iosMain/`** — iOS actuals:
  - `PlatformContext`: `expect abstract class` (not `expect class`) because Android's `actual
    typealias PlatformContext = android.content.Context` requires exact modality match with
    `Context`, which is abstract. iOS's actual provides a concrete singleton via an anonymous
    subclass: `companion object { val Default: PlatformContext = object : PlatformContext() {} }`.
  - `PlatformLock` + `SafeMap`/`SafeSet`/`SafeFlag`/`SafeLongRef`: thread-safety primitives —
    `ReentrantLock` on Android, `NSLock` on iOS — backing the engine's shared mutable state.
  - `executeOnDispatcher`: Android's actual uses `runInterruptible(Dispatchers.IO) { runBlocking {
    block() } }` for real JVM thread-interruption semantics; iOS's actual uses plain
    `withContext(Dispatchers.Default)` — **best-effort cooperative cancellation only**, not true
    interruption. This is a known platform limitation, not a bug to fix.
- **`src/androidUnitTest/`** — JVM tests (Robolectric), ported to `KClass` call sites. 27/27
  passing as of the last full run.
- Old flat `src/main/java` / `src/test/java` layout no longer exists (superseded by the source
  sets above) — if you see it reappear, see "Known environment gotcha" below.

Full verification command (run all of this before considering a `frameready/` or `shared-ui/`
change done):
```bash
./gradlew :frameready:compileDebugKotlinAndroid :frameready:compileKotlinIosArm64 \
  :frameready:compileKotlinIosX64 :frameready:compileKotlinIosSimulatorArm64 \
  :frameready:compileCommonMainKotlinMetadata :frameready:testDebugUnitTest \
  :shared-ui:compileDebugKotlinAndroid :shared-ui:compileKotlinIosSimulatorArm64 \
  :shared-ui:compileCommonMainKotlinMetadata \
  :app:compileDebugKotlin :sample-ios:compileKotlinIosSimulatorArm64
```

## `shared-ui/` — one UI, both platforms

The main showcase screen is **one Compose Multiplatform UI shared verbatim** between Android and
iOS — not two hand-kept-in-sync implementations. `app/MainActivity.kt` and
`sample-ios/MainViewController.kt` are both thin hosts that construct a `MainViewModel` and render
`MainScreen(viewModel)`; all actual screen code lives in `shared-ui/src/commonMain`.
`MainScreen` calls `FrameReady.signalCompositionReady()` from `LaunchedEffect(Unit)` so Compose
Android and iOS share the same post-composition trigger. Android's `FrameReadyProvider` may also
fire via Activity resume (`trampolineThresholdMs` default 0); `markFirstFrame` is once-per-process
so the first signal wins. Showcase `await` of sample initializer A is a button, not `ViewModel` init.

- Went through a Material 3 UX pass (M3 audit: color tokens, typography, shape, elevation, layout,
  motion, accessibility, theming consistency). **Scope note:** only `shared-ui`'s screen — the
  `sample-*` modules intentionally were not touched and do not share this theme.
- `ui/theme/Color.kt` / `Theme.kt` / `Type.kt` define the app's dark "cosmic slate" brand identity
  as a real M3 `darkColorScheme` (`DemoColorScheme`, full 5-step surface-container tonal scale), a
  full 15-role `Typography` scale, and an M3 shape scale (`DemoShapes`).
  **`MyApplicationTheme` always renders `DemoColorScheme`, on every platform, regardless of system
  light/dark setting** — do not reintroduce a system-driven `darkTheme` parameter. This was a real
  bug: the iOS simulator defaults to light mode, and a generic `lightColorScheme()` has no matching
  tuned `extraColors`/`extraTypography` tokens, so text rendered with broken/invisible contrast.
  Confirmed via screenshot on both platforms after the fix — identical dark rendering on both.
  `dynamicColor` was dropped entirely (was already defaulted off) since Android's dynamic-color API
  has no iOS equivalent and this is a branded demo, not a dynamic-color candidate anyway.
- Anything with no native M3 role (warning/success/chart colors, monospace code text, large
  metric-value text) is exposed via `ExtraColors`/`ExtraTypography`, `staticCompositionLocalOf`
  extensions on `MaterialTheme` (`MaterialTheme.extraColors.*`, `MaterialTheme.extraTypography.*`).
  **Follow this pattern for new semantic roles** — don't add hardcoded hex colors or ad-hoc
  extension functions.
- `MainScreen.kt` has zero hardcoded `Color(0xFF...)` / `RoundedCornerShape(Ndp)` literals —
  everything routes through `MaterialTheme.colorScheme`/`.typography`/`.shapes` or the `extra*`
  locals above. Keep it that way for any new UI added here.
- Accessibility conventions already applied, follow them for new composables: `semantics {
  heading() }` on section titles, `semantics(mergeDescendants = true) {}` on composite
  cards/list-items, `contentDescription = null` on purely decorative icons, `heightIn(min = 48.dp)`
  on tappable controls, and `LocalReducedMotion` (from `Theme.kt`, backed by an
  `expect`/`actual @Composable isReducedMotionEnabled()` — `Settings.Global` on Android,
  `UIAccessibilityIsReduceMotionEnabled()` on iOS) gating any non-decorative
  `AnimatedVisibility`/`animate*AsState`.
- `MainViewModel` is a plain multiplatform `androidx.lifecycle.ViewModel` (not `AndroidViewModel`)
  using `kotlin.time.TimeSource.Monotonic` instead of `SystemClock.elapsedRealtime()`. The Android
  host still gets it via `by viewModels()`; the iOS host just does
  `remember { MainViewModel() }` (no `ViewModelStoreOwner` wiring — this is a single-screen demo).
- **Do not add `compose.components.resources`** to `shared-ui`'s dependencies unless you actually
  load a Compose-resources image/string. This repo's `rootProject.name` ("My Application", with a
  space) gets baked into the auto-generated resource-accessor class name, producing an invalid
  space-containing class name that fails D8 dexing (`mergeLibDexDebug` error). Hit and fixed once
  already by removing the then-unused dependency — don't re-add it casually.
- Don't reach into Android's `Context`/`Toast`/`SharedPreferences` from shared code — e.g. the
  "Reset Launch History" button used to clear `SharedPreferences` directly and show a `Toast`; it
  now calls the portable `FrameReady.resetStability()` and shows inline `Text` feedback
  (`UiState.resetMessage`) instead, matching every other status display already on the screen.

## Verifying on the iOS Simulator

There's no `.xcodeproj` in this repo — building one by hand isn't worth the boilerplate.
`sample-ios/SwiftApp/main.swift` (a `UIViewControllerRepresentable` wrapping
`MainViewControllerKt.MainViewController()`) is compiled directly with `swiftc` against the
Gradle-built `.framework` and run as a hand-packaged `.app` bundle:

```bash
# 1. Build the KMP framework (static, so no runtime dylib dependency to embed)
./gradlew :sample-ios:linkDebugFrameworkIosSimulatorArm64

# 2. Compile the Swift app against it
SDK_PATH=$(xcrun --sdk iphonesimulator --show-sdk-path)
swiftc sample-ios/SwiftApp/main.swift \
  -sdk "$SDK_PATH" -target arm64-apple-ios17.0-simulator -parse-as-library \
  -F sample-ios/build/bin/iosSimulatorArm64/debugFramework -framework SampleIos \
  -o sample-ios/SwiftApp/build/SmokeTestApp

# 3. Package as a minimal .app bundle (Info.plist alongside the binary) and codesign ad hoc
codesign --force --sign - sample-ios/SwiftApp/build/SmokeTestApp.app

# 4. Install + launch on a booted simulator
xcrun simctl install <DEVICE_ID> sample-ios/SwiftApp/build/SmokeTestApp.app
xcrun simctl launch <DEVICE_ID> com.frameready.smoketest
```

**Simulator runtime must match the SDK used to compile**, or the binary crashes at launch with
`dyld: Symbol not found: ___invert_h2` (a Swift ABI symbol missing from the older runtime's
`libSystem`). Check `xcrun simctl list runtimes` and boot a device on the newest installed
runtime that corresponds to the installed Xcode's default SDK — don't assume an arbitrary
existing/older simulator device will work.

**The Info.plist must include** `<key>CADisableMinimumFrameDurationOnPhone</key><true/>` —
Compose Multiplatform's `ComposeUIViewController` throws `IllegalStateException` at launch without
it (a high-refresh-rate-display sanity check). Add it to the hand-written Info.plist alongside the
usual `CFBundleExecutable`/`CFBundleIdentifier` keys.

## Known environment gotcha

This repo lives under `~/Documents`, which has iCloud Drive "Desktop & Documents" sync enabled on
this machine. The sync daemon has previously reverted actively-edited files (`gradle/libs.versions.toml`,
`frameready/build.gradle.kts`) back to a stale state mid-edit, and briefly resurrected deleted
directories. If files revert with no code-side explanation, suspect this before anything else.
Mitigation already applied: `xattr -w com.apple.fileprovider.ignore#P 1 FrameReady` on the repo root
excludes it from iCloud sync — check `xattr -p com.apple.fileprovider.ignore#P FrameReady` if it recurs.

## Keeping this file current

**After completing any nontrivial feature or fix, invoke the `update-context` skill** (or apply
its instructions manually: `.claude/skills/update-context/SKILL.md`) to refresh this file and, if
the change is user-facing (public API, README-documented behavior, benchmark numbers), `README.md`
too. Do this as part of finishing the task, not as a separate follow-up the user has to ask for.
