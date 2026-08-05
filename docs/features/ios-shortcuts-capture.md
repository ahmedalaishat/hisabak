# iOS Shortcuts capture action

## Requirement
iOS Shortcuts capture action: expose transaction capture as an App Intent so a Shortcuts
personal automation (When I get a message → Run immediately) can auto-parse bank SMS on
iOS. Add CaptureSource.SHORTCUT (notifiesOnRecord = true), a thin iosMain Kotlin bridge
(captureFromShortcut) resolving CaptureTransactionUseCase + APPLICATION_SCOPE from Koin, a
Swift AppIntent ('Capture transaction', text parameter, result dialog) +
AppShortcutsProvider in iosApp, unit tests for the new source policy, docs.

## Spec
- **Goal:** hands-free bank-SMS capture on iOS. Apple exposes no SMS-read API, so the app
  ships a Shortcuts **action**; the user wires it to a "When I get a message" personal
  automation ("Run immediately", optionally filtered to the bank sender) — near-parity
  with Android's `SMS_BROADCAST` without the app ever reading messages.
- **In scope:**
  - `CaptureSource.SHORTCUT` with `notifiesOnRecord = true` (capture happens outside the
    app, so the "transaction recorded" heads-up is wanted).
  - `captureFromShortcut(text, completion)` in `shared/iosMain` — the thin platform
    adapter over `CaptureTransactionUseCase`, running on `APPLICATION_SCOPE` so the
    capture isn't tied to the intent's lifetime; completion carries success + a localized
    outcome message (CMP resources, en+ar).
  - `CaptureTransactionIntent` (AppIntents) + `AppShortcutsProvider` in `iosApp/` so the
    action is discoverable in the Shortcuts app with zero setup.
- **Out of scope:** a Share Extension (separate process, needs an App Group for the DB);
  localizing androidApp's `CaptureActivity` toasts (pre-existing English literals);
  Siri phrases beyond the default; any automation setup UI in-app.
- **Acceptance criteria:**
  - Capturing with `SHORTCUT` parses + persists the transaction, posts the recorded
    notification, re-checks budgets, and logs `sms_captured` with `source=shortcut`.
  - Unparseable text fails without persisting or notifying (existing funnel behavior).
  - The intent returns a localized dialog for both outcomes.
  - "Capture transaction" appears in the Shortcuts app's action list for Hisabak.
- **Edge cases:** app not running when the automation fires — iOS launches the process
  headlessly for an in-app intent, so `iOSApp.init` → `startIosApp()` has run before
  `perform()`; Koin is always up. Blank/garbage text → failure dialog, nothing stored.
- **Assumptions:** dialog strings are new shared CMP resources
  (`shortcut_capture_success` / `shortcut_capture_failure`) rather than reusing Android's
  hardcoded toast literals; intent metadata (title, parameter names) stays English, like
  all store-facing metadata until B7.

## User setup (per device, one time)

Shortcuts app → Automation → New → **When I get a message** → set the bank as the sender
(recommended) → **Run immediately** → action: Hisabak's **Capture transaction** → pass the
shortcut input (the message text) as the "Message text" parameter. Incoming bank SMS then
parse automatically; recorded transactions post the usual notification.

## Design
- **Domain/model changes:** one enum case, `CaptureSource.SHORTCUT(notifiesOnRecord =
  true)`. The funnel (`CaptureTransactionUseCase`) is untouched — this is the
  open-for-extension path the pipeline was built for.
- **Files to add/change:**
  - `shared/src/commonMain/.../capture/CaptureSource.kt` — new case
  - `shared/src/commonMain/composeResources/values{,-ar}/strings.xml` — 2 outcome strings
  - `shared/src/iosMain/.../feature/sms/platform/ShortcutCapture.kt` — the bridge (new)
  - `iosApp/iosApp/CaptureTransactionIntent.swift` — AppIntent + provider (new; the
    project uses filesystem-synchronized groups, so no pbxproj edit)
  - `shared/src/commonTest/.../CaptureTransactionUseCaseTest.kt` — SHORTCUT policy test
- **Test strategy:** extend `CaptureTransactionUseCaseTest` — SHORTCUT saves, notifies,
  and reports `source=shortcut` in analytics (the PII-free param). Swift/AppIntents glue
  is compile-gated by the `ios-compile.yml` app-build job; the automation itself is
  device-only (Shortcuts can't run on a simulator-received SMS) and is verified manually.
- **Trade-offs / decisions:** App Intent in the main app target (not an extension) — runs
  in the app process, so Koin/Room come for free and the bridge stays ~20 lines, matching
  the thin-Swift-bridge philosophy (`CryptoKitGcmCipher`). Callback bridge rather than a
  suspend export — no exceptions cross the framework boundary, same rule as the GCM
  cipher. `notifiesOnRecord = true` mirrors `SMS_BROADCAST`/`SHARE`: the user is outside
  the app when it fires.
