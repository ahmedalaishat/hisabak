# Kotlin Multiplatform migration plan

Hisabak is migrating from a single-module Android app to **Kotlin Multiplatform + Compose
Multiplatform**, in two phases:

- **Phase A (in progress):** a running, fully-tested **Android** app restructured into KMP form —
  all common code (domain, data, ViewModels, **and Compose UI**) in `shared/commonMain`, Android
  platform code in `shared/androidMain` + a thin `androidApp`, and **stub iOS actuals** in
  `iosMain` so the iOS targets compile. No iOS app yet.
- **Phase B (later):** real iOS support — Xcode `iosApp`, real actuals, multiplatform Navigation 3.

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
│   ├── schemas/                   # moved verbatim from app/schemas/
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
- [ ] **PR 0 — Dead-code cleanup + this plan doc** *(low risk)*: delete `feature/metrics/` +
  `feature/user/`; document the migration + the `android` branch.
- [ ] **PR 1 — Toolchain bump on current structure** *(risky, isolated)*: Kotlin 2.4.10, KSP, Room,
  coroutines/serialization bumps as required. No file moves. Full suite + staging build.
- [ ] **PR 2 — Restructure** *(mechanical but wide)*: `git mv app androidApp`; create `shared`
  (android + iOS targets, wizard-style `Platform` expect/actual sample, empty iosMain from day
  one); root aggregate task `unitTests` (`:androidApp:testProdDebugUnitTest` +
  `:shared:testAndroidHostTest` — verify the exact AGP-generated host-test task name). Same-PR
  consistency updates: `.claude/hooks/run-tests.sh` → `./gradlew unitTests`,
  `.github/workflows/{test,release,distribute}.yml` (`app/` → `androidApp/` paths + task),
  CLAUDE.md, README, `docs/testing.md`, `docs/cd.md`, `.claude/skills/{git-workflow,feature}`.
- [ ] **PR 3 — kotlinx-datetime swap in place** *(risky, semantic)*: rewrite the ~50 `java.time`
  files (Instant/LocalDate/TimeZone/YearMonth/daysUntil/Format); add the `LocalizedDateFormatter`
  port; Room TypeConverters; add boundary tests (month ends, DST) first; schema JSON diff must be
  empty.
- [ ] **PR 4 (splittable 4a/4b/4c) — Pure core → commonMain**: `core/common` + testutil fakes →
  commonTest (JUnit4 → kotlin-test; `MainDispatcherRule` → `@BeforeTest/@AfterTest`
  `Dispatchers.setMain/resetMain` helper); `core/domain` + all `feature/*/domain`; SMS engine +
  codec + monitors + seed data. Identical test counts before/after, called out per PR.
- [ ] **PR 5 — Room KMP** *(risky)*: entities/DAOs/DB/repos → commonMain; `androidx.room` plugin;
  `@ConstructedBy`; `expect fun databaseBuilder()` (android actual wires
  `DatabaseDecryptionMigration` pre-open; iOS actual uses BundledSQLiteDriver); `app/schemas/` →
  `shared/schemas/` verbatim, byte-identical v3 JSON asserted.
- [ ] **PR 6 — DataStore/preferences → commonMain**: `expect` store-path provider (android
  `filesDir`, iOS Documents dir). `AppLocale` stays androidApp.
- [ ] **PR 7 — DriveAuthorizer contract redesign + Koin split**: remove the `Intent`/`IntentSender`
  leak — `NeedsConsent` carries an opaque common `ConsentRequest`; an android-side
  `DriveConsentLauncher` in androidApp does the IntentSender launch; `resultFrom(Intent?)` moves
  into the android impl; add `AuthorizeOutcome.Unavailable`. Backup/Restore VMs + Routes reworked
  (tests via the existing `FakeDriveAuthorizer`). Koin: shared modules (pure) +
  `expect fun platformModule()` (android/iOS actuals) + androidApp module (Firebase,
  GoogleDriveAuthorizer, AppLocale).
- [ ] **PR 8 — Resources + theme + components → commonMain** *(riskiest UI PR)*: strings →
  composeResources (values + values-ar, plurals); `R.string` → `Res.string` rewrite; bundled fonts
  (DM Sans, Geist Mono, Tajawal — subset weights); theme/components/DonutChart; Vico →
  `multiplatform-m3`; `Motion.kt` expect/actual. **Spike first:** CMP resource formatting vs the
  Arabic `nu-arab` digit config. Mandatory manual EN+AR QA (RTL, Arabic-Indic digits, plurals,
  bidi amount rows) before merge.
- [ ] **PR 9 (stackable per feature) — Screens + ViewModels → commonMain; iOS gate**:
  feature-by-feature (dashboard → transactions → category/brand/budget → settings/onboarding →
  sms → backup/restore); permission-launcher screens take callback params from the androidApp nav
  layer; complete the iosMain stub set; add an `ios-compile.yml` CI job (`macos-14`,
  `:shared:compileKotlinIosSimulatorArm64`, path-filtered to `shared/**`).

**Phase A exit criteria:** `./gradlew unitTests` green · staging app fully functional (manual QA
in EN + AR) · `:shared:compileKotlinIosSimulatorArm64` green in CI · docs/skills/CI consistent.

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

## Phase B (out of scope for now)

Xcode `iosApp` + `MainViewController` (wizard pattern), nav → JB multiplatform Nav3, real iOS
actuals (Keychain, CryptoKit AES-GCM, LocalAuthentication, BGTaskScheduler, Ktor Drive remote +
ASWebAuthenticationSession OAuth, Firebase via gitlive or stub), iOS locale/digit strategy,
App Store setup.
