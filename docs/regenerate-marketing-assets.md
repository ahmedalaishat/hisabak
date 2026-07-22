# Regenerating marketing assets (screenshots + demo GIF)

Runbook for recreating the README/Play Store screenshots (`docs/screenshots/`,
`play/listing/en-US/phone-screenshots/`) and the demo GIF (`docs/demo.gif`).

**Goal:** a premium, internally-consistent set — **dark theme, English, seeded data,
pinned 06:18 clock, clean status bar**. Captured on the **Pixel emulator** using the
**staging** build (the only flavor that seeds demo data) — never the real `com.hisabak`
app (this is a public repo; capturing real data would leak it).

`$S = emulator-5554` throughout. Requires `ffmpeg` on the host.

---

## 1. Emulator setup (not recorded — do before any capture)

```bash
S=emulator-5554
# Staging build (SEED_DATA=true). Pin to the emulator so it never hits a physical device:
./gradlew :androidApp:assembleStagingDebug
adb -s $S install -r app/build/outputs/apk/staging/debug/app-staging-debug.apk

adb -s $S shell cmd uimode night yes                          # dark theme
adb -s $S shell settings put global captive_portal_mode 0     # kills the wifi "!" (no-internet) glyph
adb -s $S shell cmd appops set com.hisabak.staging POST_NOTIFICATION ignore  # no system-notif icons mid-capture

# Demo-mode status bar: 06:18 ("618"), full wifi, no mobile, full battery, no notifications
for c in "enter" "clock -e hhmm 0618" "network -e wifi show -e level 4 -e fully true" \
         "network -e mobile hide" "battery -e level 100 -e plugged false" "notifications -e visible false"; do
  adb -s $S shell am broadcast -a com.android.systemui.demo -e command $c
done
```

### System date → 2024-12-08
The seed (`SeedData.kt`) generates transactions from Jan 2024 → **today**, so pin "today" to
get a full, in-range dataset. `adb root` is **blocked** on the Pixel AVD, so set it via the UI:

```bash
adb -s $S shell settings put global auto_time 0
adb -s $S shell am start -a android.settings.DATE_SETTINGS
```
Tap **Date** → in the calendar tap **Previous month** (`≈321,1111`) until the header reads the
target month (Jun 2026 → Dec 2024 = **18 taps**) → tap day **8** → **OK**. Verify with
`adb -s $S shell date` → `... Dec 8 ... 2024`.

### Fresh seed + skip onboarding
```bash
adb -s $S shell pm clear com.hisabak.staging
for p in RECEIVE_SMS READ_SMS SEND_SMS POST_NOTIFICATIONS; do
  adb -s $S shell pm grant com.hisabak.staging android.permission.$p   # pre-grant => no onboarding dialogs
done
adb -s $S shell am start -n com.hisabak.staging/com.hisabak.MainActivity
sleep 6   # seeding a year of data takes a moment before onboarding renders
```
Onboarding: tap **Skip** (top-right `1177,228`) → **Get started** (`640,2658`) → **Skip** restore
(`640,2664`) → Dashboard. (Perms pre-granted, so no permission dialogs.)

---

## 2. Still screenshots → `docs/screenshots/*.png` (overwrite; full-screen 1280×2856)

Capture: `adb -s $S exec-out screencap -p > /tmp/x.png`, then **Read the PNG back** to verify
(dark, 06:18, dirham glyph not literal "AED", no clipping). Screens:

| File | How to reach it |
|------|-----------------|
| `dashboard.png` | Dashboard tab, tap **This year** (`795,468`) for a rich chart |
| `budgets.png` | Dashboard → **Categories** segment (`1033,636`) |
| `transactions.png` | **Transactions** tab, tap **This year** |
| `sms.png` | **SMS** tab; paste 2–3 bank messages (see below) → **Parse & import**; then clear the shade |
| `manage.png` | **Manage** tab → **Categories** sub-tab (`913,514`) |
| `notifications.png` | Trigger an alert first (below), open the **bell**, clear the system notif it posts |

**SMS (sms.png):** tap the paste field (`640,894`), `adb -s $S shell input text "Purchase%sof%sAED%s245.50%swith%sCard%s4471%sat%sCarrefour,"` (use `%s` for spaces), tap **Parse & import** (`976,1108`). The paste flow stamps the **current system date** (Dec 8 2024) — unlike `adb emu sms send`, which stamps the host's real time.

**Notifications (notifications.png):** the `CategoryLimitMonitor` fires at 50/80/100% of a current-month expense limit. With "today" = Dec 8, December spend is near-zero, so add one: FAB (`1148,2412`) → amount `2500` → brand **Apple Store** (Shopping; Dec shopping = 0, limit 3,000 → 80%) → Save. Opens "Shopping at 80% of budget".

---

## 3. Play Store listing → `play/listing/en-US/phone-screenshots/` (overwrite)

Copies of the same images, numbered:
```bash
D=docs/screenshots; P=play/listing/en-US/phone-screenshots
cp $D/dashboard.png $P/1-dashboard.png; cp $D/transactions.png $P/2-transactions.png
cp $D/budgets.png $P/3-budgets.png; cp $D/notifications.png $P/4-notifications.png
cp $D/manage.png $P/5-manage.png
```

---

## 4. Demo GIF → `docs/demo.gif`

**Storyboard:** onboarding → dashboard (period toggles, Trends, Categories, open a category w/ limit)
→ Transactions (filter by category → All → add) → SMS live paste-parse → Manage (brands, categories,
open one) → Settings → Backup.

**Record smoothly:** pre-resolve tap coords with `uiautomator dump` **before** recording, then during
the recording issue only `input tap X Y` + `sleep` — **never dump mid-recording** (dumps freeze the
screen for seconds → janky video). Record in **segments at tab boundaries** (one continuous app
session, but separate `screenrecord` files so thinking-time between segments isn't recorded), then
concatenate. Re-record only the segment(s) whose screens changed and reuse the rest.

```bash
# one segment:
adb -s $S shell screenrecord --bit-rate 8000000 --size 720x1606 /sdcard/s1.mp4 &
sleep 1.2
# ... input tap / input text / sleep choreography ...
adb -s $S shell pkill -INT screenrecord; sleep 2
adb -s $S pull /sdcard/s1.mp4 /tmp/s1.mp4

# concat + encode (palette, ~1.6x speed, 320px, 12fps → ~3 MB, ~45 s):
printf "file '/tmp/s1.mp4'\nfile '/tmp/s2.mp4'\nfile '/tmp/s3.mp4'\nfile '/tmp/s4.mp4'\n" > /tmp/concat.txt
ffmpeg -y -f concat -safe 0 -i /tmp/concat.txt -c copy /tmp/demo_full.mp4
ffmpeg -y -i /tmp/demo_full.mp4 -vf "setpts=PTS/1.6,fps=12,scale=320:-1:flags=lanczos,palettegen=stats_mode=diff" /tmp/pal.png
ffmpeg -y -i /tmp/demo_full.mp4 -i /tmp/pal.png -filter_complex "[0:v]setpts=PTS/1.6,fps=12,scale=320:-1:flags=lanczos[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=3" docs/demo.gif
```
Keep the un-encoded `demo_full.mp4` as a high-quality source if a re-encode is wanted later.

### Tap coords (Pixel_10_Pro, 1280×2856 — re-verify if layout changes)
- **Tabs** (y≈2712): Dashboard `128`, Transactions `384`, SMS `640`, Manage `896`, Settings `1152`.
- **Onboarding:** Next/Get started pill `640,2658`; top Skip `1177,228`; restore Skip `640,2664`.
- **Dashboard chips** (y≈468): This month `191`, Last month `502`, This year `795`. **Segments** (y≈636): Summary `285`, Trends `639`, Categories `1033`. Top category row (This month) ≈ `499,823`.
- **Transactions:** Category filter chip `174,1309`; dropdown All `592,1787`, Dining `667,1926`; FAB `1148,2412`.
- **Add sheet:** amount field `744,1296`; Expense seg `202,1535`; brand chips y≈`1784` (Apple Store `223`, Nobu `498`, Starbucks `759`); Save `640,2472`. Type amount, then `input keyevent 4` to drop the keyboard before tapping Save.
- **SMS:** paste field `640,894`; Parse & import `976,1108`.
- **Manage** sub-tabs (y≈514): Brands `269`, Categories `913`. **Settings** Backup row ≈`640,868`.

---

## 5. Gotchas

- **Screenshot px ≠ device coords** at hi-DPI — resolve coords from `uiautomator dump` node `bounds`, not by eyeballing a screenshot. (The coords above are already device-space.)
- **Demo mode does not hide *existing* notification icons.** Clear them: `adb -s $S shell cmd statusbar expand-notifications`, tap **Clear all** (`resource-id=…:id/dismiss_text`), then `cmd statusbar collapse`. Budget-alert/SMS-import system notifications dirty the bar — suppress via the appops above, or clear the shade right before capturing.
- **`adb emu sms send`** stamps the host's real time (PDU timestamp) → 2026 dates and future-dated transactions. Use the in-app **paste** flow for date-consistent SMS.
- **`adb root` is blocked** on the Pixel AVD → set the date through the Settings UI.
- `installStagingDebug` (Gradle) installs to **all** connected devices — build the APK and `adb -s $S install -r` to target only the emulator.

## 6. Restore the emulator afterwards
```bash
adb -s $S shell am broadcast -a com.android.systemui.demo -e command exit
adb -s $S shell settings put global auto_time 1
adb -s $S shell cmd appops set com.hisabak.staging POST_NOTIFICATION default
```
