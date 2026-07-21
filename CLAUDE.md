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
- **DI:** Koin
- **Async:** Kotlin Coroutines + Flow
- **State:** ViewModel + `collectAsStateWithLifecycle`
- **Charts:** Vico
- **Crash reporting:** Firebase Crashlytics. Wired via the `google-services` + `firebase-crashlytics`
  Gradle plugins (config in `app/google-services.json`, project `hisabak-finance-tracking`).
  Collection is gated on `!BuildConfig.DEBUG` in `HisabakApp` — **on in release, off in debug** —
  so local runs never reach the dashboard. Reports carry no financial/personal data; the
  privacy policy (`docs/privacy.html`) discloses it.
- **Analytics:** Firebase Analytics (no extra plugin — uses the same `google-services` setup),
  collection gated the same way (`!BuildConfig.DEBUG`, on in release). Behind a small domain
  abstraction: the `Analytics` interface + the `AnalyticsEvent` catalogue in
  `core/domain/analytics/` (the single place event names/params live), with
  `FirebaseAnalyticsClient` in `core/data/analytics/`, registered via `di/AnalyticsModule.kt`.
  Inject `Analytics` into a ViewModel/use case and log on the success path; tests use
  `testutil/FakeAnalytics`. **Strict no-PII:** events only carry booleans, enums, and coarse amount
  buckets — never raw amounts, names, notes, or SMS text. Screen views are reported manually from
  `MainActivity` (single-Activity Compose app, so Firebase's auto screen tracking doesn't fire).
- **Storage:** Room (SQLite) — `Room*Repository` impls per feature's `data/`, entities/DAOs/
  mappers in `data/local/`, and the database in `core/data/local/` (`HisabakDatabase`).
  The Room schema is exported to `app/schemas/` (committed); bump the DB version and add a
  real `Migration` for any entity change — **release builds don't destructively fall back**
  (debug builds do, for fast iteration).
- **At-rest protection:** the database is plain (unencrypted) SQLite, relying on Android's
  file-based encryption of app-private storage; the live-device threat is App Lock's job. The
  app-level SQLCipher layer that versions ≤1.8.x used was **removed** (its Keystore-wrapped key was
  never auth-gated, so it added little over FBE). Databases carried over from those versions are
  decrypted once, in place, before Room opens (`DatabaseDecryptionMigration`,
  `core/data/local/security/` — keys off the `SQLite format 3` header, unlocks with the legacy
  passphrase from `KeystoreDatabaseKeyStore`, runs `sqlcipher_export`, verifies + atomically swaps,
  then clears the stored key; idempotent, retries on crash; loads the SQLCipher native lib only when
  an encrypted file is actually found). The `sqlcipher-android` dependency is retained **only** for
  this migration — drop it, the migration, and the key store once the ≤1.8.x upgrade window closes. Lightweight app prefs (the onboarding flag, the
  theme mode, and the `appLockEnabled` flag) use DataStore (`core/data/preferences/`) behind the
  `AppPreferences` interface (`core/domain/`); `MainActivity` reads `themeMode` and feeds the
  resolved boolean to `HisabakTheme(darkTheme=…)`.
- **Localization:** English + Arabic (RTL). User-facing strings live in `res/values/strings.xml`
  (+ `res/values-ar/`) and are read via `stringResource`/`pluralStringResource` — **don't hardcode
  UI text**. The in-app language switch is framework-only (no appcompat, so Navigation 3's
  `NavDisplay` keeps its `ComponentActivity` dispatcher owner): the chosen tag is stored by
  `AppLocale` (`core/data/preferences/`) in a synchronous SharedPreferences, `MainActivity`
  overrides `attachBaseContext` to wrap the Context in that locale + layout direction, and the
  Settings screen saves the tag then calls `recreate()`. **Numbers follow the language:** English
  uses Western digits (pinned to `Locale.US`), Arabic uses **Arabic-Indic** digits — the wrapped
  locale carries `nu-arab` so resource-formatted numbers (percentages, dates, counts via `%d`)
  render Arabic-Indic config-driven, amounts pin `ar-u-nu-arab` in `compactAmountParts`, and the
  few fixed-format numbers use `localizeDigits(text, arabic)`. Amounts always read LTR
  (glyph · number · K/M-or-أ/م suffix) via a forced `LayoutDirection.Ltr`, with the number and
  suffix as separate `Text`s so Arabic-Indic digits don't bidi-reorder. Amounts keep the dirham
  glyph in both languages.
- **Platform:** Android only, portrait, edge-to-edge. `minSdk 29`. **Core-library desugaring is
  enabled** (`isCoreLibraryDesugaringEnabled` + `desugar_jdk_libs`), so `java.time` is safe to use
  freely down to API 29 — without it, API-34+ additions like `LocalDate.ofInstant` throw
  `NoSuchMethodError` on older devices at runtime (this caused the v1.5.0 launch crash).
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
    `withTransaction`), `JsonBackupCodec` (kotlinx.serialization), `AesGcmBackupCrypto`
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
- **CMP-bound:** the app is planned to migrate to **Compose Multiplatform**. Keep platform APIs
  (`Context`, `FragmentActivity`, `BiometricPrompt`, Keystore) out of domain/shared code, keep
  state/business logic as pure Kotlin, and keep Composables on multiplatform-safe APIs.

---

## Project Structure

```
com.hisabak
├── core/common/              shared value objects (Money, SyncMetadata, IDs)
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

**Jetpack Navigation 3** (`com.hisabak.nav`). 5-tab bottom navigation, each tab a
top-level destination with its own back stack. State is retained per tab when
switching; the user always exits the app through the **Dashboard** (home) tab.

- `NavKeys.kt` — destination keys: `DashboardKey`, `TransactionsKey`, `SmsKey`,
  `ManageKey`, `SettingsKey` (top-level) + `TransactionEditKey/BrandEditKey/CategoryEditKey(id)` (children).
- `NavigationState.kt` — `NavigationState` (one back stack per tab), `Navigator`
  (`navigate`/`goBack`: back from a tab → home; back from home → exit), and `toEntries()`
  which wires the saveable-state + **ViewModel-store** entry decorators. The ViewModel
  decorator scopes each screen's ViewModel to its `NavEntry`, so a popped destination's
  ViewModel is cleared — re-entering a screen starts fresh (no stale state).
- `BottomSheetScene.kt` — `BottomSheetSceneStrategy`; the transaction add/edit destination
  is marked with `bottomSheet()` metadata so it renders as a modal bottom sheet overlay.
- `MainActivity.kt` — `Scaffold` + `NavDisplay`. Top bar / bottom nav / FAB are derived
  from the current destination.

| Tab | Top-level key | Internal screens |
|-----|---------------|-----------------|
| Dashboard | DashboardKey | Single screen |
| Transactions | TransactionsKey | List → Edit (bottom sheet) |
| SMS | SmsKey | Single screen |
| Manage | ManageKey | Brands/Categories list → Edit (full screen) |
| Settings | SettingsKey | Theme + language + app lock → Backup & restore (full screen) |

Pattern: `List` → tap row or FAB → push `Edit(id?)` destination → Save/Cancel calls
`navigator.goBack()` → back to `List`.

---

## Domain Models

### Transaction
- `id`, `amount: Money`, `brandId`, `note?`, `occurredAt: Instant`, `sourceSmsId?`, `sync`

### Brand
- `id`, `name`, `categoryId?`, `sync`

### Category
- `id`, `name`, `type: CategoryType`, `color: String`, `icon: String`, `sync`
- `CategoryType`: INCOME | EXPENSES | SAVINGS | INVESTMENT
- `color` options: green, blue, orange, red, teal, purple, pink, gray
- `icon` options: wallet, cart, briefcase, car, utensils, piggy-bank, home, film, book, heart, gift, plane

### Budget
- `id`, `name`, `amount: Money`, `startAt`, `endAt?`, `saving`, `period`, `reoccurrence`, `categoryIds`, `sync`
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
already translated into `ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Motion.kt`,
`HisabakTheme.kt`) and shared components in `ui/components/`. For production UI, use those
directly; see the design skill's `compose-bridge.md` for the token/component → Kotlin map.
Use the HTML/CSS kit only for throwaway visual mockups.

### Foundations

- **Theme:** wrap content in `HisabakTheme { }`. Light **and** dark are first-class — every
  screen must look right in both. The active mode follows the user's Settings choice
  (Light/Dark/System, persisted in DataStore), not just `isSystemInDarkTheme()`. Never hardcode
  hex; use `MaterialTheme.colorScheme.*`, `HisabakTheme.colors.*`, `HisabakType.*`, `Spacing.*`,
  `Sizing.*`.
- **Type:** DM Sans for UI; **Geist Mono with tabular figures for every amount**
  (`HisabakType.amount` / `amountLarge` / `amountHero`). Amounts align in columns —
  don't use the sans font for money. **Arabic uses Tajawal** for both UI and amounts (one
  cohesive face — Geist Mono has no Arabic-Indic glyphs); the typography is built via the shared
  `hisabakTypography(family, arabic)` builder in `Type.kt`, and `AmountText`/`MoneyText` swap the
  figure family to Tajawal under Arabic. Fonts load via the Downloadable Fonts (Google Fonts) provider.
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
  components — `DirhamGlyph`, `AmountText`, `MoneyText` (`ui/components/HisabakComponents.kt`,
  glyph = `res/drawable/ic_dirham`) — which render the glyph + Geist Mono tabular figures.
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
- All entities carry `SyncMetadata` (prepared for future cloud sync)
- `rememberSaveable` keeps tab nav state alive across tab switches
- Don't invent new screens/flows from scratch — match existing Hisabak designs;
  if a design doesn't exist yet, leave a `// TODO: design` note rather than guessing

### UI Don'ts

- No green backgrounds, no decorative gradients, no glassmorphism, no emoji,
  no colored left-border accent stripes

---

## Testing

JVM unit tests guard the domain logic and ViewModels. Full guide: `docs/testing.md`.

- Run `./gradlew testProdDebugUnitTest`. **Keep the suite green before finishing any change.**
  A Stop hook (`.claude/settings.json` → `.claude/hooks/run-tests.sh`) runs this
  automatically whenever Kotlin files changed and blocks on failure.
- **New feature → new tests.** When you add or change a use case, repository, ViewModel,
  or any business logic, add or update its test **in the same change**.
- Tests live in `app/src/test/…` mirroring `main`. Reuse the harness in
  `com.hisabak.testutil` (`TestClock`, `MainDispatcherRule`, `Fake*` repositories,
  `TestData`) rather than a mocking framework; build the real use case around a fake repo.
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
