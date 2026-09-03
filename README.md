<p align="center">
  <img src="docs/app-icon.svg" width="96" height="96" alt="Hisabak app icon">
</p>

<h1 align="center">Hisabak</h1>

<p align="center">
  A personal finance tracker for Android and iOS that turns your bank messages into a clean,
  organized view of your money — categorize spending, set monthly budgets, and get notified
  before you overshoot. One Kotlin Multiplatform codebase, two native apps.
</p>

<p align="center">
  <a href="../../actions/workflows/test.yml"><img src="../../actions/workflows/test.yml/badge.svg" alt="CI"></a>
  <a href="../../releases"><img src="https://img.shields.io/github/v/release/ahmedalaishat/hisabak?color=0B7A5B&label=release" alt="Release"></a>
  <img src="https://img.shields.io/badge/platform-Android%20%C2%B7%20iOS-3DDC84" alt="Platform">
  <img src="https://img.shields.io/badge/minSdk-29-blue" alt="Min SDK">
  <img src="https://img.shields.io/badge/Kotlin%20Multiplatform-Compose%20Multiplatform-7F52FF" alt="Kotlin Multiplatform">
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License">
  <img src="https://img.shields.io/badge/code-100%25%20AI--written-7F52FF" alt="100% AI-written code">
</p>

<p align="center">
  <b>🤖 Built entirely in plain English.</b><br>
  From idea and architecture to the Kotlin code, tests, CI/CD automation, the app icon and
  store graphics, the demo video, and the Play Store listing — the entire project was produced
  by <b>Claude Opus 4.8</b> via Claude Code. I directed and reviewed; Claude built it.
</p>

<p align="center">
  <img src="docs/demo.gif" width="300"
       alt="Hisabak demo — onboarding, dashboard, budgets, transactions, and SMS capture">
</p>

<p align="center"><sub><i>A quick tour — dark theme.</i></sub></p>

---

## 📸 Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/dashboard.png" width="220"><br><sub>Dashboard</sub></td>
    <td align="center"><img src="docs/screenshots/budgets.png" width="220"><br><sub>Budgets</sub></td>
    <td align="center"><img src="docs/screenshots/notifications.png" width="220"><br><sub>Budget alerts</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/transactions.png" width="220"><br><sub>Transactions</sub></td>
    <td align="center"><img src="docs/screenshots/sms.png" width="220"><br><sub>SMS inbox</sub></td>
    <td align="center"><img src="docs/screenshots/manage.png" width="220"><br><sub>Manage</sub></td>
  </tr>
</table>

> Shown in dark theme. Light and dark are both first-class — every screen is designed for both.

---

## 📥 Download

Three ways to get it (Android 10+ / API 29):

- **Try the demo** — a build pre-loaded with sample data, so the dashboard, charts, and budgets
  are populated the moment you open it:
  **[install the demo](https://appdistribution.firebase.dev/i/b817bdd33c687f05)** via Firebase
  App Distribution (accept the invite, then install through the Firebase App Tester app).
- **Direct APK** — the production build (starter categories only, your data only): grab the latest
  `hisabak-*.apk` from the [**Releases**](../../releases) page, then open it on-device (allow
  "install unknown apps") or `adb install` it. A ~3 MB R8-minified, release-signed build.
- **Google Play** — coming soon (currently in internal testing).

**iOS:** the app builds and runs from source (see [Build & run](#-build--run)); an App Store /
TestFlight release is pending an Apple developer account.

---

## ✨ Features

Everything below is built and shipping today:

- [x] 🚀 **Guided onboarding** — a premium, animated first-launch walkthrough of the app's
  features, ending with an SMS-permission primer.
- [x] 💬 **SMS capture** — parse bank SMS into transactions automatically (with permission), or
  capture one on demand by sharing it into Hisabak, selecting its text → Hisabak, or pasting it.
  Messages no template recognizes fall back to **AI** — on-device (Gemini Nano / Apple
  Intelligence) where the phone supports it, nothing leaving the device; and on the many phones
  that don't, an **optional** parsing service you host and switch on yourself. Either way the parse
  is suggested on the message and you confirm it with one tap, and Hisabak learns a template from
  it so that bank format is read locally from then on.
- [x] 🔔 **Budgets with alerts** — set a monthly limit per category and get notified at **50% /
  80% / 100%**. Alerts arrive as a system notification *and* an in-app entry; tapping one opens
  the dashboard with that category expanded.
- [x] 📊 **Dashboard** — net worth with cash / savings / investment breakdown, income & expense
  trends, category and brand breakdowns, and period filters (this/last month, this/last year, all
  time) across Summary, Trends, and Categories tabs — plus a **Review** card that points out what
  changed and what needs attention (a category over or near its limit, a sharp rise or fall, your
  largest expense, savings rate, uncategorized spend), worked out on the phone with nothing sent
  anywhere, each finding one tap from its transactions.
- [x] 🧾 **Transactions** — searchable, filterable list (by brand, category, date range), with
  uncategorized spending surfaced for quick cleanup. Add, edit, or delete an entry (deletion is
  confirmed first, and hands any bank message that created it back to the SMS inbox).
- [x] 🗂️ **Organize** — categories (income / expense / savings / investment) with **144 icons**
  across 11 groups (searchable in English *and* Arabic) and **any colour you like** — you pick the
  hue, the app derives shades that stay readable in light, dark, and notifications. Naming a
  category picks its icon for you, and it warns when a colour or icon clashes with one you already
  use. Brands map to categories, and either links straight to its transactions. Safe deletion with
  brand-merge and confirmation.
  Create a brand right from the transaction sheet and a category right from the brand editor —
  and on devices with on-device AI, the brand editor suggests the category (existing or a
  drafted new one) as a tap-to-confirm chip.
- [x] 🧩 **Custom SMS templates** — teach the app your bank's message format by example:
  paste a sample (or start from an unparsed inbox message), confirm the highlighted
  amount/brand/date, and future messages parse instantly. Specificity-ranked matching and a
  save-time preview keep a bad template from breaking the built-ins. Backed up with your data.
  Confirming an AI-parsed message **teaches a template automatically** — the next message in that
  format parses with no AI at all, on any device. Learned rules are marked as such and can be
  edited, deleted, or undone on the spot.
- [x] ✨ **Smart capture** — the SMS inbox takes anything: auto-captured bank SMS, pasted
  messages, or plain notes ("lunch 45 yesterday"). Known formats import instantly; everything
  else gets an AI suggestion you confirm with one tap — on-device where supported, otherwise via
  the optional parsing service (off by default).
- [x] 📱 **iOS app** — the same app, natively on iPhone from the shared Kotlin Multiplatform
  codebase: every screen, Arabic/RTL, App Lock (Face ID), Drive backup, templates, and on-device
  AI parsing (Apple Intelligence). iOS has no SMS access by design, so capture goes through a
  **"Capture transaction" Shortcuts action** — point a "When I get a message" automation at it
  for hands-free capture, without the app ever reading your messages.
- [x] 💸 **Savings & investment withdrawals** — savings/investment entries have a deposit or
  withdrawal direction, so taking money back out nets your buckets and returns it to cash.
  Doubles as a loan ledger: keep a person as a brand in a savings category and their brand
  total shows what's still outstanding (a fully repaid loan reads 0).
- [x] 🛎️ **Notifications center** — unread badge on the bell, swipe-to-dismiss, and mark-all-read.
- [x] 🔒 **App lock** — optional biometric / device-PIN lock that gates access on launch and when
  you return to the app, so your finances stay private on a shared device. (Your data lives in
  app-private storage, protected at rest by the OS's built-in file-based encryption.)
- [x] ⚙️ **Settings** — pick your **theme** (light / dark / system) and **language**
  (**English / العربية**, fully localized and right-to-left). Both are saved across launches.
- [x] ☁️ **Google Drive backup & restore** — connect a Google account and back up your data to a
  private, app-only space in your Drive, optionally encrypted with a passphrase (AES-256-GCM), with
  optional **automatic backups** (daily / weekly / monthly). A new install offers to restore from
  Drive right after onboarding. (Requires the app's Drive access to be configured.)
- [x] 📴 **Offline-first** — all data is stored locally on-device (Room). Light & dark themes,
  polished motion, edge-to-edge.

---

## 🗺️ Roadmap

What's next, roughly in order:

- [ ] 🍎 **iOS on the App Store** — the iOS app is built and running (see Features); public
  distribution via TestFlight / the App Store awaits an Apple developer account.
- [ ] 🛎️ **Notification capture** — read bank transaction notifications to capture spending without
  the SMS permission (works in the Play build).
- [ ] 💱 **Multi-currency** — track transactions and balances across more than one currency.
- [ ] 🤖 **Auto-categorization** — automatic brand → category suggestions.
- [ ] 💡 **AI insights assistant** — ask questions about your spending and get clear, on-point
  answers.
- [ ] ↩️ **Refunds** — record a refund against a transaction so returned money is reflected in
  balances and category spend.

> Privacy stays first: AI features will be designed to keep your data under your control.

---

## 🧰 Tech stack

- **Language:** Kotlin (Multiplatform — Android + iOS from one codebase)
- **UI:** Compose Multiplatform + Material 3
- **Navigation:** Navigation 3 (multiplatform: androidx runtime + JetBrains UI artifacts)
- **Persistence:** Room KMP (local, offline-first)
- **DI:** Koin
- **Async:** Coroutines + Flow
- **State:** ViewModel + `collectAsStateWithLifecycle`
- **Charts:** Vico (multiplatform)
- **AI parsing:** Gemini Nano via the ML Kit GenAI Prompt API (Android) / Apple Foundation Models
  (iOS) — on-device, nothing transmitted; plus an optional self-hosted parse service (`server/`,
  FastAPI + Docker) for the majority of phones that have no on-device model — opt-in, and it stores
  nothing
- **Crash reporting & analytics:** Firebase Crashlytics + Analytics (release builds only;
  disabled in debug; analytics events carry no personal or financial data)

---

## 🏗️ Architecture

Three Gradle modules — **`shared`** (Kotlin Multiplatform: domain, data (Room/DataStore),
ViewModels, navigation, and the Compose Multiplatform UI; compiles for Android and iOS),
**`androidApp`** (a thin Android shell: platform integrations, permission/consent glue), and
**`testutil`** (shared test fakes) — plus **`iosApp`**, the Xcode project: a SwiftUI shell around
the shared UI with native Swift bridges for CryptoKit (backup encryption) and Apple Foundation
Models (AI parsing). The KMP migration is functionally complete — both apps ship from the shared
code ([migration plan](docs/kmp-migration.md)); the pure-Android line is preserved on the
[`android` branch](../../tree/android).

Feature-by-layer, with clean architecture inside each feature:

```
com.hisabak
├── core/                                shared primitives (Money, Clock, DomainResult, Room db)
├── ui/                                  design system: theme, motion, shared components
└── feature/<name>/
    ├── domain/        entities, use cases, repository interfaces
    ├── data/          Room repository implementations + mappers
    └── presentation/  stateful Route + stateless Screen + ViewModel (MVI-style)
```

Budget alerts are driven by a small reactive engine (`CategoryLimitMonitor`) that observes
transactions and limits and fires once per threshold per category per month — so manually added
and SMS-imported transactions are both covered through a single path.

---

## 🧪 Testing & quality

The domain logic and ViewModels are covered by **330 JVM unit tests** (money math, the SMS
template parser, backup/restore, budget/limit logic, and ViewModel state) that run on the plain
JVM — no emulator needed:

```bash
./gradlew unitTests
```

Quality is enforced, not just hoped for:

- **CI** — every pull request targeting `develop`/`main` runs the suite, the Kotlin/Native iOS
  compile, and an iosApp simulator build via GitHub Actions.
- **Branch protection** — a red suite can't be merged into the shared branches.
- **Auto-merge** — routine PRs into `develop` merge automatically once the suite passes;
  release PRs (`develop`→`main`) are merged deliberately by hand.
- **Local guard** — a pre-finish hook runs the tests on any change that touches Kotlin.

Tests use hand-written fakes over a small harness (`com.hisabak.testutil`) rather than a
mocking framework. See [`docs/testing.md`](docs/testing.md) for the full strategy.

---

## 🚀 Build & run

**Requirements:** Android Studio (latest stable), JDK 17, Android SDK. `minSdk 29`, `targetSdk 36`.

```bash
git clone https://github.com/ahmedalaishat/hisabak.git
cd hisabak
./gradlew installStagingDebug   # build & install the staging variant on a device/emulator
# or open the project in Android Studio and Run (pick the "stagingDebug" variant)
```

The app has two flavors with separate package names so they coexist on one device:
**production** (`com.hisabak`) and **staging** (`com.hisabak.staging`, labeled "Hisabak STG").
The **staging** build seeds demo data on first launch so the dashboard, charts, and budgets are
populated immediately; the **production** build seeds only a small set of starter categories
(no brands or transactions) so it's usable right away with your data only.

**iOS** (requires a Mac with Xcode): open `iosApp/iosApp.xcodeproj` and run the `iosApp` scheme,
or build headlessly:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

The same two flavors exist as schemes — `iosApp` (production) and `iosAppStaging` ("Hisabak STG"
with seeded demo data). Running on a physical iPhone needs a development team set via
`DEVELOPMENT_TEAM=<your team id>` (or in Xcode's Signing settings).

---

## 💡 Inspiration

Hisabak is inspired by [**Hisabi**](https://github.com/hisabi-app/hisabi) — a self-hosted Laravel
personal-finance web app by Saleem Hadad. The domain model (transactions, brands, categories,
budgets, SMS ingestion, dashboard metrics) mirrors Hisabi's so concepts map cleanly between the two.

---

## 📄 License

**Hisabak** is licensed under the **MIT License** — see [`LICENSE`](LICENSE) for details.

---

<p align="center">
  Built by <strong>Ahmad Alaishat</strong> · <a href="https://github.com/ahmedalaishat">GitHub</a> · <a href="https://www.linkedin.com/in/ahmedalaishat">LinkedIn</a>
</p>
