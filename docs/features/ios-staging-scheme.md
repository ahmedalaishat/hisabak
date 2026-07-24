# iOS staging scheme

## Requirement
Set up the iOS staging scheme mirroring the Android `staging` flavor; CI/CD (TestFlight)
comes later with the paid Apple Developer plan (PR B7).

## Spec
- **Goal:** a staging build of the iOS app that installs alongside prod, is visually
  distinguishable, and seeds demo data — the same contract as Android's `staging` flavor.
- **In scope:** `DebugStaging`/`ReleaseStaging` Xcode configurations backed by
  `Configuration/Staging.xcconfig`, a shared `iosAppStaging` scheme, the `HisabakFlavor`
  Info.plist key, and `AppConfig.seedData` wired from it in `iosPlatformModule`.
- **Out of scope:** any distribution channel (TestFlight lane lands with B7); a staging
  Firebase app registration (the committed `GoogleService-Info.plist` is prod's — see
  Trade-offs); staging-specific app icon.
- **Acceptance criteria:**
  - `xcodebuild -scheme iosAppStaging -configuration DebugStaging` produces a bundle with
    id `com.hisabak.staging`, display name "Hisabak STG", `HisabakFlavor=staging`.
  - The staging app installs alongside prod and seeds full demo data on first launch.
  - The prod scheme is byte-for-byte unaffected (`HisabakFlavor=prod`, no seeding change).
- **Assumptions:** no `MARKETING_VERSION` "-staging" suffix (Android's `versionNameSuffix`
  counterpart) — `CFBundleShortVersionString` is format-validated at archive/upload time
  and the display name already distinguishes the builds. `smsAutoCapture` stays `false` in
  every iOS flavor: unlike Android staging (which carries `RECEIVE_SMS`), iOS has no
  SMS-read API — the Shortcuts action is the near-automatic path.

## Design
- **Mechanism:** flavor-varying settings live in the xcconfig layer, mirroring how
  `Config.xcconfig` already carries identity. `Staging.xcconfig` `#include`s the base and
  overrides `PRODUCT_BUNDLE_IDENTIFIER`, `INFOPLIST_KEY_CFBundleDisplayName`, and
  `HISABAK_FLAVOR`; `Config.xcconfig` gains `HISABAK_FLAVOR=prod` as the default. The
  physical `Info.plist` exposes `HisabakFlavor=$(HISABAK_FLAVOR)` to the shared code.
- **Custom configuration names:** the KMP `embedAndSignAppleFrameworkForXcode` task can't
  infer debug/release from `DebugStaging`/`ReleaseStaging`, so each staging configuration
  pins `KOTLIN_FRAMEWORK_BUILD_TYPE` (debug/release) — the documented escape hatch.
- **Kotlin:** `bundleFlavor()` in `IosPlatformModule` reads the plist key;
  `AppConfig(seedData = flavor == "staging", …)`. `startIosApp` already branches
  `seedIfEmpty()` vs `seedStartersIfEmpty()` on it — no shared-code changes.
- **Trade-offs / decisions:** staging reuses prod's `GoogleService-Info.plist` — Firebase
  logs a bundle-id-mismatch warning, and Crashlytics is collection-off in Debug anyway;
  registering a staging iOS app in Firebase is deferred until staging release builds
  matter (B7). Same for the Drive OAuth client: Google validates the redirect scheme, not
  the runtime bundle id, so staging Drive backup works with the shared client id. Same
  `BGTaskScheduler` identifier string in both apps is fine — identifiers are per-app.
- **Test strategy:** no unit-testable logic (one plist read); verified by building both
  schemes, inspecting the built `Info.plist`s, and a side-by-side simulator install where
  staging seeded 585 demo transactions and prod stayed prod.
