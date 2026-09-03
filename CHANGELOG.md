# Changelog

All notable changes to Hisabak are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **An AI explanation of the review, if you want one.** On the insights screen Hisabak can now
  explain what changed and suggest what to do — "Dining is over its limit by 300; a 1,600 cap would
  hold next month" — with a tap to set that limit, which opens the category editor prefilled so you
  still confirm it. It is **off by default** and has its own switch (Settings → Insights): turning
  it on sends only your totals by category for the period — never transactions, notes, brands, or
  messages — and **See what's shared** shows you the exact figures before anything is sent. The
  explanation is fetched once and kept until your numbers materially change, and if the service
  can't answer the review below is complete on its own.
- **A review on the dashboard.** Under the cash, savings and investment figures, Hisabak now points out what changed
  and what needs attention for the period you're viewing: a category over or near its limit, a
  category up or down sharply on last time, your largest expense, your savings rate and how it
  moved, and anything still uncategorized. It's all worked out on your phone from numbers the
  dashboard already has — nothing is sent anywhere. Tap **See all** for the full list, and tap a
  finding to jump to those transactions.

## [2.2.0] — 2026-09-03

### Added
- **Parsing that works on every phone** — on-device AI only exists on flagship Androids and
  iPhone 15 Pro or newer, so most phones never saw it. You can now point Hisabak at a small parsing
  service (self-hosted; see `server/`) and have unrecognised bank messages read by a proper model
  instead. It is **off by default** and lives in Settings → SMS parsing: only messages no template
  recognises are sent, only while the setting is on, and nothing is stored. Because Hisabak learns a
  template from each parse, the same bank format is only ever sent once.
- **A real icon library for categories** — 144 icons across 11 groups (food, transport,
  shopping, home and bills, health, travel, work, money, leisure, tech, other) replace the
  original 12. Tap the icon tile in the category editor to open a picker with search; it
  matches English and Arabic terms whichever language the app is in, and Arabic matches
  without hamza or diacritics, so "اكل" finds the restaurant icon. Naming a category picks its
  icon for you — the picker opens on matches for the name you typed, and an AI-suggested
  category arrives with a fitting glyph and a colour that doesn't clash with the ones you
  already use.
- **Any color you like for a category** — alongside the eight preset swatches there's now a
  custom hue picker. You choose the hue; the app works out the exact shades, so the color stays
  readable in light mode, dark mode, and on notification tiles. The picker previews both themes
  side by side, shows the colors your other categories already use, and tells you when your pick
  is too close to one of them — the dashboard donut colors its slices by category, so
  near-duplicates are genuinely hard to read. New categories now start on the color furthest
  from the ones you already have.

- **Bank messages no longer create duplicate shops.** When Hisabak learns a message format, it now
  also remembers that the merchant name your bank writes ("GOOGLEYOUTUBE,US") means the shop you
  picked ("Youtube video"). Before, the next message of that format quietly created a second shop
  with no category, which then sat outside your budgets and dashboard. Brand matching is also
  consistent everywhere now, and prefers your most-used shop when a merchant name could match
  several.
- **Confirm automatically (off by default)** — when a bank message arrives in the background and
  the amount and shop were both found in the message text, and the shop is one you already have,
  Hisabak can save the transaction and tell you rather than waiting for a tap. Pasted messages
  always wait for you, new shops are never created this way, and larger amounts still ask. Anything
  saved this way is tagged **Unreviewed** in the inbox until you look at it, so the handful of
  messages nobody checked are easy to find — messages matched by one of your templates stay
  **Linked**, since those are read by a rule you set up rather than guessed at. Tapping **Review
  transaction** on a tagged row clears the tag.
- **The SMS inbox now offers the AI settings where they help**, rather than leaving them buried in
  Settings: a card above the paste box explains what each does, with **Turn on**, **Not now**
  (asks again next time you open the app), and **Don't ask again** (never asks again; the switch
  stays in Settings). Only one is ever shown at a time.
- **The online model is now used whenever it is switched on**, including on phones that have their
  own built-in AI. Previously the built-in model answered first and the setting had little effect
  on those devices. Your phone still falls back to its own model when the service can't be reached.
- **Settings now has an SMS parsing section**, gathering the three things that decide how messages
  are read: your parsing templates, whether unrecognised messages may be sent to the online model,
  and whether a confirmed-looking result may be saved without asking you.
- **The app learns your bank's message format** — when you confirm an AI-parsed message, Hisabak
  saves the pattern behind it as a parsing rule. The next message from that bank in the same
  format is read instantly, with no AI involved, and it keeps working on devices that have no
  on-device AI at all. The rule appears in Settings → SMS parsing marked "Learned", where you can
  edit or delete it like any other, and the confirmation snackbar offers Undo straight away. A
  rule is only kept if it is specific enough to be safe and doesn't contradict messages you have
  already captured.
### Changed
- **The transactions summary now shows a savings rate** instead of an "income ratio". It reads
  what share of the period's income you kept — break-even is 0%, and overspending shows as a
  red bar rather than a shorter green one. The old measure put break-even at 50% and could
  never show a deficit as a loss. Periods with no income yet say so rather than reading 0%.
- **Long chip rows wrap instead of scrolling forever** — the brand picker in the transaction
  sheet, the category picker in the brand editor, and the category filter on the Brands
  screen now split into two rows past four chips and three past eight, still scrolling
  sideways. Short lists stay a single row.

### Fixed
- **The keyboard no longer closes the instant you tap a note.** Tapping the note field in the
  transaction sheet opened the keyboard and dismissed it again immediately. Tapping a field low
  enough on a screen to need scrolling into view made the app mistake its own scroll for you
  scrolling to read, and it hides the keyboard when you do that. It now only hides it when you
  actually drag.

## [2.1.0] — 2026-09-01

### Highlights
- Tap any shortened amount to read it in full, and create a brand or a category without
  leaving the screen you were on — with on-device AI suggesting the category for you.

### Added
- **Create a category without leaving the brand editor** — the category row in the brand
  screen ends with a "New category" chip that opens the category editor; saving returns
  you to the brand with the new category already selected and your typed name intact.
- **Create a brand from the transaction sheet** — the brand row ends with a "New brand"
  chip that opens the brand editor (with its category tools); saving returns to the sheet
  with the new brand selected and everything you'd typed — amount, note, date — restored.
  The "uncategorized brand" note is now tappable for new transactions too.
- **AI-suggested categories** — on devices with on-device AI, typing a brand name makes
  the brand editor suggest a category: tap "Suggested: Groceries" to pick it, or, when
  nothing fits, "New: Pharmacy" opens the category editor pre-filled with a sensible
  name, type, color, and icon. Suggestions are only ever applied when you tap them, and
  nothing leaves your phone.

### Changed
- **Tap an amount to see it exactly** — abbreviated figures (`⊅ 1.25K`, `⊅ 1.89M`) expand to
  the full amount in place for a few seconds, shrinking to fit rather than clipping, and
  collapse again on their own. Amounts that were never abbreviated stay inert. Screen readers
  now always hear the exact figure instead of the abbreviation.

## [2.0.0] — 2026-08-05

### Highlights
- Hisabak 2.0 — teach the app your bank's SMS format with parse templates you create by
  example, let on-device AI read unrecognized messages and plain-text notes (nothing is
  recorded without your confirmation), capture hands-free on iPhone through Shortcuts, track
  savings and investment withdrawals (including loans), delete transactions, and count on a
  Google Drive auto-backup that keeps itself up to date.

### Added
- **Teach the app your bank's SMS format** — Settings → SMS parsing lists the parse templates
  (the built-ins plus your own) and lets you create new ones *by example*: paste a bank message
  (or tap "Create template" on an unparsed message in the SMS inbox), confirm what the app
  highlighted as the amount, brand, date, and changing text like balances, and save. From then
  on, messages in that format parse instantly on any device — no AI needed. Safety rails keep a
  badly-made template from breaking anything: more specific templates always win, a template
  whose amount lands on non-numeric text is skipped, and before saving you're warned if a
  draft also matches lots of unrelated stored messages. Templates can be turned off (built-ins
  too), your own can be edited and deleted, and they're included in Drive backups. Saving a
  template made from an inbox message imports that message through it on the spot, and the
  "Create template" action stays available even after the AI has offered its own suggestion —
  the suggestion is one-shot, a template covers every future message.
- **One capture box for everything** — the SMS tab's paste box now takes bank messages *and*
  plain notes in your own words. A recognized bank format imports instantly, as before.
  Anything else — "lunch 45 yesterday", "100 at noon", or an unrecognized bank SMS — lands in
  the inbox and the on-device AI reads it while you watch, then offers the usual suggestion
  card to confirm or dismiss: nothing is ever created without your tap. Typed notes understand
  relative dates ("yesterday", "last friday") and shorthand like "15k", and pasted backlogs
  may be dated up to a year back. Runs fully on-device — your text never leaves the phone.
  Without AI support, unmatched text still lands in the inbox with a clear message and the
  "Create template" path. Messages imported via AI keep a "Review transaction" shortcut that
  opens the created entry for a quick human check.
- **Savings & investment withdrawals** — savings and investment transactions now have a
  direction: deposit or withdrawal. A withdrawal takes money back out of the bucket, so the
  dashboard Savings/Invest pills and your cash reflect it, and a brand's total shows the net
  balance. This also makes loan tracking work: put a person's brand in a savings category,
  record what you lent as deposits and repayments as withdrawals, and the brand shows what's
  still outstanding — a fully repaid loan reads 0 instead of disappearing. Savings and
  investment amounts in the transaction list now use their own colors (blue/purple) with a
  true +/− sign instead of rendering like income.
- **Delete a transaction** — added one by mistake? Open it from the Transactions tab and use
  "Delete transaction" at the bottom of the sheet; a confirmation step guards the tap. The
  amount comes straight back out of your totals and budgets. If the transaction was captured
  from a bank message, that message returns to the SMS inbox so you can import it again.
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
  messages. The action is silent and reports through notifications — "transaction recorded"
  on a parse, or "Saved for review" with a tap-through to the SMS inbox when a message
  couldn't be read — and returns a needs-review flag your shortcut can branch on; a
  companion "Open SMS inbox" action jumps straight to the review screen.
- **Reconnect Google Drive from the Backup screen** — if backup is on but no Google account is
  connected (declined, failed, or revoked access), a "Connect Google account" button now appears;
  previously the only way back was turning backup off and on again.

### Changed
- **The keyboard gets out of your way** — it now dismisses when you scroll any list, tap
  outside a text field, or complete an action (importing a message, saving a template or
  transaction, opening the date picker).
- **Bundled fonts** — DM Sans, Geist Mono and Tajawal now ship inside the app instead of being
  downloaded through Google Play services, so the app's typography renders correctly offline, on
  first launch, and on devices without Play services.

### Fixed
- Capturing a message via the iOS Shortcut right after the app started could miss your custom
  parse templates (only the built-ins were loaded yet) and wrongly send the message to review;
  capture now waits for your templates to load before matching.
- **Auto-backup now actually keeps up on iPhone** — opening the app runs any overdue backup
  on the spot (both platforms), instead of waiting for a background slot the system may never
  grant. iOS background refresh stays as a bonus path for days the app isn't opened.
- If Google Drive access expires or is revoked, the backup screen now offers **Connect Google
  account** again instead of failing forever with no way to reconnect.
- Staging builds back up to their own Drive file, so testing can no longer overwrite your real
  backup (they previously shared one file).
- Double-tapping a row that opens an editor (a brand, a category, a transaction) could crash
  the app; rapid re-taps are now ignored.

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
- **Crash on some AI suggestions** — an AI parse whose currency came back as a local
  abbreviation or symbol (e.g. "Dhs", "د.إ") crashed the app; such results now fall back
  to your app currency, the same as when the message names no currency at all.
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
