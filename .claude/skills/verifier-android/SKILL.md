---
name: verifier-android
description: Run-and-verify harness for the Hisabak Android app on an emulator — boot, install, drive via adb, capture evidence. Use when verifying a change end-to-end, confirming a fix works, or capturing screenshots of real behavior. Pairs with the built-in /verify skill, which auto-discovers it.
user-invocable: true
---

# Hisabak Android verifier

The repo's **evidence-capture protocol**: how to get a handle on the real app and drive the
surface a change touches. The built-in `/verify` skill looks for this skill first — when
present, follow it instead of cold-starting.

This is a **generic harness**, not a fixed test script. The job is always: *build → run →
drive the surface the diff touches → capture what you see*. The repo facts below are the
durable anchors that make that fast; the checks themselves come from whatever you're verifying.

## When to use it (and when not)

- **Use it** for changes with a runtime/GUI surface: screens, navigation, Room/persistence,
  the capture intents, notifications, first-run/onboarding.
- **Skip it** for changes with no runtime surface — docs, type-only, pure build config. Example:
  the release-signing gate (`-PrequireReleaseSigning`) is verified by running Gradle, not the app.
- Don't substitute unit tests or typecheck for running the app — those prove CI, not behavior.

## Repo anchors (stable facts)

| Thing | Value |
|---|---|
| Package (prod) | `com.hisabak` |
| Package (staging) | `com.hisabak.staging` — labeled "Hisabak STG" |
| Primary build | **prod debug** — `./gradlew :androidApp:installProdDebug` |
| SMS-broadcast build | **staging** — `./gradlew :androidApp:installStagingDebug` (prod is SMS-free) |
| Launcher | `com.hisabak/.MainActivity` |
| Bottom-nav tabs | Dashboard · Transactions · SMS · Manage · Settings |
| Capture entry point | `com.hisabak/.feature.sms.platform.CaptureActivity` (exported; `ACTION_SEND` + `ACTION_PROCESS_TEXT`, `text/plain`) |
| Bank-SMS templates | `androidApp/src/main/java/com/hisabak/feature/sms/domain/DefaultSmsTemplates.kt` (e.g. `Purchase of AED {amount} with {card} at {brand},`) |
| Emulators (AVDs) | `Pixel_10_Pro`, `Pixel_6_Pro_Rooted` |

## Get a handle

**Boot an emulator and wait for it** (run the boot detached, then block on a real condition —
don't `sleep` blindly):

```bash
$ANDROID_HOME/emulator/emulator -avd Pixel_10_Pro -no-snapshot-load -no-boot-anim \
  -gpu swiftshader_indirect > /tmp/emulator.log 2>&1 &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
```

**Clean-install** so you get a true first-run (onboarding, seeded starter data, empty states):

```bash
adb uninstall com.hisabak >/dev/null 2>&1   # ignore "not installed"
./gradlew :androidApp:installProdDebug
adb shell am start -n com.hisabak/.MainActivity
```

## Drive it

**Never guess pixel coordinates from a screenshot** — on this device the screenshot is
1280×2856 and visual estimates miss. Pull exact element bounds from the view hierarchy and tap
the center:

```bash
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | tr '<' '\n<' | grep 'text="Next"'   # → bounds="[x1,y1][x2,y2]"
adb shell input tap <cx> <cy>
adb exec-out screencap -p > /tmp/frame.png   # then Read /tmp/frame.png
```

Notes:
- Bottom sheets are modal overlays — the bottom-nav is behind them; dismiss the sheet (Cancel)
  before switching tabs.
- To type into a field, tap its `EditText` node (often narrow / left of the displayed value),
  then `adb shell input text "…"`; clear with repeated `adb shell input keyevent 67`.

## Verify outcomes from state, not just pixels

- Notifications: `adb shell dumpsys notification --noredact | grep -A12 com.hisabak`
  (read `android.title` / `android.text`).
- Exact field values: re-run `uiautomator dump` and read the node `text=` (e.g. the editor
  shows the stored amount un-abbreviated).
- Crashes: `adb logcat -d | grep -iE "FATAL|AndroidRuntime"`.

## Headless capture testing (no UI fiddling)

Feed bank-SMS text straight to the capture activity. Craft text matching a line in
`DefaultSmsTemplates.kt`:

```bash
adb shell "am start -n com.hisabak/.feature.sms.platform.CaptureActivity \
  -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT 'Purchase of AED 1,234.56 with Card 1234 at Starbucks,'"
```

**Quoting gotcha:** wrap the whole `am …` command in outer double quotes so the *device* shell
receives the text as a single argument. Without them the value truncates at the first space
(you'll get `Purchase` only) and the parse silently fails. Staging additionally exercises the
real `RECEIVE_SMS` broadcast path.

## Gotchas

- Screenshot pixels ≠ device coordinates on hi-DPI → always tap from `uiautomator` bounds.
- Clean-install before first-run/onboarding/seed checks; otherwise you test an upgraded install.
- In-app amounts render the **dirham glyph** (the `content-desc="AED"` on that ImageView is the
  a11y label, not visible text). System **notifications** legitimately contain "AED" — they
  can't embed the custom glyph. Don't flag either as a bug.

## Reporting

Use the built-in `/verify` skill's report format (Verdict · Claim · Method · Steps with
✅/❌/⚠️/🔍 · Findings). This skill only supplies the *handle*; `/verify` owns the verdict.

## Example — verifying a capture change (illustrative, not a fixed script)

1. Clean-install prod debug; launch.
2. Send the `ACTION_SEND` intent above.
3. `dumpsys notification` shows **"Transaction recorded / AED 1,234.56 at Starbucks …"** and the
   transaction appears under Transactions → confirms parse + persist + notify completed even
   though the translucent capture activity finished immediately.

Real verifications target whatever the diff actually changed — drive that surface, not this one.
