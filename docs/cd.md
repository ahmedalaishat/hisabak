# Continuous delivery

How Hisabak builds are distributed. Two environments with separate package names so they
coexist on one device:

| Variant | applicationId | Channel | SMS auto-capture |
|---------|--------------|---------|------------------|
| `staging` | `com.hisabak.staging` ("Hisabak STG") | Firebase App Distribution → testers | **Yes** (`RECEIVE_SMS`) |
| `prod` | `com.hisabak` ("Hisabak") | Google Play *(planned)* | **No** — share / select-text / paste |

**iOS mirrors the same two flavors** via Xcode configurations (`DebugStaging`/`ReleaseStaging`,
`iosApp/Configuration/Staging.xcconfig`) and the **`iosAppStaging`** scheme: bundle id
`com.hisabak.staging`, "Hisabak STG" on the home screen, seeded demo data (the `HisabakFlavor`
Info.plist key feeds the shared `AppConfig`). SMS auto-capture stays off in every iOS flavor —
Apple has no SMS-read API; the Shortcuts "Capture transaction" action is the near-automatic
path. No iOS distribution channel yet: staging installs are dev-signed direct deploys
(`xcodebuild -scheme iosAppStaging -configuration DebugStaging` + `devicectl`); a TestFlight
lane arrives with PR B7 once the paid Apple Developer account exists.

**SMS by flavor.** `RECEIVE_SMS` is a Google Play *restricted permission* that the Play build
must not declare, so the broadcast receiver + permission live in the **staging flavor only**
(`androidApp/src/staging/AndroidManifest.xml`). The `prod`/Play build is SMS-free and captures via the
permission-free paths (share a bank SMS, select its text → Hisabak, or paste). A
`BuildConfig.SMS_AUTO_CAPTURE` flag (per flavor) gates the SMS-only UI (onboarding primer,
auto-import banner).

## Staging → Firebase App Distribution (live)

`.github/workflows/distribute.yml` runs on every **push to `develop`** (and on demand via
*Run workflow*). It builds `assembleStagingRelease` and uploads the APK to the **`qa`** testers
group via the [Firebase Distribution GitHub Action](https://github.com/wzieba/Firebase-Distribution-Github-Action).

> The Firebase App Distribution **Gradle plugin** is not used — it requires the legacy AGP
> `AppExtension` that AGP 9 removed. The GitHub Action uploads the built APK directly instead.

**Required GitHub secret**
- `FIREBASE_SERVICE_ACCOUNT_JSON` — a Google Cloud service account (role: **Firebase App
  Distribution Admin**) JSON key, for `hisabak-finance-tracking`. Create it under
  *IAM & Admin → Service Accounts*, then add the JSON as a repo Actions secret.

The staging Firebase **App ID** (`1:469916556187:android:bb6a22afa66dde1b80df3e`) is not a
secret and lives inline in the workflow.

**Signing:** staging release builds use the debug key (the release signing config falls back to
debug when no keystore is configured) — fine for tester installs. The Play (prod) build passes
`-PrequireReleaseSigning`, which turns a missing/unusable release keystore into a hard build
failure instead of a silent debug-key fall back — a debug-signed AAB can never reach Play.

## Production → Google Play internal (live)

`.github/workflows/release.yml` runs on a **`v*` release tag**. It decodes the release
keystore, builds a **signed** AAB + APK, **publishes the AAB** to the Play **internal** track
via [r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play), and **attaches
the APK to a GitHub Release** (`hisabak-<tag>.apk`) for direct download. Promotion
**internal → production** stays a manual step in the Play Console.

**Play "What's new":** generated at release time from the latest `CHANGELOG.md` version
section by `scripts/changelog-to-whatsnew.sh` (flattened to plain bullets, capped at Play's
500-char limit) into `distribution/whatsnew/whatsnew-en-US`, which the upload step pushes via
`whatsNewDirectory`. So the storefront notes always track the CHANGELOG — keep that section
user-facing and concise. (The generated file is git-ignored.)

Distribution channels: **demo** = staging via Firebase (sample data); **direct APK** = the
GitHub Release here; **live** = Play.

## Crash reporting & analytics (Firebase Crashlytics + Analytics)

Release builds of **both** flavors (`assembleStagingRelease`, `bundleProdRelease`/`assembleProdRelease`)
are minified, so the Crashlytics Gradle plugin uploads the R8 **mapping file** to Firebase during
the build for deobfuscated stack traces. This needs no extra GitHub secret — it authenticates from
the committed `androidApp/google-services.json` (project `hisabak-finance-tracking`), not the
service-account keys above. Runtime collection for **both** Crashlytics and Analytics is gated on
`!BuildConfig.DEBUG` in `HisabakApp`, so only release builds report. Analytics events are strictly
**no-PII** — booleans, enums, and coarse amount buckets only, never raw amounts, names, notes, or SMS
text (the event catalogue is `core/domain/analytics/AnalyticsEvent.kt`). Because the app sends crash
diagnostics and usage analytics, the Play **Data safety** form must declare both (see
`play/CONSOLE-CHECKLIST.md`) and the privacy policy discloses them.

> Like Firebase, the Gradle Play Publisher **plugin** is not used (AGP 9 incompatibility). The
> Action uploads the built AAB directly.

**Required GitHub secrets**
- `RELEASE_KEYSTORE_BASE64` — base64 of the upload keystore (`base64 -i hisabak-keystore.jks`).
- `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` — keystore credentials.
- `PLAY_SERVICE_ACCOUNT_JSON` — a Google Cloud service account JSON with Play access (granted in
  the Play Console under *Users & permissions*).

**One-time Play Console setup**
1. Create the app in the Play Console and enable **Play App Signing**.
2. **Upload the first signed AAB manually** to the internal track — the API can't create the
   app or seed the first release. CI publishes every release after that.
3. **Store listing** is automated: `scripts/play/push-listing.mjs` pushes copy + graphics from
   `play/listing/en-US/` via the Android Publisher API. The Console-only items (Data safety,
   content rating, app access, etc.) have pre-filled answers in `play/CONSOLE-CHECKLIST.md`.
   Privacy policy is live at `https://ahmedalaishat.github.io/hisabak-android/privacy.html`.

**Cutting a release** (the `git-workflow` skill's "ship it"): bump `versionName`/`versionCode`,
merge `develop`→`main`, then tag `vX.Y.Z` and push — the tag triggers this workflow.
