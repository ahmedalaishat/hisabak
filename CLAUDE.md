# Hisabak — Claude Code Context

## What This App Is

**Hisabak** (Arabic: "your account") is a personal finance tracker for Android.
It auto-captures bank transactions from SMS messages, lets users categorize spending,
track budgets, and visualize financial trends.

Inspired by the [Hisabi](https://github.com/hisabi-app/hisabi) web app by Saleem Hadad.
Domain model mirrors Hisabi so concepts transfer cleanly.

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Clean Architecture per feature (domain / data / presentation)
- **DI:** Koin, split by platform: the pure modules (use cases, repos, DAOs, ViewModels via the
  multiplatform `viewModel {}` DSL) live in `shared/commonMain` — `di/SharedModules.kt` exposes
  `sharedModules` — and each platform adds its bindings on top: androidApp's
  `di/PlatformModule.kt` (+ `AnalyticsModule`) via `appModules`, iOS's
  `shared/src/iosMain/.../di/IosPlatformModule.kt` (everything real since Phase B tiers 1–2
  except `Analytics`, still a no-op pending the B6 Firebase decision; the module is a
  `fun iosPlatformModule(gcmCipher)` — the Swift CryptoKit bridge is injected from
  `iOSApp.swift` via `startIosApp`).
- **Async:** Kotlin Coroutines + Flow
- **State:** ViewModel + `collectAsStateWithLifecycle`
- **Charts:** Vico (`com.patrykandpatrick.vico:multiplatform-m3`; charts in
  `shared/commonMain` — `feature/dashboard/presentation/components/VicoCharts.kt`)
- **Crash reporting:** Firebase Crashlytics. Wired via the `google-services` + `firebase-crashlytics`
  Gradle plugins (config in `androidApp/google-services.json`, project `hisabak-finance-tracking`).
  Collection is gated on `!BuildConfig.DEBUG` in `HisabakApp` — **on in release, off in debug** —
  so local runs never reach the dashboard. Reports carry no financial/personal data; the
  privacy policy (`docs/privacy.html`) discloses it. **iOS: Crashlytics only, natively in
  Swift** (`firebase-ios-sdk` via SPM, configured in `iOSApp.init`, guarded on
  `GoogleService-Info.plist` presence, same debug/release gating; no event analytics —
  `NoopAnalytics` is deliberate until iOS has real users).
- **Analytics:** Firebase Analytics (no extra plugin — uses the same `google-services` setup),
  collection gated the same way (`!BuildConfig.DEBUG`, on in release). Behind a small domain
  abstraction: the `Analytics` interface + the `AnalyticsEvent` catalogue in
  `core/domain/analytics/` (the single place event names/params live), with
  `FirebaseAnalyticsClient` in `core/data/analytics/`, registered via `di/AnalyticsModule.kt`.
  Inject `Analytics` into a ViewModel/use case and log on the success path; tests use
  `testutil/FakeAnalytics`. **Strict no-PII:** events only carry booleans, enums, and coarse amount
  buckets — never raw amounts, names, notes, or SMS text. Screen views are reported manually from
  `MainActivity` (single-Activity Compose app, so Firebase's auto screen tracking doesn't fire).
- **Storage:** Room **KMP** (SQLite) — entities/DAOs/mappers, `HisabakDatabase`
  (`@ConstructedBy`), migrations, and the `Room*Repository` impls all live in
  `shared/src/commonMain` (`core/data/local/` + per-feature `data/local/`); platform
  `hisabakDatabaseBuilder(...)` functions in `shared/src/androidMain` (framework SQLite driver,
  as before) and `shared/src/iosMain` (BundledSQLiteDriver, Documents dir). The `androidx.room`
  Gradle plugin + per-target KSP run in `shared`; the schema is exported to `shared/schemas/`
  (committed); bump the DB version and add a real `Migration` for any entity change —
  **release builds don't destructively fall back** (debug builds do, for fast iteration).
- **At-rest protection:** the database is plain (unencrypted) SQLite, relying on Android's
  file-based encryption of app-private storage; the live-device threat is App Lock's job. The
  app-level SQLCipher layer that versions ≤1.8.x used was **removed** (its Keystore-wrapped key was
  never auth-gated, so it added little over FBE). Databases carried over from those versions are
  decrypted once, in place, before Room opens (`DatabaseDecryptionMigration`,
  `shared/src/androidMain` `core/data/local/security/` — keys off the `SQLite format 3` header, unlocks with the legacy
  passphrase from `KeystoreDatabaseKeyStore`, runs `sqlcipher_export`, verifies + atomically swaps,
  then clears the stored key; idempotent, retries on crash; loads the SQLCipher native lib only when
  an encrypted file is actually found). The `sqlcipher-android` dependency is retained **only** for
  this migration — drop it, the migration, and the key store once the ≤1.8.x upgrade window closes. Lightweight app prefs (the onboarding flag, the
  theme mode, and the `appLockEnabled` flag) use DataStore (`core/data/preferences/`) behind the
  `AppPreferences` interface (`core/domain/`); `MainActivity` reads `themeMode` and feeds the
  resolved boolean to `HisabakTheme(darkTheme=…)`.
- **Localization:** English + Arabic (RTL). User-facing strings live in **Compose Multiplatform
  resources** — `shared/src/commonMain/composeResources/values/strings.xml` (+ `values-ar/`),
  generated `Res` class in `com.hisabak.shared.resources` — read via CMP's
  `stringResource`/`pluralStringResource` (`org.jetbrains.compose.resources`) — **don't hardcode
  UI text**. The only Android-resource strings left in `androidApp/src/main/res/values{,-ar}/`
  are the 10 notification strings read via `Context.getString` outside Compose
  (`AndroidNotificationStrings`, `SystemNotifier`) plus the flavor-generated `app_name`.
  The in-app language switch is framework-only (no appcompat, so Navigation 3's
  `NavDisplay` keeps its `ComponentActivity` dispatcher owner): the chosen tag is stored by
  `AppLocale` (androidApp, `core/data/preferences/`) in a synchronous SharedPreferences,
  `MainActivity` overrides `attachBaseContext` to wrap the Context in that locale + layout
  direction, and the Settings screen saves the tag then calls `recreate()`. **The
  `Locale.setDefault(...)` call in `AppLocale.wrap` is load-bearing:** CMP resolves
  values/values-ar off the process default locale (`Locale.current`), not the wrapped Context.
  **iOS uses the OS-native path instead:** `CFBundleLocalizations` declares en+ar so iOS
  offers the per-app language picker in the Settings app, and the in-app language row
  deep-links there (`IosSettingsRoute`); CMP resolves strings off the resulting locale.
  **Numbers follow the language:** English uses Western digits, Arabic uses **Arabic-Indic**
  digits. CMP's `%1$d` interpolation is *not* locale-aware (plain `toString()` substitution,
  positional `%1$d`/`%1$s` only, no `%%` escape), so numeric format args are localized at the
  call site via `localizedFormatArg(n)`; amounts get Arabic-Indic digits + `٬`/`٫` separators
  from the pure-Kotlin `compactAmountParts(major, arabic)`, and the few fixed-format numbers
  use `localizeDigits(text, arabic)` (all in `ui/components/HisabakComponents.kt`, shared).
  Arabic plural selection works in CMP (full CLDR rules, all 6 Arabic categories). Amounts
  always read LTR (glyph · number · K/M-or-أ/م suffix) via a forced `LayoutDirection.Ltr`,
  with the number and suffix as separate `Text`s so Arabic-Indic digits don't bidi-reorder.
  Amounts keep the dirham glyph in both languages.
- **SMS parse templates (user-manageable):** the regex templates live in Room (`sms_templates`,
  seeded from the 10 Hisabi defaults in `DefaultSmsTemplates`; defaults are disable-only). The
  detector is `ObservingSmsTemplateDetector` (data/parser) — a synchronous `detect` over a
  compiled snapshot refreshed from `SmsTemplateRepository` on `APPLICATION_SCOPE`; matching is
  **specificity-ranked** (`rankTemplates`: most literal anchor first, user templates over
  defaults on ties) and a match whose `{amount}` capture isn't a positive number falls through.
  Users create templates **by example** (Settings → SMS parsing, or "Create template" on an
  unparsed inbox message): tag amount/brand/date/skip spans on a sample; the pure derivation /
  reconstruction / suggestion logic is `feature/sms/domain/template/TemplateSample.kt`
  (user templates store their sample so editing re-tags via `reconstructSpans`, which
  `matchEntire`s). Save enforces a 10-char literal anchor minimum + a numeric amount tag, and
  previews how many stored messages the draft also matches. Templates ride in the backup
  envelope (schema v5).
- **On-device AI (SMS parse fallback + smart entry):** bank SMS that match no regex template get
  parsed by the platform's on-device model into a **confirm-first suggestion** (never an
  auto-created transaction). The port is `AiSmsParser` + `SuggestAiParse/ConfirmAiSuggestion/
  DismissAiSuggestionUseCase` in `feature/sms/domain/ai/` (commonMain; the shared `sanitize`
  step owns acceptance rules, prompts live platform-side). The same port's `parseFreeText`
  powers the inbox's **free-text capture**: an unmatched manual paste comes back from ingest
  as `CaptureResult.StoredUnparsed` (a success, paste-source only — background sources keep
  the detached fallback) and `SmsInboxViewModel` drives
  `SuggestAiParseUseCase(freeText = true)` with a visible spinner — the note prompt carries
  today's date so relative wording resolves, and its plausibility window reaches a year back
  (vs 7 days for live SMS), never the future. Brand snapping is the shared `canonicalizeBrand`
  (`feature/sms/domain/ai/BrandCanonicalizer.kt`). The add-transaction sheet is deliberately
  manual-only — every AI affordance is confirm-first and lives in the inbox. Parsing is **brand-aware**: the
  top-50 most-used brand names go into the prompt, and `canonicalize` (exact/substring/
  Levenshtein≤2) snaps the model's merchant string to an existing brand deterministically. Android:
  `GeminiNanoSmsParser` over the ML Kit GenAI **Prompt API** (`com.google.mlkit:genai-prompt`,
  beta — OS-managed Gemini Nano via AICore, flagship devices only). iOS: `AiSmsBridge` seam →
  `FoundationModelsSmsParser.swift` (Apple Foundation Models, iOS 26+, `@Generable`; injected
  via `startIosApp(gcmCipher, aiSmsBridge)` like the CryptoKit bridge). Unsupported devices
  report `Unavailable` and every AI affordance stays hidden. Inference is fully on-device —
  SMS text never leaves the phone; analytics events (`ai_parse_*`) stay PII-free.
- **Platform:** Android only, portrait, edge-to-edge. `minSdk 29`.
- **Dates & times: use kotlinx-datetime** (`kotlin.time.Instant`, `kotlinx.datetime.LocalDate` /
  `YearMonth` / `TimeZone`), **not `java.time`** — the code is KMP-bound and java.time doesn't
  exist on iOS. Locale-aware display formatting goes through the **`LocalizedDateFormatter`
  port** (`shared/commonMain` `ui/format/LocalizedDateFormatter.kt`): shared screens read it via
  the `LocalDateFormatter` CompositionLocal, `MainActivity` provides
  `AndroidLocalizedDateFormatter` (androidApp `ui/format/` — the single sanctioned `java.time` +
  `DateUtils`/`Formatter` site), iOS provides `IosLocalizedDateFormatter` (iosMain —
  NSDateFormatter/NSRelativeDateTimeFormatter/NSByteCountFormatter), and the composition
  default is the pure-kotlinx `BasicLocalizedDateFormatter` (English names). Core-library
  desugaring stays enabled in `androidApp` (`isCoreLibraryDesugaringEnabled` +
  `desugar_jdk_libs`) for that formatter path — API-34+ java.time additions like
  `LocalDate.ofInstant` throw `NoSuchMethodError` on older devices without it (caused the
  v1.5.0 launch crash).
- **App lock:** optional biometric/device-credential gate (Settings → Security, `appLockEnabled`
  pref). `androidx.biometric` `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (PIN
  fallback, no custom PIN UI). **`MainActivity` is a `FragmentActivity`** — required by
  `BiometricPrompt`; it still extends `androidx.activity.ComponentActivity` (NavDisplay keeps its
  dispatcher owner) and pulls in `androidx.fragment`, **not** appcompat, so the no-appcompat rule
  holds. `security/AppLock.kt` gates the nav (`AppLockGate`) and locks on cold start + on return
  past a grace window; the lock decision is the pure `shouldLock(...)` in
  `core/domain/security/` (CMP-ready), the prompt is `core/platform/security/BiometricAuthenticator`
  (Android glue). It's an access gate, **not** at-rest encryption.
- **Backup (Google Drive):** connect a Google account, back up to Drive's hidden **App Data Folder**,
  and restore — Settings → Data → `BackupKey` screen, plus a one-time post-onboarding restore page
  (`RestoreRoute`, gated by the `restoreOffered` pref). Settings: enable toggle, account row,
  optional encryption toggle + passphrase, auto-backup period (`AutoBackupPeriod`, incl. `NEVER`,
  default `NEVER`), and **Back up now**. Prefs on `AppPreferences` (`backupEnabled`,
  `backupEncryptionEnabled`, `autoBackupPeriod`, `restoreOffered`); the passphrase via
  `KeystoreBackupPassphraseStore` (Keystore AES-GCM, only IV+ciphertext persisted — **never
  plaintext**), the account email via `DataStoreBackupAccountStore`.
  - **Auth:** `DriveAuthorizer` (interface; fake in tests) → `GoogleDriveAuthorizer` using Google
    Identity **Authorization API** (`play-services-auth`) for the `drive.appdata` scope; consent runs
    through a `PendingIntent` launched by the Route. **Cloud setup is required** — see
    `docs/google-drive-backup-setup.md` (OAuth client by package + SHA-1; no secret in the app).
  - **Engine (destination-agnostic):** `RoomBackupRepository` (snapshot + replace-all in one
    immediate write transaction), `JsonBackupCodec` (kotlinx.serialization), `AesGcmBackupCrypto`
    (passphrase→PBKDF2→AES-256-GCM; `isEncrypted` sniffs the `HSBK` magic so unencrypted backups
    restore without a passphrase), `GoogleDriveBackupRemote` (`BackupRemote` over Drive v3 REST via
    `HttpURLConnection`). `RunBackupUseCase` / `RestoreFromRemoteUseCase` orchestrate; encryption is
    optional (caller passes the passphrase or null). `HisabakDatabase.SCHEMA_VERSION` is stamped into
    the envelope and gated on import.
  - **Auto-backup:** `AutoBackupScheduler` (domain interface + pure `autoBackupInterval(period)`) →
    `WorkManagerAutoBackupScheduler` enqueues unique periodic work that runs `BackupWorker`
    (CoroutineWorker resolving deps via Koin, default factory). Any network, silent; rescheduled from
    `BackupViewModel` (period/enable changes) and `HisabakApp` on launch. The passphrase is
    Keystore-stored (non-auth-gated) so encrypted auto-backups run unattended. Passkeys were rejected
    (need WebAuthn PRF + a server).
- **CMP migration:** the app is **Kotlin Multiplatform + Compose Multiplatform** — plan and PR
  sequence in `docs/kmp-migration.md`. **Phase A is complete**: all common code (domain, data,
  ViewModels, Compose UI) lives in `shared/commonMain`, the iOS targets compile against stub
  platform bindings (grep `TODO(Phase-B)`), and Android stays the only shipping app. **Phase B
  is in progress** (real iOS app, multiplatform Nav3, real iOS actuals — PR plan at the end of
  `docs/kmp-migration.md`): the Xcode **`iosApp/`** builds and runs the `Shared` framework's
  `MainViewController()` on the iOS simulator. The pure-Android line is frozen
  on the **`android` branch** (v1.9.0) as a reference. The standing rules hold: keep platform
  APIs (`Context`, `FragmentActivity`, `BiometricPrompt`, Keystore) out of `commonMain`, keep
  state/business logic as pure Kotlin, and keep Composables on multiplatform-safe APIs.

---

## Project Structure

Three Gradle modules plus the Xcode app (Phase A of the KMP migration is complete, Phase B in
progress — see `docs/kmp-migration.md`):

- **`androidApp/`** — a **thin Android shell** (`com.android.application`, prod/staging
  flavors, signing, Firebase): `MainActivity`, `HisabakApp`, `security/AppLock.kt`,
  the platform glue (SMS capture, `SystemNotifier`, WorkManager, Keystore stores,
  `GoogleDriveAuthorizer`/`GoogleDriveBackupRemote`, `AesGcmBackupCrypto`,
  `FirebaseAnalyticsClient`, `BiometricAuthenticator`, `AppLocale`,
  `AndroidLocalizedDateFormatter`), the Koin platform bindings (`di/PlatformModule.kt`),
  and the **thin Routes** that need launchers/Intents (`SmsInboxRoute`, `OnboardingRoute`,
  `SettingsRoute`, `BackupRoute`, `RestoreRoute`). Everything else is in `shared`.
- **`shared/`** — the KMP library (`org.jetbrains.kotlin.multiplatform` +
  `com.android.kotlin.multiplatform.library`; android + iosArm64 + iosSimulatorArm64,
  static `Shared` framework). `commonMain` holds the whole app minus the platform glue:
  domain + data (Room, DataStore), the SMS parsing engine, theme/components/icons/CMP
  resources, **all feature Screens + ViewModels** (plus the Routes with no platform
  touchpoints), the Vico charts, the pure Koin modules (`di/SharedModules.kt`), and the
  **nav layer + app shell** (`nav/`, `HisabakRoot.kt` — see Navigation below).
  `iosMain` has the stub platform bindings (`di/IosPlatformModule.kt`,
  `core/platform/IosStubs.kt`, all `TODO(Phase-B)`) and the `MainViewController`
  placeholder entry point. Source sets: `commonMain` / `androidMain` / `iosMain` +
  `commonTest` (kotlin-test) / `androidHostTest` (JUnit4).
- **`testutil/`** — a small KMP library with the shared test fakes
  (`com.hisabak.testutil`: `TestClock`, `TestData`, `Fake*` incl. `FakeBackupCrypto`),
  used by both `shared/commonTest` and `androidApp/src/test` (KMP has no multiplatform
  test-fixtures yet).
- **`iosApp/`** — the Xcode project (not a Gradle module; JetBrains wizard pattern): a SwiftUI
  shell whose `ContentView` wraps `MainViewController()` from the static `Shared` framework,
  built by the `embedAndSignAppleFrameworkForXcode` script phase. Config in
  `iosApp/Configuration/Config.xcconfig` (bundle id `com.hisabak`, `TEAM_ID` empty — simulator
  only until App Store setup; `GOOGLE_OAUTH_CLIENT_ID` for Drive backup, blank = unavailable). Build headlessly with
  `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination 'platform=iOS Simulator,name=iPhone 17'`.
  **Flavors mirror Android:** the `iosAppStaging` scheme builds the `DebugStaging`/`ReleaseStaging`
  configurations (`Staging.xcconfig` — bundle id `com.hisabak.staging`, "Hisabak STG", seeded demo
  data via the `HisabakFlavor` Info.plist key read by the shared `AppConfig`; `smsAutoCapture`
  stays false on iOS — no SMS API, the Shortcuts action is the near-automatic capture path).
  `Staging.xcconfig` `#include`s `Config.xcconfig`, so anything it doesn't override is inherited
  — note it carries **its own `GOOGLE_OAUTH_CLIENT_ID`**, since iOS OAuth clients key off the
  bundle id and the prod client is registered to `com.hisabak`.

Package layout (unchanged by the migration — files move between modules, packages stay):

```
com.hisabak
├── core/common/              shared value objects (Money, IDs)
├── ui/
│   ├── components/           shared Compose components
│   └── theme/                Material 3 theme (colors, typography, shapes)
└── feature/<name>/
    ├── domain/               entities, use cases, repository interfaces
    ├── data/                 repository implementations
    └── presentation/         Route (stateful) + Screen (stateless) + ViewModel
```

---

## Navigation

**Navigation 3, multiplatform** (`com.hisabak.nav`, `shared/commonMain` — androidx
`navigation3-runtime` (multiplatform upstream) + JetBrains `navigation3-ui` + JB
`lifecycle-viewmodel-navigation3`; the JB artifacts keep `androidx.*` package names).
5-tab bottom navigation, each tab a top-level destination with its own back stack. State is
retained per tab when switching; the user always exits the app through the **Dashboard**
(home) tab.

- `NavKeys.kt` — destination keys: `DashboardKey`, `TransactionsKey`, `SmsKey`,
  `ManageKey`, `SettingsKey` (top-level) + `TransactionEditKey/BrandEditKey/CategoryEditKey(id)` (children).
- `NavigationState.kt` — `NavigationState` (one back stack per tab), `Navigator`
  (`navigate`/`goBack`: back from a tab → home; back from home → exit), and `toEntries()`
  which wires the saveable-state + **ViewModel-store** entry decorators plus an
  **opaque-background decorator** (screens are content-only layers over the Scaffold
  background; slide-type transitions — e.g. the JB iOS default push — would otherwise show
  the previous destination through the incoming one; sheet entries are excluded). The
  ViewModel decorator scopes each screen's ViewModel to its `NavEntry`, so a popped
  destination's ViewModel is cleared — re-entering a screen starts fresh (no stale state).
  The `NavDisplay` container transition is pinned to a **cross-fade** on both platforms
  (tab switches must not animate like hierarchy pushes); full-screen children carry their
  own `fullScreenTransition()` metadata.
- `BottomSheetScene.kt` — `BottomSheetSceneStrategy`; the transaction add/edit destination
  is marked with `bottomSheet()` metadata so it renders as a modal bottom sheet overlay.
- `HisabakRoot.kt` (`shared/commonMain`, `com.hisabak`) — the whole app shell:
  theme resolution from prefs, the first-launch flow (onboarding → restore offer → app),
  and the `Scaffold` + `NavDisplay` (top bar / bottom nav / FAB derived from the current
  destination). Its **`PlatformSlots`** parameter carries the per-platform seams: the five
  thin Routes (onboarding, restore, SMS inbox, settings, backup), the app-lock gate, a
  notification-permission effect, and a system-bar styler. `MainActivity` supplies the
  Android slots; `MainViewController` (iosMain) starts Koin via `startIosApp()` and supplies
  the iOS slots (`IosRoutes.kt` — inert seams until their Phase B tier lands).

| Tab | Top-level key | Internal screens |
|-----|---------------|-----------------|
| Dashboard | DashboardKey | Single screen |
| Transactions | TransactionsKey | List → Edit (bottom sheet) |
| SMS | SmsKey | Inbox → template editor (full screen, from an unparsed message) |
| Manage | ManageKey | Brands/Categories list → Edit (full screen) |
| Settings | SettingsKey | Theme + language + app lock → Backup & restore / SMS parsing → template editor (full screen) |

Pattern: `List` → tap row or FAB → push `Edit(id?)` destination → Save/Cancel calls
`navigator.goBack()` → back to `List`.

---

## Domain Models

### Transaction
- `id`, `amount: Money`, `brandId`, `note?`, `occurredAt: Instant`, `sourceSmsId?`
- `amount` is signed for savings/investment-type brands: deposits positive, withdrawals
  **negative** (the edit sheet's deposit/withdrawal toggle). Income/expense amounts are always
  positive — direction comes from the category type. Downstream sums are raw, so buckets and
  brand totals net naturally (this is how lending is tracked: person-brand in a savings
  category, repayments as withdrawals).

### Brand
- `id`, `name`, `categoryId?`

### Category
- `id`, `name`, `type: CategoryType`, `color: String`, `icon: String`
- `CategoryType`: INCOME | EXPENSES | SAVINGS | INVESTMENT
- `color` options: green, blue, orange, red, teal, purple, pink, gray
- `icon` options: wallet, cart, briefcase, car, utensils, piggy-bank, home, film, book, heart, gift, plane

### Budget
- `id`, `name`, `amount: Money`, `startAt`, `endAt?`, `saving`, `period`, `reoccurrence`, `categoryIds`
- `Reoccurrence`: CUSTOM | DAILY | WEEKLY | MONTHLY | YEARLY

### Money
- `amountMinor: Long` (cents), `currency: Currency`

### SmsMessage
- `id`, `body`, `receivedAt: Instant`, plus parsed fields (brand, amount, status)

---

## Design System

This app uses the **Hisabak design system**. When building or editing UI, follow these rules.
The full system (tokens, component specs, screen prototypes) lives in
`.claude/skills/hisabak-design/`. **The Compose app is the source of truth** — the tokens are
already translated into `shared/src/commonMain/kotlin/com/hisabak/ui/theme/` (`Color.kt`,
`Type.kt`, `Shape.kt`, `Motion.kt`, `HisabakTheme.kt`) and shared components in
`shared/src/commonMain/kotlin/com/hisabak/ui/components/` — both **Compose Multiplatform**
(commonMain); keep them free of Android-only APIs. For production UI, use those
directly; see the design skill's `compose-bridge.md` for the token/component → Kotlin map.
Use the HTML/CSS kit only for throwaway visual mockups.

### Foundations

- **Theme:** wrap content in `HisabakTheme { }`. Light **and** dark are first-class — every
  screen must look right in both. The active mode follows the user's Settings choice
  (Light/Dark/System, persisted in DataStore), not just `isSystemInDarkTheme()`. Never hardcode
  hex; use `MaterialTheme.colorScheme.*`, `HisabakTheme.colors.*`, `HisabakType.*`, `Spacing.*`,
  `Sizing.*`.
- **Type:** DM Sans for UI; **Geist Mono with tabular figures for every amount**
  (`HisabakType.amount` / `amountLarge` / `amountHero` — composable getters reading
  `LocalHisabakFonts`). Amounts align in columns — don't use the sans font for money.
  **Arabic uses Tajawal** for both UI and amounts (one cohesive face — Geist Mono has no
  Arabic-Indic glyphs); the typography is built via the shared `hisabakTypography(family, arabic)`
  builder in `Type.kt`, and `AmountText`/`MoneyText` swap the figure family to Tajawal under
  Arabic. Fonts are **bundled OFL TTFs** in `shared/src/commonMain/composeResources/font/`
  (DM Sans 400/500/600/700, Geist Mono 400/500/600, Tajawal 400/500/700) loaded through CMP
  resources — the downloadable-fonts (Google Fonts provider) setup was removed.
- **Spacing:** 8dp grid. 16dp page margin, 16dp card padding, 12dp between cards.
  Touch targets ≥ 44dp.
- **Shape:** 12dp default card radius, 16dp hero/sheet, pill for buttons/chips/badges,
  14dp category icon tiles.
- **Depth:** prefer surface contrast over shadows. Flat cards use a 1dp outline; reserve
  real elevation for sheets, dialogs, menus, and the FAB.

### Color — the cardinal rule

**Green (`primary`) is meaningful, never decorative.** Reserve it for exactly three things:
1. money-positive values (income, positive trends)
2. the single primary action on a screen
3. the active bottom-nav tab

Everything else is neutral or a *semantic finance color*:
`income` green · `expense` coral · `savings` blue · `investment` purple.
Backgrounds are neutral gray (`background`), never green-tinted.
Category dots/tiles use the 8 `cat*` colors.

### Category Color Palette

| Key | Background | Foreground |
|-----|-----------|-----------|
| green | `#D1FAE5` | `#047857` |
| blue | `#DBEAFE` | `#2563EB` |
| orange | `#FFEDD5` | `#EA580C` |
| red | `#FEE2E2` | `#DC2626` |
| teal | `#CCFBF1` | `#0D9488` |
| purple | `#F3E8FF` | `#9333EA` |
| pink | `#FCE7F3` | `#DB2777` |
| gray | `#F3F4F6` | `#4B5563` |

### Icons

**Hugeicons** (free, MIT) — a single-weight **stroke** set (1.5, round caps/joins), vendored as
Compose `ImageVector`s in `ui/icons/HugeIcons.kt` (generated by `tools/gen-hugeicons.mjs` from the
design skill's Hugeicons data; re-run it, don't hand-edit). Use `HugeIcons.*` like the old
`Icons.*` — `Icon(tint = …)` recolors the stroke. There is no filled variant, so **active state
reads via color + the selected pill indicator** (bottom nav active tab = green), not a filled glyph.
Directional icons (`ArrowBack`, chevrons, `List`, `Message`) set `autoMirror` so they flip in RTL.
Category icons sit on a tinted rounded-square tile in the category color (the notification large-icon
rasterizer in `CategoryGlyphIcon.kt` strokes the same vector).

### Voice & Copy

- Address the user as **you**. **Sentence case** for buttons/labels; Title Case only for
  proper screen names ("SMS Inbox"). No emoji.
- Money in the UI uses the **dirham glyph**, never the literal text "AED". Use the shared
  components — `DirhamGlyph`, `AmountText`, `MoneyText` (shared
  `ui/components/HisabakComponents.kt`, glyph = `shared/src/commonMain/composeResources/drawable/
  ic_dirham.xml`) — which render the glyph + Geist Mono tabular figures.
  Never hardcode `"AED …"` in a `Text`. (The only exception is simulated bank-SMS sample
  text, which genuinely contains "AED".) Amounts are shown **compactly** — thousands as `K`,
  millions as `M`, both to 2 decimals, under 1,000 exact (the shared `compactAmount` /
  `compactAmountMinor`, applied by `MoneyText`/`AmountText`); only the transaction edit input
  stays exact. Income shows `+`, expenses the true minus `−` (U+2212), both colored. Hero
  balances drop the sign and use neutral text.
- Every list screen needs a real empty state: icon + "No … yet" + one-line guidance + a CTA.

---

## Shared Components (`ui/components/`)

Reuse existing Composables; extend them to match the spec rather than forking.
Match prototypes in `.claude/skills/hisabak-design/components/` and `ui_kits/mobile/`.
Each component has a `.prompt.md` (what/when + usage) and `.d.ts` (props) — read those first.

`HisabakTopBar`, `HisabakBottomNav`, `CreateActionButton`, `PrimaryPillButton`,
`SurfaceCard`, `IconTile`, `CircleIconTile`, `ListRow`, `ListRowContent`, `StatCard`, `SearchField`,
`SectionHeader`, `FilterChipRow`, `GradientBanner`, `DarkPromoBanner`, `MostUsedCard`,
`EmptyStatePanel`, `ProgressBar`, `AreaLineChart`, `BarSparkline`, `DonutChart`

---

## Key Conventions

- No comments unless the WHY is non-obvious
- No error handling for impossible cases — trust domain guarantees
- No premature abstractions — add only what the current task requires
- Validate only at system boundaries (user input, external SMS)
- `rememberSaveable` keeps tab nav state alive across tab switches
- Don't invent new screens/flows from scratch — match existing Hisabak designs;
  if a design doesn't exist yet, leave a `// TODO: design` note rather than guessing

### UI Don'ts

- No green backgrounds, no decorative gradients, no glassmorphism, no emoji,
  no colored left-border accent stripes

---

## Testing

JVM unit tests guard the domain logic and ViewModels. Full guide: `docs/testing.md`.

- Run `./gradlew unitTests`. **Keep the suite green before finishing any change.**
  A Stop hook (`.claude/settings.json` → `.claude/hooks/run-tests.sh`) runs this
  automatically whenever Kotlin files changed and blocks on failure.
- **New feature → new tests.** When you add or change a use case, repository, ViewModel,
  or any business logic, add or update its test **in the same change**.
- Tests live in `shared/src/commonTest/…` (domain logic, **kotlin-test**) and
  `androidApp/src/test/…` (ViewModels + JVM-bound tests, **JUnit4**), each mirroring its
  main source set. Reuse the harness in the `:testutil` module (`com.hisabak.testutil`:
  `TestClock`, `Fake*` repositories, `TestData`; `MainDispatcherRule` stays in
  `androidApp/src/test`) rather than a mocking framework; build the real use case around
  a fake repo.
- Currently out of scope (no tests required): Compose UI, Room DAOs, navigation.

---

## Automated feature pipeline

Use the **`/feature`** skill (`.claude/skills/feature/SKILL.md`) to take a high-level
requirement to a reviewed PR: `/feature "<requirement>"`. It runs spec → design → branch →
code+tests → QA → docs → PR autonomously, then **enables auto-merge** (`gh pr merge --auto`)
so the PR lands on `develop` once CI is green — no manual gate. Per-feature
spec+design land in `docs/features/<slug>.md`; user-visible changes update `CHANGELOG.md`.

**Workflow vocabulary — one phrase per action, never conflate them:**

| Phrase | Action | Touches `main`? |
|--------|--------|-----------------|
| _(none needed)_ | every feature PR into `develop` auto-**squash**-merges on green CI (`gh pr merge --auto --squash`) — don't wait for a "merge it". (Release `develop`→`main` PRs use a **merge commit**.) | no — routine |
| **merge it** | merge a still-open PR **now** (e.g. the `develop`→`main` release PR, which stays manually gated) | depends |
| **send to testers** | distribute a staging build (Firebase) | no |
| **ship it** / **release** | cut a production release: bump version → `develop`→`main` → tag → Play | **yes — deliberate; confirm the version first** |

### Pre-PR consistency check (every PR, not just `/feature`)

After code review and **before opening any PR**, confirm the diff didn't leave docs/skills
stale, and update whatever it touched **in the same PR**: `CLAUDE.md` (stack/architecture/
commands), `README.md` (build/run/features/badges), `docs/` (`testing.md`, `cd.md`),
`.claude/skills/` (`git-workflow`, `feature`, `hisabak-design/compose-bridge.md`, `verifier-android`),
`.claude/hooks/run-tests.sh`, `.github/workflows/*.yml`, and `CHANGELOG.md`. Common triggers:
renamed Gradle tasks/variants, changed `applicationId`/package, changed test/build/run
commands, storage/architecture changes, design token/component changes, new dependencies,
user-visible behavior. A `gh pr create` hook re-surfaces this checklist automatically.

---

## Active Design Work

A full redesign is in progress. See `DESIGN_BRIEF.md` for the complete product and
visual design specification to hand to a designer.

Current design issues being addressed:
- Too many competing visual weights — no clear hierarchy
- Green overused (loses semantic meaning)
- Charts cramped inside small cards
- Primary action (Add Transaction) not prominent enough
- Empty states lack guidance
