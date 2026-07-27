# Google Drive backup — Cloud setup

The encrypted backup/restore feature stores a single file in the user's **Drive App Data Folder**
(hidden, app-private) using the `drive.appdata` OAuth scope. No secret ships in the app — Android
authorizes by the app's **package name + signing SHA-1**. Until the steps below are done, connecting
an account in the app will fail with an auth error; the code is otherwise complete.

## 1. Enable the Drive API
In the [Google Cloud Console](https://console.cloud.google.com/) for the project backing Firebase
(`hisabak-finance-tracking`): **APIs & Services → Library → Google Drive API → Enable**.

## 2. OAuth consent screen
**APIs & Services → OAuth consent screen**:
- User type **External**; fill app name, support email, developer contact.
- Add the scope `https://www.googleapis.com/auth/drive.appdata` (App Data Folder — **sensitive**,
  not restricted).
- While unverified, add yourself + testers under **Test users** (works without full verification).
- For a public Play release, submit for **OAuth verification** (sensitive-scope review). The
  appdata scope does **not** require the restricted-scope CASA assessment.

## 3. Android OAuth client(s)
**APIs & Services → Credentials → Create credentials → OAuth client ID → Android** for each package:
- **prod:** package `com.hisabak` + the signing SHA-1.
- **staging:** package `com.hisabak.staging` + the signing SHA-1 (only if testing Drive there).

Get the SHA-1 with:
```bash
./gradlew :androidApp:signingReport     # SHA1 per variant
```
**Debug builds are signed with the release keystore** when one is configured (`keystore.properties`),
so `prodDebug` and `prodRelease` share the **same SHA-1** — register that one fingerprint and both
debug and release builds authorize. (On a checkout with no keystore, debug falls back to the default
debug keystore; register that SHA-1 instead.)

## 4. (If using google-services.json) refresh it
Adding OAuth clients  doesn't require app code changes, but if you regenerate
`androidApp/google-services.json` from Firebase, keep the existing Firebase config intact.

## Verify
Build a debug variant on a device/emulator signed with a registered SHA-1, sign in with a **test
user** account, enable backup → connect account → consent → **Back up now** should succeed; the file
appears in that account's Drive App Data (not visible in the normal Drive UI).

## Notes
- Tokens are short-lived and fetched on demand; the app stores only the chosen account email
  (the encryption passphrase is stored separately, Keystore-encrypted).
- Revoking access in the Google account settings invalidates backups' authorization; the app will
  prompt to reconnect.

## 5. iOS client (Phase B)

The iOS app authorizes with **ASWebAuthenticationSession + PKCE** (no Play Services, no client
secret). One-time setup in the same GCP project:

1. **APIs & Services → Credentials → Create credentials → OAuth client ID → iOS**, one per
   bundle id (no SHA-1 — iOS clients key off the bundle id):
   - **prod:** `com.hisabak`
   - **staging:** `com.hisabak.staging` (only if testing Drive there)
2. Copy each client id (`NNNN-xxxx.apps.googleusercontent.com`) into its xcconfig as
   **`GOOGLE_OAUTH_CLIENT_ID=NNNN-xxxx.apps.googleusercontent.com`** — prod into
   `iosApp/Configuration/Config.xcconfig`, staging into `Staging.xcconfig` (which `#include`s
   Config and must override it, or staging inherits the prod client and ships a client id that
   doesn't match its own bundle id). Info.plist's `GoogleOAuthClientID` expands from it at
   build time. While it's blank, the app reports Drive as unavailable ("Connect a Google
   account" stays inert) — everything else about backup still works.
3. The OAuth redirect uses the **reversed client id** as a custom URL scheme
   (`com.googleusercontent.apps.NNNN-xxxx:/oauth2redirect`); ASWebAuthenticationSession
   handles the callback directly, so no CFBundleURLTypes entry is needed.

The refresh token is stored in the iOS Keychain; access tokens are refreshed silently per
Drive call. Backups written by either platform restore on the other — the encrypted format
is identical (verified by `tools/crypto-interop/run.sh`).
