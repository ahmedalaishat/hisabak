# Hisabak — Design System

**Hisabak** (Arabic: حسابك, "your account") is a personal-finance tracker for **Android**. It captures bank transactions from SMS automatically, lets people categorize spending, set budgets, and understand where their money goes through clean dashboards and charts. The promise is **zero-friction expense tracking via SMS automation + calm analytics**.

This repository is the brand's design system: tokens, fonts, reusable React components, foundation specimen cards, and a high-fidelity mobile UI kit.

- **Platform:** Android (Jetpack Compose / Material 3 mindset), portrait, phones only (360–412dp).
- **Market / language:** English-first, with Arabic (RTL) support planned. Copy here is English.
- **Themes:** Light **and** dark are first-class — every screen ships both.
- **Aesthetic:** Modern & minimal — Stripe/Linear calm, not flashy. Green is *meaningful*, never decorative.

### Sources
This system was authored from a detailed product design brief (no external codebase or Figma was provided). There is **no upstream repo or Figma link** — this project is the source of truth. If a codebase or Figma is later attached, reconcile tokens and component names against it.

---

## CONTENT FUNDAMENTALS — how Hisabak writes

**Voice:** plain, calm, confident. A trustworthy money companion, never salesy or jokey.

- **Person:** Address the user as **you** ("Add your first transaction", "Turn it on to log transactions from SMS"). The app refers to itself only as "Hisabak" when needed.
- **Casing:** **Sentence case** everywhere — buttons, headers, menu items ("Add transaction", not "Add Transaction" in product copy; screen *titles* use Title Case like "SMS Inbox" as proper nouns). Be consistent within a surface.
- **Tone:** direct and short. Prefer verbs. "Parse & Import", "Enable", "Add Transaction". No exclamation marks except genuine success ("Imported").
- **Numbers & money:** always currency-prefixed — **`AED 1,234.56`**. Tabular figures so columns align. Income is shown `+AED …`, expenses `−AED …` (true minus `−`, not hyphen). Hero balances drop the sign and color (neutral).
- **Empty states:** name the gap, then guide. Title "No transactions yet" → subtitle "Add your first transaction or import one from an SMS." → CTA.
- **Status language:** SMS states are one word each — **Linked**, **Parsed**, **Unparsed**. Banners are a short statement of fact + one action ("Auto-import is disabled" / "Enable").
- **Emoji:** **none.** This is a finance app; restraint signals trust. Iconography carries meaning instead.
- **Vibe examples:** "Net Worth", "Total Balance · June", "Most used", "Income & spending", "Paste an SMS". Short labels, one idea each.

---

## VISUAL FOUNDATIONS

**Color.** Green (`#0B7A5B`, `--accent`) is the brand and is reserved for **three meanings only**: money-positive (income), the single primary action per screen, and the active nav tab. Everything else is neutral gray or a *semantic financial hue*: expense coral `#E5544B`, savings blue `#2F6FED`, investment purple `#7C5CFC`. Backgrounds are a cool light neutral (`#F6F7F9`), **never** green-tinted. Eight category swatches (green, blue, orange, red, teal, purple, pink, gray) drive the category color picker. Dark theme lifts the green to `#21A87C` for contrast and uses near-black surfaces (`#0B0D12` bg, `#161A22` cards). See `tokens/colors.css`.

**Type.** **DM Sans** for all UI (geometric, calm). **Geist Mono** for every amount and SMS body — tabular figures keep money aligned in lists. Scale: hero 40 / display 32 / title 24 / section 18 / body 16 / label 14 / caption 13 / overline 11. Weights 400/500/600/700. Large display numbers get tight tracking (`-0.02em`). See `tokens/typography.css`.

**Spacing & layout.** 8dp base grid. **16dp** page margin, **16dp** card padding, **12dp** between cards, **8dp** under section titles. Touch targets ≥ 44dp. Edge-to-edge Android: a status bar (≈30dp) and a 64dp bottom nav frame the scroll area; the keyboard/gesture inset is respected on sheets. See `tokens/spacing.css`.

**Depth.** Depth comes from **surface contrast first** (white cards lift off the gray page), not heavy shadows. Flat cards use a 1px hairline ring (`--ring-card`); the headline card adds a soft `--shadow-card`. Real shadows are reserved for floating layers — bottom sheets/dialogs (`--shadow-md`), menus (`--shadow-lg`), and the primary FAB (`--shadow-accent`, a green-tinted glow). See `tokens/elevation.css`.

**Corners.** 12dp default card radius (`--r-md`), 16dp for hero cards & sheets (`--r-lg`), 24dp for the sheet top & large surfaces (`--r-xl`), 14dp for category icon tiles (`--r-tile`), full pill for buttons/chips/badges.

**Cards.** White surface, 12–16dp radius, hairline ring or soft shadow — never both heavy. Tinted cards (soft accent / warning / success background, no shadow) carry banners and "most used" highlights. No colored left-border accent stripes.

**Backgrounds & imagery.** No photography, no gradients-as-decoration, no patterns or textures. The single permitted gradient is the area-chart fill (accent → transparent) and the app-icon background. Surfaces are solid. Charts are clean inline SVG (area line, grouped bars, donut max-5, bar sparkline) using the semantic palette.

**Motion.** Calm and quick. Standard ease `cubic-bezier(0.2,0,0,1)`; durations 120/200/320ms. Sheets slide up with the emphasis ease. **No bounce** on functional UI. Buttons/chips give a subtle press feedback (`scale(0.97)`); icon buttons scale to 0.92. Charts grow width on mount. All motion collapses under `prefers-reduced-motion`.

**States.** Hover/press darken via the accent ramp (`--accent-hover`, `--accent-pressed`); pressed controls also shrink slightly. Focus shows a 3px soft ring (`--focus-ring`). Disabled drops opacity to ~0.45. Selected chips/tabs fill with accent and switch text to white. Active bottom-nav icon switches from **outlined → filled** and turns green.

**Transparency & blur.** Used sparingly: the modal scrim (`--scrim`), soft semantic "soft" tints (14% color mixes), and category avatar fills (`color-mix` 14–16%). No glassmorphism.

---

## ICONOGRAPHY

- **Icon system:** **Material Symbols Rounded** (Google), loaded as a variable icon font via `tokens/fonts.css`. Use `<span class="material-symbols-rounded">ligature</span>`; add `is-filled` for the filled variant (active nav, stars). This matches the Android/Material 3 platform and gives one consistent rounded family at any size. *(Substitution note: the brief suggested Material Symbols **or** Lucide; we chose Material Symbols Rounded for Android authenticity. If the production app standardizes on Lucide, swap the font include and the `IconButton`/icon usages.)*
- **Sizing:** 24px default in app bars/nav, 18–20px inline in chips/rows/buttons, 36px in empty-state badges.
- **Category icons** sit inside a **tinted rounded-square tile** in the category's color (`CategoryIcon`) — a signature Hisabak pattern; keep it everywhere a category appears.
- **No emoji. No unicode glyphs as icons.** Money signs are typeset text, not icons. The true minus `−` is used for negative amounts.
- **Brand mark:** a rounded "token" square with a rising sparkline (tracking + growth). Files in `assets/` — `logo-mark.svg`, `logo-wordmark.svg` (+ `-dark`), `app-icon.svg`. These are simple wordmark/mark constructions created for this system (no prior brand existed); treat as provisional and replace with final brand art when available.

---

## INDEX — what's in this repo

**Entry / tokens**
- `styles.css` — global entry; `@import`s everything. Consumers link **this** file only.
- `tokens/fonts.css` · `colors.css` · `typography.css` · `spacing.css` · `elevation.css` · `base.css`

**Foundations** (`guidelines/`, shown in the Design System tab)
- Colors: green scale, neutral scale, financial semantics, category palette, light/dark surfaces
- Type: display & titles, body & labels, amounts (mono)
- Spacing: scale, radii, elevation
- Brand: logo & mark

**Components** (`components/`, bundled to `window.HisabakDesignSystem_aa2548`)
- `core/` — Button, IconButton, Chip, Badge, StatusChip, Avatar, ProgressBar, AmountText
- `forms/` — Input, SearchBar, SegmentedControl
- `data/` — Card, StatCard, ListRow, CategoryIcon, CategoryTile, EmptyState
- `navigation/` — TopAppBar, BottomNav

Each component dir has `<Name>.jsx` + `<Name>.d.ts` + `<Name>.prompt.md`, and one `@dsCard` HTML.

**UI kit** (`ui_kits/mobile/`)
- `index.html` — interactive Android app with the real 5-tab nav (Dashboard, Transactions, SMS, Manage, Settings), light/dark toggle, the bell→Notifications and Settings→Backup flows, and toolbar previews for the Onboarding, Restore, and Sync full-screen flows.
- Tab screens: `Dashboard.jsx`, `Transactions.jsx`, `SmsInbox.jsx`, `Manage.jsx`, `Settings.jsx`.
- Sub-flows: `Backup.jsx`, `Notifications.jsx`, `BrandEdit.jsx`, `CategoryEdit.jsx`, `AddTransactionSheet.jsx`, `Onboarding.jsx`, `Restore.jsx`, `Sync.jsx`.
- Support: `charts.jsx`, `extras.jsx` (Toggle/SettingsRow/FormSection/RadioRow/HeroDisc), `mock.js`. See `ui_kits/mobile/README.md`.

**Assets** (`assets/`) — `logo-mark.svg`, `logo-wordmark.svg`, `logo-wordmark-dark.svg`, `app-icon.svg`.

**Skill** — `SKILL.md` (Agent-Skills compatible).

**Jetpack Compose (source of truth)** — these tokens are already translated into the Android app and live there, not in this skill. See `androidApp/src/main/java/com/hisabak/ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Motion.kt`, `HisabakTheme.kt`) and shared components in `ui/components/`. For production UI, use those directly; **`compose-bridge.md`** in this skill maps each token/component here to its Kotlin counterpart. The HTML/CSS kit is for throwaway visual mockups only. The repo-root `CLAUDE.md` carries the design rules for Claude Code working in the app codebase.

> Namespace for `@dsCard` HTML and UI-kit screens: `window.HisabakDesignSystem_aa2548`.
