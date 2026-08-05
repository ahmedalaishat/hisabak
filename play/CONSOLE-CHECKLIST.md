# Play Console setup checklist (pre-filled)

The Play **Developer API** automates the *store listing* (title, descriptions, graphics,
screenshots, contact details) — see `scripts/play/README.md`. Everything below is **Console-only**
(no API) and gates review. Pre-filled for Hisabak; just confirm and click through.

Target: **internal testing first** → promote to production later.

---

## App content (left nav → "App content")

**Privacy policy**
- URL: `https://ahmedalaishat.github.io/hisabak/privacy.html`

**Data safety** → the released app collects **crash diagnostics and anonymous usage analytics only**
(Firebase Crashlytics + Analytics):
- Does your app collect or share any required user data? **Yes — diagnostics and app activity only.**
- Data types:
  - **App info & performance → Crash logs** and **Diagnostics** (Crashlytics).
  - **App activity → App interactions** (Analytics: screen views and in-app actions, in aggregate).
  Nothing else — no financial data, no SMS, no personal info; all of that stays on-device. Analytics
  carries no raw amounts/names/notes — only coarse, non-identifying buckets.
- Collected? **Yes.** Shared? **Yes** — processed by Google (Firebase) on our behalf.
- Processing: not optional for the user (always on in release), **not** used for tracking/ads,
  **encrypted in transit**. Data deletion: handled per Firebase's retention.
- Everything else (financial records, brands, categories, budgets): **not collected** — on-device.

**App access**
- Are all features available without special access? **Yes — all functionality is available
  without an account or login.**
- Reviewer note (paste): *"Hisabak has no sign-in. To test capture: open the SMS tab and paste the
  sample bank message shown there, or share/select any 'Purchase of AED 99.00 ... at <Merchant>'
  text into the app — it creates a transaction. Budgets: Manage → add a category limit."*

**Ads**
- Does your app contain ads? **No.**

**Content rating** (questionnaire → IARC)
- Category: **Utility / Productivity / Other** (finance).
- Answer **No** to all violence/sexual/profanity/controlled-substance/gambling questions.
- Expected result: **Everyone**.

**Target audience and content**
- Target age group: **18+** (do not include under-18 / "Designed for Families").
- Appeals to children? **No.**

**Financial features**
- Does your app provide financial features? Personal finance **management/budgeting only**.
- Does **not** offer: loans, banking, payments/transfers, crypto, investment/trading services.
  (Select "personal finance management / budgeting" if listed; otherwise "none of these.")

**Government apps / News / Health / COVID-19 / Data deletion (account)**
- All **No / Not applicable** (no accounts → no account-deletion URL required; data is on-device
  and removed on uninstall).

---

## Store settings (left nav → "Store settings")

- **App category:** Finance.
- **Tags:** budgeting, expense tracker, personal finance.
- **Store listing contact:** email `ahmedalaishat@gmail.com` (phone/website optional).
- **External marketing:** leave default.

## Store listing (mostly pushed via API; confirm)

- **App name:** Hisabak — Budget & Expenses
- **Short / full description:** pushed via API (`play/listing/en-US/`).
- **Graphics:** app icon (512×512), feature graphic (1024×500), 2–8 phone screenshots — pushed via
  API from `play/listing/en-US/graphics` + `phone-screenshots`.
- **Default language:** English (United States) — en-US.

---

## Pricing & distribution

- **Free** app, **no in-app purchases**.
- **Countries:** select target markets (UAE/GCC first, or worldwide — your call).
- Confirm the content guidelines + US export law checkboxes.

---

## Release (internal testing)

1. **App signing:** keep **Play App Signing** enabled (default).
2. **First AAB is manual:** the API can't seed a new app's first release — upload the signed
   `prod` AAB once in **Testing → Internal testing → Create release** (built SMS-free by CI; see
   `docs/cd.md`). Add testers by email and share the opt-in link.
3. After that, CI publishes each tagged release to the internal track automatically.
4. When ready, **promote internal → production** and submit for full review.

> Note: the public/Play build is **SMS-free** (no `RECEIVE_SMS`). Keep the listing copy framed as
> "share / select / paste" capture — it must match the build the reviewer tests.
