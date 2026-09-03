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
  Swift** (`firebase-ios-sdk` via SPM, configured in `iOSApp.init` with **per-flavor plists** —
  `GoogleService-Info-Prod.plist` / `-Staging.plist`, picked by the `HisabakFlavor` Info.plist
  key via `FirebaseOptions(contentsOfFile:)` so staging crashes attribute to
  `com.hisabak.staging`; the dSYM upload build phase picks the same plist by `CONFIGURATION`
  and passes `-gsp`. Guarded on plist presence, same debug/release gating; no event analytics —
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
  are the notification strings read via `Context.getString` outside Compose
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
  compiled snapshot refreshed from `SmsTemplateRepository` on `APPLICATION_SCOPE`
  (`SmsTransactionProcessor` first `awaitReady()`s the initial DB load, so cold-start captures
  like the iOS Shortcut intent see user templates, not just the shipped defaults); matching is
  **specificity-ranked** (`rankTemplates`: most literal anchor first, user templates over
  defaults on ties) and a match whose `{amount}` capture isn't a positive number falls through.
  Users create templates **by example** (Settings → SMS parsing, or "Create template" on an
  unparsed inbox message): tag amount/brand/date/skip spans on a sample; the pure derivation /
  reconstruction / suggestion logic is `feature/sms/domain/template/TemplateSample.kt`
  (user templates store their sample so editing re-tags via `reconstructSpans`, which
  `matchEntire`s). Save enforces a 10-char literal anchor minimum + a numeric amount tag, and
  previews how many stored messages the draft also matches. Templates ride in the backup
  envelope (schema v6). Templates are also **learned from AI parses** — see the AI section
  below; those carry `derivedByAi` and show a "Learned" badge in Settings.
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
  manual-only — every AI affordance is confirm-first: parse suggestions live in the inbox, and
  the **brand editor suggests a category** (`AiCategorySuggester` port + pure
  `sanitizeCategorySuggestion` + `SuggestBrandCategoryUseCase` in `feature/brand/domain/ai/`;
  debounced on name-settle, rendered as a tappable chip — an existing category selects on tap,
  a proposed new one opens the category editor prefilled via `CategoryEditKey` prefill fields +
  `forPick`/`CategoryCreatedBus`). Parsing is **brand-aware**: the
  top-50 most-used brand names go into the prompt, and `canonicalize` (exact/substring/
  Levenshtein≤2) snaps the model's merchant string to an existing brand deterministically. Android:
  `GeminiNanoSmsParser` + `GeminiNanoCategorySuggester` over the ML Kit GenAI **Prompt API**
  (`com.google.mlkit:genai-prompt`,
  beta — OS-managed Gemini Nano via AICore, flagship devices only). iOS: `AiSmsBridge` /
  `AiCategoryBridge` seams →
  `FoundationModelsSmsParser.swift` / `FoundationModelsCategorySuggester.swift` (Apple
  Foundation Models, iOS 26+, `@Generable`; injected
  via `startIosApp(gcmCipher, aiSmsBridge, aiCategoryBridge)` like the CryptoKit bridge).
  Unsupported devices
  report `Unavailable` and every AI affordance stays hidden. Inference is fully on-device —
  SMS text never leaves the phone; analytics events (`ai_parse_*`, `ai_category_*`) stay PII-free.
- **One brand resolver, and learned aliases:** every path that turns machine-extracted merchant
  text into a brand goes through `ResolveBrandUseCase` (`feature/brand/domain/usecase/`) —
  learned alias first, then `canonicalizeBrand` (`feature/brand/domain/BrandNameMatching.kt`;
  exact → containment either way → Levenshtein ≤2) over the **usage-ordered** names, then
  `findByExactName`. `FindOrCreateBrandUseCase` is `resolve(…) ?: create`, and
  `AutoConfirmSuggestionUseCase`'s "brand already exists" gate calls the same resolver, so the gate
  cannot disagree with the write it guards. The ladder is **deliberately not** applied to
  user-typed names (at distance 2 "Noon" and "Moon" are one brand — fine for a bank string, wrong
  for something a user typed), which is safe because the brand editor doesn't use
  `FindOrCreateBrandUseCase`. Suggest time and link time used to differ — link time had
  containment only, through an **unordered** `LIKE` — which is what let one merchant end up with
  two brands. **Aliases** (`brand_aliases`, CASCADE, lowercased key) close the rest of the gap:
  `LearnBrandAliasUseCase` records the raw merchant string → brand pair on confirm whenever the
  raw text doesn't already resolve to that brand, since a synthesized template captures the
  merchant **as written** while the transaction points at the canonical brand
  ("GOOGLEYOUTUBE,US" vs "Youtube video" share no containment, so every later message minted
  another brand — silently, on the background path, with no category). The raw string rides on
  `SmsMessage.suggestedBrandRaw` from suggest time for the same reason `suggestedPattern` does:
  after `sanitize` canonicalizes, it is gone. Aliases **do** ride in the backup envelope (unlike
  the AI provenance flags): they are knowledge other devices need. `SCHEMA_VERSION` 8→9, additive
  auto-migration.
- **Insights review (deterministic, layer 1 of `docs/features/ai-insights.md`):** the dashboard's
  Summary tab shows a **Review** card — top three findings for the selected period, **See all** →
  `InsightsKey(period)` full-screen list, tap → the transaction list filtered by category (or the
  uncategorized filter) via `TransactionListFilterBus`. Everything is pure and on-device:
  `InsightsSummary.from(DashboardSnapshot, period)` (`feature/insights/domain/`) selects what the
  rules need from the snapshot the dashboard already computed — so the review can never disagree
  with the numbers above it — and `deriveInsights(summary)` applies six rules (over/near limit,
  spend up/down/new vs prior, largest category, savings rate, uncategorized), each degrading to
  *absent* when its inputs are missing. Thresholds are constants in `DeriveInsights.kt`
  (`NEAR_LIMIT_RATIO` 0.8, `CHANGE_THRESHOLD_PCT` 25, `MATERIAL_SHARE` 0.05). `Insight` carries
  figures, never copy — `InsightRow` renders text from `type` + fields via `insights_*` strings, so
  the rules test without resources and Arabic gets the same treatment. `InsightsSummary` is also
  the exact payload the opt-in AI layers will send later: its shape *is* the privacy boundary (no
  rows, no notes). Both the dashboard and `InsightsViewModel` recompute it — one pure pass beats a
  bus or a fat nav key.
- **Learn-once template synthesis:** a confirmed AI parse also **teaches the regex engine**, so
  the next message of that bank format parses offline on any device — including the majority
  that have no on-device model at all. No extra model call: `deriveAiSpans`
  (`feature/sms/domain/template/AiTemplateSynthesis.kt`, pure) locates the amount and brand the
  model returned back in the body and lets `suggestSpans` handle the rest (dates, times, and the
  other number tokens, which must become `{ignore}` or the pattern would be pinned to one
  message). The remote parser also returns **verbatim evidence** (`brandText`/`amountText`: the
  substrings exactly as written), which `deriveAiSpans` prefers over inference and verifies against
  the body — `brandName` may have been snapped to an existing brand, so locating *it* matches only
  a prefix and leaves a branch code ("TALABAT-DXB-991" → "-DXB-") literal in the rule. On-device
  engines report no evidence and fall back to value-matching. There is deliberately **no confidence
  score**: model self-assessment is poorly calibrated, a substring claim is checkable.
  The candidate rides on `SmsMessage.suggestedPattern` from suggest time — the *raw*
  merchant string is only available before `canonicalizeBrand` — and `SynthesizeTemplateUseCase`
  installs it on confirm behind the same gate hand-made templates pass
  (`SaveSmsTemplateUseCase.validate`, plus dedupe and a `PreviewSmsTemplateUseCase` conflict
  check). Declining is a success with a null value: a rule that can't be trusted is simply not
  installed, and the user's transaction is never at risk. **Pasted text is included** — notes and
  bank SMS share one input field, and on iOS (no SMS API) pasting is how bank messages arrive, so
  the gate rather than the source tells them apart: a note leaves too little literal anchor to
  pass. The inbox snackbar offers **Undo**; `sms_template_synthesis_skipped` reasons say whether
  the local locator is the bottleneck.
- **Server-side parsing (opt-in, reaches every device):** on-device models cover a small minority
  of phones — Gemini Nano needs AICore (flagship silicon) and Apple Foundation Models an
  iPhone 15 Pro+ — so `RemoteAiSmsParser` (`feature/sms/domain/ai/`) implements the same
  `AiSmsParser` port against a small self-hosted service (`server/`, FastAPI + Docker; Claude
  Haiku 4.5 by default behind a `ParseProvider` protocol, so a self-hosted model is a swap with no
  client change). `PreferredAiSmsParser` composes the two: **the service leads when the user has enabled it** —
  turning that switch on is a preference, not merely consent, and the on-device models are
  materially weaker; preferring the local model made the setting close to a no-op on the devices
  that have one. Nothing is transmitted until the opt-in, and the fallback runs both ways, so a
  phone with a local model still parses when the service is unreachable (offline, an outage, a
  spent daily budget). Transport
  follows the `BackupRemote` pattern rather than adding an HTTP dependency —
  `RemoteParseClient` in commonMain, `HttpRemoteParseClient` (androidApp, `HttpURLConnection`) and
  `IosRemoteParseClient` (iosMain, `NSURLSession`); every failure is `null`, so an outage degrades
  to the regex templates. **Strictly opt-in:** the `remoteParseEnabled` pref gates it, is re-checked
  per call so revocation is immediate, and the Settings row only appears when the build actually has
  a service configured (`AppConfig.parseServiceUrl`/`parseServiceToken` — Android BuildConfig from
  gradle properties, iOS Info.plist). The service never logs or stores message text, and
  `docs/privacy.html` discloses the whole flow. Cost stays bounded because template synthesis turns
  each parse into a regex, so it is called roughly once per bank *format*.
- **Auto-confirm (opt-in, off by default):** a suggestion may become a transaction without a tap
  when `shouldAutoConfirm` (`feature/sms/domain/ai/AutoConfirm.kt`, pure) allows it: the setting is
  on, the source is **not** a paste (the user is on the screen anyway), `suggestedPattern != null`
  (so `deriveAiSpans` located *both* the amount and the merchant in the message — nothing was
  invented), the brand already exists (never create one unattended), and the amount is under
  `AUTO_CONFIRM_CEILING_MINOR`. `AutoConfirmSuggestionUseCase` delegates to
  `ConfirmAiSuggestionUseCase` rather than writing its own path, so the transaction, message link,
  template synthesis, and limit re-check stay identical; `IngestSmsUseCase` calls it through a
  function seam on the background path only, and the result is announced via
  `TransactionRecordedNotifier`. **What the gate cannot catch is a misreading** — a message saying
  "USD 42.10 (AED 154.62)" was read as 42.10 with the evidence genuinely present, so verification
  passes; `server/evals` is how that error rate gets measured.
- **Who waits for the AI fallback:** `CaptureSource.awaitsAiFallback` is true wherever something
  reports the outcome the moment capture returns — a Shortcut result, a share-sheet toast. Those
  **await** the AI parse and auto-confirm before answering; detaching made them claim "saved for
  review" while the parse that would have said "recorded" was still running, and on iOS the app
  suspends when the Shortcut ends, so that work only resumed at the next launch (the user watched
  a background capture parse itself in front of them). Only `SMS_BROADCAST` keeps the detached
  fallback — nothing is waiting on it and the notification is the channel. `MANUAL_PASTE` returns
  early either way, since the inbox drives its own suggestion with a spinner.
- **Provenance on a linked row:** the "AI parsed" badge says the parse came from a model; the
  **status chip** says whether anyone checked it — `Linked` vs `Unreviewed` (amber `warning`, the
  one state that asks the user for something, so never green). `SmsMessage.autoConfirmed` carries
  it, written by `SmsTransactionProcessor.commit` in the same write; `SCHEMA_VERSION` 6→7,
  additive. The chip deliberately tracks **verified vs not**, not who tapped: a regex template
  match is unattended too, but it is deterministic and user-authored/vetted, so it stays `Linked`.
  `Unreviewed` is reserved for the one risky quadrant — a model inferred it and nobody checked —
  because the auto-confirm gate verifies *evidence*, not meaning, and a badge on the majority of
  rows (regex is the dominant path, and the only one on iOS) would flag nothing.
  **The tag is dischargeable**, or it would accumulate until it marked everything and signalled
  nothing: opening the row's transaction ("Review transaction") fires
  `MarkSmsReviewedUseCase` → `SmsMessage.reviewed` (a **separate** flag, `SCHEMA_VERSION` 7→8
  additive auto-migration — `autoConfirmed` stays true forever, since who created the row is
  history and whether anyone checked it is not). The chip reads
  `autoConfirmed && !reviewed`. Marking happens **on the way in, not on return**: seeing the
  amount and brand *is* the review, and there is no nav result to wait for. Clearing on *save*
  was rejected — a correct auto-confirm needs no edit, so the common case would back out unsaved
  and stay tagged forever. Neither flag rides in the backup envelope (`SmsMessageRecord` carries
  neither), so a restored row reads as `Linked`.
- **Inbox offers (discoverability):** both AI settings are also offered where they matter — a card
  above the paste box in the SMS inbox, since that is where an unrecognised message lands. Three
  answers, because two are not enough: **Turn on**, **Not now** (returns next launch — held in the
  ViewModel, deliberately not persisted), and **Don't ask again** (persisted in
  `suppressedInboxPrompts`; Settings keeps the switch). `choosePrompt`
  (`feature/sms/presentation/inbox/InboxPromptPolicy.kt`, pure) shows **at most one** — online
  parsing first, auto-confirm after it is on — and offers neither where it would be a promise the
  build can't keep (no parse service configured) or pointless (no engine to produce a suggestion).
  `inbox_prompt_accepted` / `inbox_prompt_suppressed` say whether the offer is welcome or noise.
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
    the envelope and gated on import. The Drive file name is **flavor-scoped**
    (`backupFileName(flavor)` off `AppConfig.flavor`: prod keeps `hisabak-backup.bak`, staging
    writes `hisabak-backup-staging.bak`, and `findLatest` filters by name) — the App Data Folder
    is scoped to the GCP *project*, so every flavor/platform client shares one folder; the name is
    what keeps staging from clobbering prod while cross-platform restore within a flavor works.
  - **Auto-backup:** `AutoBackupScheduler` (domain interface + pure `autoBackupInterval(period)`) →
    `WorkManagerAutoBackupScheduler` enqueues unique periodic work that runs `BackupWorker`
    (CoroutineWorker resolving deps via Koin, default factory). Any network, silent; rescheduled from
    `BackupViewModel` (period/enable changes) and `HisabakApp` on launch. The passphrase is
    Keystore-stored (non-auth-gated) so encrypted auto-backups run unattended. Passkeys were rejected
    (need WebAuthn PRF + a server). Background schedulers are best-effort (iOS's BGAppRefreshTask
    especially), so the reliability net is the **foreground catch-up**: `CatchUpAutoBackupUseCase`
    (single, shared in-flight guard) runs an overdue backup when `now − lastBackupAt ≥ period` —
    called from `HisabakApp.onCreate`, `startIosApp`, iOS scene-active (`onIosAppForeground`),
    and the iOS BG task. `RunBackupUseCase` stamps the `lastBackupAt` pref on every successful
    upload (manual or scheduled).
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
| Dashboard | DashboardKey | Summary/Trends/Categories tabs → Insights (full screen, from the Review card) |
| Transactions | TransactionsKey | List → Edit (bottom sheet; the "New brand" chip and the uncategorized-brand note detour to the brand editor — the sheet closes/reopens around it with its typed input parked in `TransactionDraftBus`, and a created brand auto-selects via `BrandCreatedBus`) |
| SMS | SmsKey | Inbox → template editor (full screen) / transaction sheet (review of an AI-parsed entry) |
| Manage | ManageKey | Brands/Categories list → Edit (full screen; the brand editor's "+ New category" chip pushes the category editor and auto-selects the result via `CategoryCreatedBus`) |
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
- `color` options: the 8 named palette keys (green, blue, orange, red, teal, purple, pink, gray)
  **or a custom hue** — `h<0-359>`, e.g. `h210`. See Color below; parsing lives in
  `CategoryColor` (domain), so validation never needs presentation.
- `icon` options: 144 keys across 11 groups — see `CategoryVocabulary.icons` (generated from
  `tools/category-icons.mjs`). Keys are persisted, so they are **append-only**: never rename or
  remove one, or existing rows and restored backups lose their glyph.

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

**Custom hues.** A category may also store `h<0-359>` instead of a palette key — the user picks
a hue and **the app derives the shades**. Only the hue is persisted: a hex would have one value,
and every surface needs a different one per theme. The derivation is `ui/theme/HueColor.kt`
(pure, commonMain) in **OKLCH, not HSL** — HSL lightness isn't perceptual, so a fixed-lightness
rule would make yellow glare and blue vanish; out-of-gamut results drop chroma rather than clip
channels, which would shift the chosen hue. Foreground L is 0.55 light / 0.78 dark at C 0.15;
tiles use that at 15% alpha, and `hueTintOpaque` gives the opaque version notification tiles need.
`HueColorTest` asserts ≥3:1 contrast on both surfaces for every hue — **keep it green if you
retune the constants**. Resolution runs through `tintPairForColor` / `CategoryStyle.color`
(both read `HisabakTheme.isDark`) and `CategoryGlyphIcon.notificationTileColors` on Android.
The AI suggester stays on **named keys only** (`CategoryVocabulary.colors`).

New categories default to `CategoryColor.mostDistinctHue(...)` — the hue in the widest gap
between those already used — because the dashboard donut colors its slices by category color,
so two categories on one color become two indistinguishable slices. The picker also warns when
a hue lands within `COLLISION_DEGREES` of an existing category.

### Icons

**Hugeicons** (free, MIT) — a single-weight **stroke** set (1.5, round caps/joins), vendored as
Compose `ImageVector`s in `ui/icons/HugeIcons.kt` (generated by `tools/gen-hugeicons.mjs` from the
design skill's Hugeicons data; re-run it, don't hand-edit). Use `HugeIcons.*` like the old
`Icons.*` — `Icon(tint = …)` recolors the stroke. There is no filled variant, so **active state
reads via color + the selected pill indicator** (bottom nav active tab = green), not a filled glyph.
Directional icons (`ArrowBack`, chevrons, `List`, `Message`) set `autoMirror` so they flip in RTL.
Category icons sit on a tinted rounded-square tile in the category color (the notification large-icon
rasterizer in `CategoryGlyphIcon.kt` strokes the same vector).

**Category icon catalogue.** The 144 pickable category icons are curated in
`tools/category-icons.mjs` — key, group, glyph source, and English + Arabic search keywords —
and the generator emits three files from it: the vectors into `HugeIcons.kt`, the picker data
into `ui/icons/CategoryIconCatalog.kt`, and the key list into
`feature/category/domain/CategoryIconKeys.kt` (which `CategoryVocabulary.icons` re-exports, so
domain validation never reaches into presentation). To add an icon, add a row there and re-run
`node tools/gen-hugeicons.mjs` — never hand-edit the generated files. `iconForKey` resolves a
stored key through the catalogue and falls back to a plain circle for unknown keys, which is what
a backup from a newer build restores as. Search is the pure `searchCategoryIcons` (Arabic is
normalized — hamza forms, taa marbuta, alef maqsura, diacritics — so unpointed typing matches).

**The AI proposes a name and type; the app decides how it looks.** `sanitizeCategorySuggestion`
derives the **icon** from the suggested name (`iconForCategoryName`, matching the catalogue's own
keywords) and the **colour** from `CategoryColor.mostDistinctHue` over the categories already in
use — the model's colour was removed from both prompts entirely, since distinctness is something
only the app can know. `CategoryVocabulary.aiIcons` holds only the original 12, because the
on-device prompts inline that list (`GeminiNanoCategorySuggester.kt`,
`FoundationModelsCategorySuggester.swift`) and a 144-way choice both bloats the prompt and costs
accuracy — it is a fallback for when the name matches nothing. Suggestions are still *validated*
against the full set.

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
  stays exact. Anything actually abbreviated is **tappable: it swaps to the full figure in
  place** for a few seconds, shrinking to fit its container rather than clipping
  (`rememberRevealableAmount` + `exactAmount`; `LocalRevealedAmount`, provided by
  `HisabakTheme`, keeps only one expanded at a time), and screen readers always hear the exact
  figure. Amounts that lost nothing are inert — no affordance without payoff.
  Income shows `+`, expenses the true minus `−` (U+2212), both colored. Hero balances drop the
  sign and use neutral text.
- Every list screen needs a real empty state: icon + "No … yet" + one-line guidance + a CTA.

---

## Shared Components (`ui/components/`)

Reuse existing Composables; extend them to match the spec rather than forking.
Match prototypes in `.claude/skills/hisabak-design/components/` and `ui_kits/mobile/`.
Each component has a `.prompt.md` (what/when + usage) and `.d.ts` (props) — read those first.

`HisabakTopBar`, `HisabakBottomNav`, `CreateActionButton`, `PrimaryPillButton`,
`SurfaceCard`, `IconTile`, `CircleIconTile`, `ListRow`, `ListRowContent`, `StatCard`, `SearchField`,
`SectionHeader`, `FilterChipRow`, `ChipLaneGrid`, `GradientBanner`, `DarkPromoBanner`, `MostUsedCard`,
`EmptyStatePanel`, `NoticeCard`, `ProgressBar`, `AreaLineChart`, `BarSparkline`, `DonutChart`

Long chip rows (brands, categories) use **`ChipLaneGrid`**, not a bare `LazyRow`: it wraps into
`chipLaneCount` lanes — one row up to 4 chips, two to 8, three beyond — and still scrolls
sideways. It sizes its band from the type scale, so it grows with the user's font setting.

A category dot is a **glyph** wherever the colour identifies rather than references: chips, filter
rows, and the Manage/Categories cards all show `iconForKey`. The dashboard's **donut legends keep
plain dots** — there the colour is a key matching a row to its arc, and a stroke glyph carries
less colour to match with.

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
