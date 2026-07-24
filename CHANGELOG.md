# Changelog

All notable changes to Hisabak are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **AI parsing for unrecognized bank messages** — when a bank SMS doesn't match any known
  format, the app's on-device AI (Gemini Nano on Android, Apple Intelligence on iOS) now
  reads it and proposes the transaction as a suggestion you confirm with one tap; a
  "Parse with AI" button also appears on unparsed messages. Each suggestion shows the
  brand, amount, and booking date before you confirm. Suggestions recognize your
  existing brands — typos, different casing, and abbreviations resolve to the brand you
  already track instead of creating a duplicate — and messages imported this way keep an
  "AI parsed" label so you can always tell them apart from template parses. Everything runs on your device —
  messages never leave your phone. Available on devices with on-device AI support;
  everywhere else the app behaves as before.
- **iOS: capture transactions through the Shortcuts app** — Hisabak now provides a
  "Capture transaction" action in the Shortcuts app. Point a personal automation
  ("When I get a message" → Run immediately) at it to record bank SMS hands-free — the
  closest iOS gets to Android's SMS auto-capture, without the app ever reading your
  messages.
- **Reconnect Google Drive from the Backup screen** — if backup is on but no Google account is
  connected (declined, failed, or revoked access), a "Connect Google account" button now appears;
  previously the only way back was turning backup off and on again.

### Changed
- **Bundled fonts** — DM Sans, Geist Mono and Tajawal now ship inside the app instead of being
  downloaded through Google Play services, so the app's typography renders correctly offline, on
  first launch, and on devices without Play services.

## [1.9.0] — 2026-07-21

### Changed
- **Simpler on-device storage** — the app-level SQLCipher database encryption has been removed;
  your data stays in app-private storage protected by Android's built-in file-based encryption
  (and by App Lock, if you use it). The app-level layer added no real protection on top — its key
  was stored on the same device with nothing gating it — and removing it makes the app smaller and
  faster to start. Existing databases are converted automatically on first launch after updating —
  nothing for you to do, and no data is lost.

## [1.8.0] — 2026-06-25

### Added
- **Grouped amount entry** — the transaction amount field now shows thousands separators as you
  type (e.g. `1,250.00`), and its whole row is tappable to bring up the keyboard, not just the digits.

### Changed
- **Softer chart fill** — the dashboard area charts (net worth and the income/expense trends) now
  fade their fill with a vertical gradient under the line instead of a flat tint.
- **Arabic typeface** — the Arabic UI and amounts now use **Tajawal** (one cohesive, modern face)
  instead of the device's default Arabic font, pairing with DM Sans on the English side.
- **Donut charts** — category/brand/income donuts now have rounded segment ends with small gaps
  between them and animate in, for a more polished look.

### Fixed
- **Expense sign** — expense amounts in the Transactions list and SMS Inbox now show a minus (`−`)
  instead of a plus, matching the rest of the app.
- **Press feedback** — buttons, filter chips, and the segmented control no longer briefly lose their
  fill color when a press is released.
- **Light-theme status bar** — the status-bar icons now follow the app's selected theme, so they
  stay legible (dark icons on the light bar) when Light theme is used on a dark-set device.

## [1.7.0] — 2026-06-25

### Changed
- **Refreshed iconography** — the app now uses the Hugeicons stroke icon set throughout (navigation,
  actions, category tiles, notifications) for a lighter, more crafted look. The active bottom-nav tab
  reads through its colored pill indicator.
- **Redesigned Settings** — settings are now grouped into "Backup & security" and "Preferences",
  each option on its own card with a leading icon, for a clearer, calmer layout.
- **Totals in Manage** — each brand row and category tile now shows the total amount spent through
  it, so you can see at a glance where your money goes.
- **Day-grouped transactions** — the Transactions list now groups entries under day headings
  (Today, Yesterday, dates), each day in its own card, with the category shown beside each entry.
- **Clearer SMS auto-import banner** — the status banner on the SMS Inbox is now color-tinted with a
  leading icon (green when active, amber when off) so its state reads at a glance.
- **Income-ratio bar on Transactions** — the summary now shows a small bar with what share of the
  period's money flow was income, so you can read your income-vs-spending balance at a glance.
- **Notification icons by type** — budget-limit alerts now show an amber warning icon (instead of a
  generic bell), so you can tell the kind of notification at a glance.
- **Live SMS paste preview** — when you paste a bank message, the SMS Inbox now shows the brand and
  amount it parsed before you import, so you can confirm it caught the right details.
- **Top-bar logo** — the brand mark in the main screens' top bar is now a rounded square matching the
  app icon, instead of a circle.

### Security
- Hardened backup encryption: the backup file header (salt, KDF iterations, IV) is now
  cryptographically authenticated, so tampering with it is detected on restore. Existing
  encrypted backups still restore unchanged.
- The one-time plaintext-to-encrypted database migration now scrubs the old plaintext file
  before removing it, reducing the chance of recovering it from device storage.

## [1.6.0] — 2026-06-24

### Added
- **Database encryption at rest** — your on-device data is now always encrypted with SQLCipher
  (AES-256). The encryption key is generated on your device and protected by the Android Keystore,
  so your financial history can't be read off the device's storage. It's automatic — existing data
  is migrated to encrypted on first launch, with nothing to turn on and no passphrase to remember.
- **Backup passphrase reminder** — when your backups are encrypted, Settings periodically shows a
  gentle card asking if you still remember your passphrase. Confirm you remember it, or check it by
  entering it — so you're never locked out of your own backup.
- **Google Drive backup & restore** (Settings → Data) — connect a Google account and back up your
  data to your Drive (in a private, app-only space), optionally encrypted with a passphrase
  (AES-256-GCM; the passphrase is stored encrypted on-device and is the only key — keep it safe).
  After installing on a new device, a one-time page after onboarding offers to **restore** your data
  from Drive (skippable). Optional **automatic backups** run in the background on the frequency you
  pick (Never / Daily / Weekly / Monthly). (Backup requires the app's Google Drive access to be
  configured — see `docs/google-drive-backup-setup.md`.)
- **App lock** (Settings → Security) — require a fingerprint/face unlock, falling back to your
  device PIN/pattern/password, to open Hisabak. It locks on launch and when you return after
  leaving the app, with a short grace period so quick app-switches don't prompt you every time.
  This is an access gate, not at-rest encryption.
- A **Settings** tab with two controls to start:
  - **Theme** — choose Light, Dark, or System (follows your device). Your choice is saved and
    applies across the whole app instantly.
  - **Language** — switch the app between **English** and **العربية**. The app is fully
    localized and lays out right-to-left in Arabic; your choice is saved and survives restarts.

### Changed
- The top bar on the main screens now shows the Hisabak app logo instead of a generic
  profile icon, which had implied a user account the app doesn't have.

## [1.5.1] — 2026-06-19

### Fixed
- Fixed a crash on launch on Android 13 and older (API < 34). The app used a newer date API
  (`LocalDate.ofInstant`) that doesn't exist on those versions; enabling core-library desugaring
  backports it, so the app now starts reliably across all supported devices.

## [1.5.0] — 2026-06-18

### Added
- Anonymous crash reporting so crashes get found and fixed faster. It captures only technical crash
  details — never your transactions or any personal or financial data — and is off in development
  builds. See the privacy policy for details.
- Anonymous, aggregate usage analytics to learn which features help most and keep improving the app.
  It never includes your transactions, names, notes, SMS content, or exact amounts. See the privacy
  policy for details.

## [1.4.1] — 2026-06-18

### Fixed
- The home-screen app icon now matches the icon shown in the README and on the Play
  Store — an upward trend line ending in a mint data-point dot — instead of the older
  arrow variant.

## [1.4.0] — 2026-06-18

### Added
- A fresh install now starts with a small set of starter categories (Salary, Groceries,
  Dining, Transport, Shopping, Savings, Investments) so you can record a transaction right
  away. They're ordinary categories you can rename or delete.

### Fixed
- Capturing a transaction by sharing or selecting bank-SMS text now completes reliably even
  if the brief capture screen closes before the save finishes.
- Amount fields now accept a comma as the decimal separator, so entering cents works on
  keyboards that use `,` (e.g. many non-English locales).
- Uncategorized transactions (e.g. just captured from SMS) now show as neutral instead of
  appearing as green income, so they no longer look like money you received.
- Opening an uncategorized transaction to edit it now shows its brand and is titled "Edit
  transaction"; the transaction and brand edit screens no longer mislabel an edit as a new entry.

## [1.3.0] — 2026-06-18

### Added
- First-launch onboarding: an animated, premium walkthrough of the app's features
  (SMS auto-capture, on-device privacy, budgets & alerts, insights) ending with an
  SMS-permission primer.
- Transaction-recorded confirmation: when a bank SMS is captured and saved, a notification
  confirms it with the amount and brand. A categorized brand shows its category name and
  glyph and taps through to that category on the dashboard; an uncategorized brand says so
  and taps through to its editor so you can categorize it.
- Add a transaction by **sharing** a bank SMS into Hisabak, or by **selecting its text** and
  tapping Hisabak in the selection menu — both run the same parser as auto-capture. These need
  no permissions and work alongside the existing manual paste.

### Changed
- SMS **auto-capture** (reading bank texts in the background) now ships in the staging build
  only; the Play build is SMS-free to comply with Google Play's restricted-permission policy and
  captures transactions by sharing a bank SMS, selecting its text, or pasting it. Onboarding and
  the SMS tab adapt their copy and drop the SMS-permission prompt accordingly.
- Disabled Android auto/cloud backup so financial data truly never leaves the device,
  matching the privacy promise. Trade-off: no automatic restore on reinstall or device
  transfer.
- Release builds no longer wipe the database on an unexpected schema change — the Room
  schema is now exported and migrations are required, protecting on-device history. (Debug
  builds keep the destructive fallback for fast iteration.)
- Amounts are now shown compactly everywhere: thousands as `K`, millions as `M` (both to
  two decimals) — e.g. `AED 990,853` reads as `990.85K`. Applies to the dashboard, stat
  cards, transactions, charts, and budgets. The amount you type when editing stays exact.

### Fixed
- Amounts with certain cents (e.g. `19.99`) were stored a cent short due to floating-point
  truncation; they now round correctly. Affects manual entry, SMS-parsed amounts, and
  category limits.
- Budget alerts now fire reliably for transactions captured from SMS while the app is closed,
  instead of occasionally being skipped when the process was reclaimed in the background.
- Deleting a brand that can't be removed now shows a message instead of doing nothing
  silently.
- An auto-captured SMS with no date in its text is now dated when the message arrived instead
  of the moment it was processed.
- A redelivered bank SMS (same text and time) is no longer imported twice.
- Removed a non-functional "See all" link from the brands list header (it did nothing; the
  list already shows every brand).
- The SMS "auto-import is disabled" card no longer cramps its title (it was breaking
  "Auto-import" mid-word); the badge and Enable button now sit on their own row.

## [1.2.0]

### Added
- Separate `staging` and `production` build flavors (`com.hisabak.staging` /
  `com.hisabak`) so both can be installed side by side; staging is labelled "Hisabak STG".
- CI/CD: GitHub Actions run the unit suite on every PR, distribute staging builds to
  testers via Firebase App Distribution, and publish production releases to Google Play.
- JVM unit-test safeguard (77 tests) covering domain logic and ViewModels.

### Changed
- Production installs no longer seed demo data — they start empty (demo data is
  staging-only).

### Fixed
- SMS template parsing treated a template's trailing `.` as a regex wildcard, which
  dropped the imported transaction's time-of-day (it defaulted to midnight).

## [1.1.0]

### Added
- Premium UI polish and category-limit notifications.

## [1.0.0]

### Added
- Initial release: SMS-driven transaction capture, categorization, budgets, and
  financial trend visualizations.
