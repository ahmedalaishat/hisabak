# Kotlin Multiplatform migration plan

Hisabak is migrating from a single-module Android app to **Kotlin Multiplatform + Compose
Multiplatform**, in two phases:

- **Phase A (complete — PRs #90–#99):** a running, fully-tested **Android** app restructured into
  KMP form — all common code (domain, data, ViewModels, **and Compose UI**) in `shared/commonMain`,
  Android platform code in `shared/androidMain` + a thin `androidApp`, and **stub iOS actuals** in
  `iosMain` so the iOS targets compile. No iOS app yet.
- **Phase B (in progress):** real iOS support — Xcode `iosApp`, real actuals, multiplatform
  Navigation 3. Plan at the end of this doc.

Structural reference: the JetBrains KMP wizard layout (`shared` + `androidApp` + Xcode-only
`iosApp`; the `com.android.kotlin.multiplatform.library` AGP plugin; static `Shared` framework via
`embedAndSignAppleFrameworkForXcode`; `iosArm64` + `iosSimulatorArm64` only).

The pure-Android line is frozen on the **`android` branch** (v1.9.0, commit `ee44969`) as a
reference; hotfixes for it cherry-pick. `develop`/`main` carry the KMP app from Phase A onward.

## Target layout

```
Hisabak/
├── settings.gradle.kts            # :androidApp, :shared
├── shared/                        # KMP lib: android + iosArm64 + iosSimulatorArm64
│   ├── build.gradle.kts           # CMP, Room KMP plugin, KSP per-target
│   ├── schemas/                   # moved verbatim from app/schemas/ (flat layout, no per-target dirs)
│   └── src/{commonMain,androidMain,iosMain,commonTest,androidHostTest}/
│       └── commonMain/composeResources/{values,values-ar,font}/
├── androidApp/                    # from app/ — flavors, signing, Firebase, manifests, nav, MainActivity
└── iosApp/                        # Phase B only
```

Packages keep `com.hisabak.*` names so moves don't ripple through imports.

### Where things land (pattern-level)

| Current | Destination |
|---|---|
| `core/common/*`, all `feature/*/domain/*`, `core/domain/*`, SMS parsing engine, `JsonBackupCodec`, `CategoryLimitMonitor`, `TransactionRecordedNotifier`, `SeedData`/`StarterData`, `compactAmount` | `shared/commonMain` (after kotlinx-datetime swap) |
| Room entities/DAOs/mappers/`HisabakDatabase`/`Room*Repository` | `shared/commonMain` (Room KMP, `@ConstructedBy`; builder via `expect fun`) |
| `AppPreferencesDataStore`, `DataStoreBackupAccountStore` | `shared/commonMain` (DataStore KMP; `expect` path provider) |
| `ui/theme/*` (minus Motion), `ui/components/*`, `ui/icons/`, `core/presentation/*`, all `feature/*/presentation/*` screens + ViewModels, `ManageRoute/ViewModel` | `shared/commonMain` (CMP; Vico → `multiplatform-m3`; JB lifecycle + koin-compose-viewmodel) |
| `res/values{,-ar}/strings.xml`, fonts | `shared/commonMain/composeResources/` (bundled OFL TTFs replace downloadable fonts) |
| `AesGcmBackupCrypto`, `KeystoreBackupPassphraseStore`, `KeystoreDatabaseKeyStore`, `DatabaseDecryptionMigration` (+sqlcipher dep), `WorkManagerAutoBackupScheduler`+`BackupWorker`, `SystemNotifier`/notification platform, `BiometricAuthenticator`, `GoogleDriveBackupRemote` | `shared/androidMain` (interface impls bound via the android Koin platform module) |
| `FirebaseAnalyticsClient`, `GoogleDriveAuthorizer` (play-services), `AppLocale`, `MainActivity`, `HisabakApp`, `nav/*`, `security/AppLock.kt`, `feature/sms/platform/*`, permission launchers, manifests, `google-services.json`, proguard | `androidApp` |
| iOS stubs for every platform port | `shared/iosMain` |
| Pure tests + `testutil` fakes (kotlin-test conversion) | `shared/commonTest`; JVM-bound tests (crypto, decryption migration) → `shared/androidHostTest` (JUnit4) |
| `feature/metrics/`, `feature/user/` | **deleted** (were unwired dead code) |

### Key design choices

- **Interfaces over expect/actual** — existing ports (`BackupCrypto`, `Notifier`, `Analytics`,
  `DriveAuthorizer`, `BiometricAuthenticator`, `AutoBackupScheduler`, …) get platform impl classes
  bound in per-platform Koin modules. `expect`/`actual` is reserved for non-interface seams:
  `platformModule(): Module`, the Room database builder, the DataStore path, a
  `LocalizedDateFormatter` factory, and the motion-scale reader.
- **Flavors/BuildConfig** — `prod`/`staging` stay in `androidApp`; `shared` gets a plain
  `AppConfig(seedData, smsAutoCapture, isDebug)` in commonMain, built from `BuildConfig` in
  androidApp and injected via Koin. The staging `RECEIVE_SMS` manifest overlay moves to
  `androidApp/src/staging/`.
- **java.time → kotlinx-datetime 0.7.x** (has `YearMonth`; `Instant` is `kotlin.time.Instant`) —
  direct rewrite, done **in place before file moves** so the full JUnit suite validates it.
  Locale-aware date display goes behind a `LocalizedDateFormatter` port (android impl keeps
  java.time). Room TypeConverters updated schema-neutrally (no DB version bump; schema JSON must
  not change).
- **Navigation stays in `androidApp` for Phase A** (androidx Navigation 3); screens already take
  nav lambdas. JetBrains multiplatform Nav3 (`org.jetbrains.androidx.navigation3`) is the Phase B
  path.
- **Toolchain** — Kotlin 2.2.10 → 2.4.10, KSP → matching, `org.jetbrains.compose` 1.11.1 +
  `org.jetbrains.compose.material3` 1.11.0-alpha07, Room → latest with the `androidx.room` Gradle
  plugin, kotlinx-datetime 0.7.x. AGP 9.1.1 and Gradle 9.3.1 stay. Desugaring stays
  androidApp-only.
- **iOS stub policy** (all marked `// TODO(Phase-B)`, grep-able): analytics/notifier/scheduler =
  no-op; crypto + passphrase store = `throw NotImplementedError` (a silent no-op would corrupt
  backups); biometric = "unavailable" result; `DriveAuthorizer.authorize()` = new `Unavailable`
  outcome; DB builder + DataStore path = **real** implementations (BundledSQLiteDriver / Documents
  dir — free correctness); motion scale = 1f.

## PR sequence (each leaves develop green + releasable)

- [x] **Step 0 (not a PR)** — `android` reference branch created from `ee44969` (v1.9.0) and pushed.
- [x] **PR 0 — Dead-code cleanup + this plan doc** (#90) *(low risk)*: delete `feature/metrics/` +
  `feature/user/`; document the migration + the `android` branch.
- [x] **PR 1 — Toolchain bump on current structure** (#91) *(risky, isolated)*: Kotlin 2.4.10, KSP, Room,
  coroutines/serialization bumps as required. No file moves. Full suite + staging build.
- [x] **PR 2 — Restructure** *(mechanical but wide)*: `git mv app androidApp`; create `shared`
  (android + iOS targets, wizard-style `Platform` expect/actual sample, empty iosMain from day
  one); root aggregate task `unitTests` (`:androidApp:testProdDebugUnitTest` +
  `:shared:testAndroidHostTest` — verify the exact AGP-generated host-test task name). Same-PR
  consistency updates: `.claude/hooks/run-tests.sh` → `./gradlew unitTests`,
  `.github/workflows/{test,release,distribute}.yml` (`app/` → `androidApp/` paths + task),
  CLAUDE.md, README, `docs/testing.md`, `docs/cd.md`, `.claude/skills/{git-workflow,feature}`.
- [x] **PR 3 — kotlinx-datetime swap in place** *(risky, semantic)*: rewrite the ~50 `java.time`
  files (Instant/LocalDate/TimeZone/YearMonth/daysUntil/Format); add the `LocalizedDateFormatter`
  port; Room TypeConverters; add boundary tests (month ends, DST) first; schema JSON diff must be
  empty.
- [x] **PR 4 — Pure core → commonMain**: `core/common`, `core/domain`, all `feature/*/domain`,
  SMS engine + codec + monitors + seed data → `shared/commonMain`; their tests →
  `shared/commonTest` (JUnit4 → kotlin-test). The fakes moved into a new **`:testutil` KMP
  module** (`api(project(":shared"))`) so both `shared/commonTest` and `androidApp/src/test`
  (ViewModel tests) share them — multiplatform test-fixtures don't exist yet.
  `MainDispatcherRule` (JUnit4 `TestWatcher`) and `FakeDriveAuthorizer` (Intent-typed until
  PR 7) stayed in `androidApp/src/test`, as did the JVM-bound tests (`AesGcmBackupCrypto*`,
  `BackupUseCasesTest`, `DatabaseDecryptionMigrationTest`, `BaseViewModelTest`,
  `CompactAmountTest`, all ViewModel tests). `CategoryLimitMonitor` now depends on a domain
  `CategoryLimitAlertStore` port (Room DAO adapter in androidApp). Test count unchanged:
  186 (71 androidApp + 115 shared, + the `PlatformTest` sample).
- [x] **PR 5 — Room KMP** *(risky)*: entities/DAOs/DB/repos (incl. `RoomBackupRepository` +
  `DatabaseSeeder`, on `useWriterConnection` + `immediateTransaction`) → commonMain;
  `androidx.room` plugin + per-target KSP in `shared`; `@ConstructedBy` + expect
  `HisabakDatabaseConstructor`; plain per-platform `hisabakDatabaseBuilder(...)` functions
  (androidMain keeps the framework driver — no `setDriver` — with androidApp's Koin module still
  running `DatabaseDecryptionMigration` pre-open; iosMain uses BundledSQLiteDriver + Documents
  dir). `MIGRATION_1_2` rewritten to the `SQLiteConnection` form (SQL unchanged);
  `DropSyncColumnsSpec` uses repeated `@DeleteColumn` (the explicit `.Entries` container isn't
  unwrapped by native KSP). `androidApp/schemas/` → `shared/schemas/` verbatim — the room plugin
  keeps the same flat `<db-class>/<version>.json` layout (no per-target subdirs) and
  `copyRoomSchemas` regenerates a byte-identical v3 JSON (asserted by delete + regenerate + diff).
  `sqlite` bumped 2.5.1 → 2.6.2 (the androidx.sqlite Room 2.8.4 ships against); sqlcipher deps
  moved to `shared` androidMain.
- [x] **PR 6 — DataStore/preferences → commonMain**: `expect` store-path provider (android
  `filesDir`, iOS Documents dir). `AppLocale` stays androidApp.
- [x] **PR 7 — DriveAuthorizer contract redesign** (Koin `platformModule()` split deferred to PR 9, where the ViewModel moves make the platform boundary real): remove the `Intent`/`IntentSender`
  leak — `NeedsConsent` carries an opaque common `ConsentRequest`; an android-side
  `DriveConsentLauncher` in androidApp does the IntentSender launch; `resultFrom(Intent?)` moves
  into the android impl; add `AuthorizeOutcome.Unavailable`. Backup/Restore VMs + Routes reworked
  (tests via the existing `FakeDriveAuthorizer`). Koin: shared modules (pure) +
  `expect fun platformModule()` (android/iOS actuals) + androidApp module (Firebase,
  GoogleDriveAuthorizer, AppLocale).
- [x] **PR 8 — Resources + theme + components → commonMain** *(riskiest UI PR)*: strings →
  composeResources (values + values-ar, plurals); `R.string` → `Res.string` rewrite; bundled fonts
  (DM Sans, Geist Mono, Tajawal — subset weights); theme/components/DonutChart;
  `Motion.kt` expect/actual (Vico stays the android artifact — the `multiplatform-m3` swap moves
  to PR 9 with the dashboard). Mandatory manual EN+AR QA (RTL, Arabic-Indic digits, plurals,
  bidi amount rows) before merge.

  **PR 8 spike findings (CMP resources 1.11.1, verified against the runtime/plugin sources):**
  - *Locale resolution:* CMP resolves values/values-ar off the **process default locale** —
    `androidx.compose.ui.text.intl.Locale.current` in composition, `java.util.Locale.getDefault()`
    + `Resources.getSystem()` in `getSystemResourceEnvironment()` — **not** the wrapped Activity
    context from `attachBaseContext`. `AppLocale.wrap` already calls `Locale.setDefault(locale)`,
    which is now load-bearing for CMP (documented in `AppLocale`).
  - *Format args:* CMP formatting is plain string interpolation —
    `Regex("""%(\d+)\$[ds]""")` replaced with `arg.toString()`. Consequences: only positional
    `%1$d`/`%1$s` work (bare `%d` is ignored), **no** locale-aware digit formatting (the `nu-arab`
    numbering extension does nothing), and **no `%%` unescaping** (the shared strings now use a
    literal `%`). Numeric args shown to users therefore go through the shared
    `localizedFormatArg(n)` (Arabic-Indic digits baked into the arg); `compactAmountParts` was
    rewritten in pure Kotlin to emit Arabic-Indic digits + `٬`/`٫` separators itself.
    Escapes: CMP handles `\n`, `\t`, `\uXXXX`, `\\` but **not** `\'`/`\"` — quotes were unescaped
    in the shared copies.
  - *Arabic plurals:* CMP bundles full CLDR plural rules; `ar` maps to the 6-category rule list
    (zero/one/two/few/many/other) with fallback to `other` — Arabic quantity selection works, so
    all plurals moved to composeResources.
  - *AGP KMP library gotcha:* `com.android.kotlin.multiplatform.library` needs
    `androidResources.enable = true` on the `android` target or the CMP asset-copy task is left
    unwired (`copyAndroidMainComposeResourcesToAndroidAssets` fails / resources missing from the
    APK → runtime `MissingResourceException`).
  - *Strings split:* 303 keys (299 strings + 4 plurals) moved to
    `shared/src/commonMain/composeResources/values{,-ar}/`; 10 stayed in androidApp res (the
    notification channel/content strings read via `Context.getString` outside Compose), plus the
    flavor-generated `app_name`.
  - *Fonts:* OFL static TTFs served by Google Fonts (css2 API → fonts.gstatic.com, the same
    binaries the previous downloadable-fonts provider fetched), bundled in
    `shared/src/commonMain/composeResources/font/` — DM Sans 400/500/600/700
    (`fonts.gstatic.com/s/dmsans/v17/…`), Geist Mono 400/500/600
    (`fonts.gstatic.com/s/geistmono/v6/…`; Bold styles nearest-match to 600 as before), Tajawal
    400/500/700 (`fonts.gstatic.com/s/tajawal/v12/…`; Tajawal has no 600). ~576 KB total;
    staging-debug APK grew ~1.4 MB (fonts + CMP resources runtime + string CVRs). The
    `ui-text-google-fonts` dependency and `font_certs.xml` were removed.
- [x] **PR 9 — Screens + ViewModels → commonMain; iOS gate**: every feature's Screens,
  Contracts, buses, and ViewModels (+ `ManageRoute`/`ManageViewModel`) moved to
  `shared/commonMain` (packages unchanged); only five **thin Routes** stayed androidApp-side for
  launchers/Intents (`SmsInboxRoute` + `OnboardingRoute` permission launchers, `SettingsRoute`
  enroll-credential launcher + FragmentActivity biometric wiring + `AppLocale`/`recreate()`,
  `BackupRoute`/`RestoreRoute` Drive-consent IntentSender launchers). Date display went behind
  the **`LocalizedDateFormatter` port** (`ui/format/`, `LocalDateFormatter` CompositionLocal;
  android impl wraps java.time/DateUtils/Formatter, pure-kotlinx `BasicLocalizedDateFormatter`
  default doubles as the iOS Phase A behavior); `DateFormats.kt` was absorbed. Build flags went
  behind a common `AppConfig(seedData, smsAutoCapture, isDebug, versionCode)` built from
  `BuildConfig` in androidApp. `BiometricAvailability` interface extracted
  (`core/domain/security/`); Vico swapped to `multiplatform-m3` **2.5.2** (charts in commonMain;
  the swap forced **compileSdk 36.1 → 37**); Koin split into `sharedModules`
  (`di/SharedModules.kt`) + androidApp `platformModule`/`analyticsModule` + a complete
  `iosMain` stub set (`di/IosPlatformModule.kt`, `core/platform/IosStubs.kt`, all
  `TODO(Phase-B)`; DB + DataStore real). `MainViewController` placeholder added
  (`shared/iosMain`); `ios-compile.yml` CI job added (`macos-14`, main + test iOS compile,
  path-filtered). All ViewModel tests moved to `shared/commonTest` on kotlin-test
  (`MainDispatcherTest` base replaces the JUnit4 rule; `FakeBackupCrypto` replaces the JVM
  `AesGcmBackupCrypto` in VM tests) — 191 tests preserved (172 shared + 19 androidApp
  JVM-bound).

**Phase A exit criteria — met:** `./gradlew unitTests` green (191) ·
`:shared:compileKotlinIosSimulatorArm64` + `:shared:compileTestKotlinIosSimulatorArm64` green
(CI job `ios-compile.yml`) · staging build assembles (final emulator QA in EN + AR runs on the
PR) · docs/skills/CI consistent.

**Remaining for Phase B:** Xcode `iosApp` target consuming `MainViewController`; multiplatform
Navigation 3 (nav/, MainActivity scaffolding are still androidx-only); real iOS actuals for
every `TODO(Phase-B)` stub (Keychain passphrase store, CryptoKit AES-GCM, LocalAuthentication,
BGTaskScheduler, Ktor Drive remote + ASWebAuthenticationSession OAuth, analytics/notifier,
locale-aware `LocalizedDateFormatter`, real `AppConfig.versionCode`); iOS locale/digit strategy;
App Store setup.

## Verification per PR

- Every PR: `./gradlew testProdDebugUnitTest` (→ `unitTests` from PR 2) green; test-count parity
  on move PRs.
- PR 1/2/8: install + smoke the staging build on an emulator (screenshots EN + AR).
- PR 5: on-device migration check (existing v3 DB opens; a seeded ≤1.8.x encrypted DB still
  decrypts).
- PR 8: APK size diff (bundled fonts ~1–2 MB expected).
- PR 9: iOS compile job green = Phase A done.

## Risk register (top items)

| Risk | Mitigation |
|---|---|
| Kotlin 2.4.10 × KSP × Room × Koin matrix friction | Isolated in PR 1; bump Koin to latest 4.x if needed |
| AGP 9.1.1 KMP-library DSL/task names differ from the wizard (9.0.1) | Verify with `:shared:tasks` in PR 2; the `unitTests` alias absorbs churn |
| Room KMP schema regeneration / migration compat | Byte-identical schema assert; schemas moved verbatim; device check |
| CMP resources vs `nu-arab` Arabic-Indic digits + wrapped locale | Spike at PR 8 start; fallback: `localizeDigits` in common helpers / a number-format port |
| kotlinx-datetime semantic drift | Swap done in place under the full JUnit suite + added boundary tests |
| Vico multiplatform API drift from 2.0.2 | Isolated to `VicoCharts.kt`; pin a matching version or budget a small rewrite |
| Nav3 androidx ↔ JB lifecycle version skew | Screens use plain lambdas (already the pattern); align lifecycle versions |

## Phase B — real iOS app

Goal: a functional Hisabak iOS app built from the same `shared` code. Android stays releasable
after every PR; iOS capability grows PR by PR. Local iOS builds work (Xcode + iPhone
simulators); CI keeps the compile + app-build gates.

### PR sequence

- [x] **PR B1 — Xcode `iosApp` skeleton** (#100): wizard-pattern `iosApp/` (pbxproj +
  `Configuration/Config.xcconfig` + SwiftUI shell wrapping `MainViewController()` from the
  static `Shared` framework via `embedAndSignAppleFrameworkForXcode`); a **shared `iosApp`
  scheme** so `xcodebuild` works headlessly; portrait-only, bundle id `com.hisabak`,
  `TEAM_ID` empty (simulator needs no signing). CI: `ios-compile.yml` gains an app-build job
  (`xcodebuild … -destination 'iOS Simulator'`, path-filtered to `iosApp/**` too). Template
  app icon retained (real icon in PR B6). This plan section.
- [ ] **PR B2 — Multiplatform navigation**: `nav/` (`NavKeys`, `NavigationState`,
  `BottomSheetScene`, `NavTransitions`) + the app shell move to `shared/commonMain` on JB
  multiplatform Navigation 3 + JB lifecycle 2.11: the shared root is `HisabakRoot(PlatformSlots)`
  (`HisabakRoot.kt` — theme resolution, launch-stage flow, tabbed NavDisplay shell), where
  `PlatformSlots` carries the per-platform seams (the five thin Routes, app-lock gate,
  notification-permission effect, system-bar styler). `MainActivity` builds the Android slots;
  `MainViewController` starts Koin (`startIosApp`) and renders the iOS slots (`IosRoutes.kt`,
  inert seams). Versions: androidx `navigation3-runtime` 1.1.4 (multiplatform upstream),
  JB `navigation3-ui` 1.1.1 (androidx package names, so moved files keep their imports;
  API drift: `sceneStrategy`+`.then()` → `sceneStrategies` list), JB lifecycle 2.10.0 → 2.11.0.
  CI fix ridden along: the `iosApp simulator build` job needs an Xcode 26 toolchain
  (CMP 1.11.x references iOS 26 UIKit symbols — `UIViewLayoutRegion`), so it runs on
  `macos-26`; the compile job moved off deprecated `macos-14` to `macos-15`.
  Exit: all five tabs browsable on the simulator with stub platform behavior.
- [ ] **PR B3 — iOS actuals, tier 1 (local UX)**: `LocalizedDateFormatter` over
  `NSDateFormatter` (+ the Arabic/locale digit strategy), motion-scale from
  `UIAccessibility`, `BiometricAuthenticator` over LocalAuthentication,
  notifications over `UNUserNotificationCenter`, app-lock gate on the iOS root,
  real `AppConfig` values from the bundle.
- [ ] **PR B4 — iOS actuals, tier 2 (backup)**: Keychain passphrase/account stores, AES-GCM
  `BackupCrypto` (CommonCrypto/CryptoKit — must round-trip with the Android format),
  `BackupRemote` over `NSURLSession`, Google OAuth via `ASWebAuthenticationSession`,
  auto-backup via `BGTaskScheduler`.
- [ ] **PR B5 — iOS feature shape**: no SMS capture exists on iOS — gate the SMS tab and
  capture affordances off `AppConfig.smsAutoCapture` (manual entry + simulated samples
  remain); audit remaining `TODO(Phase-B)` stubs to zero or explicit accepted no-ops
  (analytics may stay no-op until B6).
- [ ] **PR B6 — Firebase on iOS + app icon**: Crashlytics/Analytics via the gitlive KMP
  wrappers **or** keep the no-op (decide then); real 1024pt app icon.
- [ ] **PR B7 — App Store setup** *(user-gated: needs the Apple Developer account)*: signing
  (`TEAM_ID`), privacy manifest + nutrition labels, launch screen, TestFlight lane in CI.

### Phase B verification

- Every PR: `./gradlew unitTests` green; `ios-compile.yml` green (compile + app build).
- B1: app boots on the iPhone simulator showing the placeholder (screenshot).
- B2: manual QA on the simulator — all tabs, EN + AR, add/edit transaction round-trip.
- B4: cross-platform backup round-trip (Android backup → iOS restore and back).

### Phase B risk register

| Risk | Mitigation |
|---|---|
| JB multiplatform Nav3 API/version drift from androidx `1.0.0` | Spike at B2 start; nav layer is small (3 files + MainActivity shell) |
| AES-GCM/PBKDF2 interop mismatch corrupts cross-platform restores | Golden-file tests: Android-produced ciphertext decrypts on iOS and vice versa |
| Google OAuth without Play Services (client id / redirect scheme) | Separate iOS OAuth client in the same GCP project; `ASWebAuthenticationSession` code flow |
| BGTaskScheduler's opportunistic scheduling ≠ WorkManager periodic guarantees | Accept best-effort on iOS; document the difference in Settings copy |
