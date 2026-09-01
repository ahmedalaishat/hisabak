# Compose bridge — design system → Kotlin

**The Hisabak Android app (Jetpack Compose) is the source of truth for production UI.** The
CSS tokens and React/HTML components in this skill are the *spec and mockup kit*; this file
maps each one to the real Kotlin it became. When building production UI, use the Kotlin
below directly — never hand-write CSS or port JSX.

App theme: `shared/src/commonMain/kotlin/com/hisabak/ui/theme/` · shared components:
`shared/src/commonMain/kotlin/com/hisabak/ui/components/` (Compose Multiplatform — keep them
multiplatform-safe) · charts: the Vico charts (`VicoCharts.kt`, on
`com.patrykandpatrick.vico:multiplatform-m3`) and the pure-Canvas `DonutChart` both live in
`shared/src/commonMain/kotlin/com/hisabak/feature/dashboard/presentation/components/`.

Wrap UI in `HisabakTheme { … }`. Read finance colors via `HisabakTheme.colors`; standard
roles via `MaterialTheme.colorScheme`. Never hardcode hex.

## Color

| Design token (CSS) | Compose |
|---|---|
| `--green-500 #0B7A5B` (brand/primary) | `MaterialTheme.colorScheme.primary` (`Green500`) |
| `--accent-hover` / `--accent-pressed` | `HisabakTheme.colors.accentHover` / `.accentPressed` |
| `--income` | `HisabakTheme.colors.income` (+ `.incomeSoft`) |
| `--expense` (coral) | `HisabakTheme.colors.expense` (+ `.expenseSoft`) |
| `--savings` (blue) | `HisabakTheme.colors.savings` (+ `.savingsSoft`) |
| `--investment` (purple) | `HisabakTheme.colors.investment` (+ `.investmentSoft`) |
| `--warning` / `--info` | `HisabakTheme.colors.warning` / `.info` (+ `…Soft`) |
| `--cat-green … --cat-gray` (8 swatches) | `HisabakTheme.colors.catGreen/catBlue/catOrange/catRed/catTeal/catPurple/catPink/catGray` |
| page bg / cards / surfaces | `MaterialTheme.colorScheme.background` / `.surface` / `HisabakTheme.colors.surfaceSunken` |
| text primary / secondary / tertiary | `colorScheme.onSurface` / `.onSurfaceVariant` / `HisabakTheme.colors.textTertiary` |
| `--ring-card` / strong border | `colorScheme.outlineVariant` / `HisabakTheme.colors.borderStrong` |

## Type

| Design | Compose |
|---|---|
| DM Sans UI scale (hero/display/title/section/body/label/caption/overline) | `MaterialTheme.typography.*` (built by `hisabakTypography(family, arabic)`); **Arabic → Tajawal**, selected by locale in `HisabakTheme` |
| **Amounts — Geist Mono, tabular** (Arabic figures → Tajawal) | `HisabakType.amount` / `HisabakType.amountLarge` / `HisabakType.amountHero` (composable getters over `LocalHisabakFonts`) |

Fonts are **bundled OFL TTFs** in `shared/src/commonMain/composeResources/font/` (DM Sans
400/500/600/700, Geist Mono 400/500/600, Tajawal 400/500/700), loaded via CMP resources —
no downloadable-fonts provider.

Money renders the **dirham glyph** (never the literal text "AED"), tabular figures; income
`+`, expense true-minus `−`, both colored; hero balances neutral/unsigned. Always use
`MoneyText` / `AmountText` / `TrailingAmount` (they apply the mono style + `DirhamGlyph`) —
never hardcode `"AED …"` in a `Text`. Amounts display **compactly** via `compactAmount` /
`compactAmountMinor` (thousands `K`, millions `M`, 2 decimals; under 1,000 exact); only the
transaction edit input stays exact. An abbreviated amount is tappable — it swaps to the full
figure in place for a few seconds — so render money through `MoneyText` / `AmountText` /
`MoneyStatCard`, never a hand-formatted `Text` that loses that escape hatch.

## Spacing · radius · sizing

| Design | Compose |
|---|---|
| 8dp grid `--space-1…10` (2/4/8/12/16/20/24/32/40/48) | `Spacing.s1 … Spacing.s10` |
| radii xs/sm/md/lg/xl (6/8/12/16/24) | `MaterialTheme.shapes.extraSmall/small/medium/large/extraLarge` |
| pill / category tile (14) | `PillShape` / `TileShape` |
| icon & control sizes | `Sizing.*` |

## Components (design name → Compose composable in `ui/components/`)

| Design system | Compose |
|---|---|
| `Button` | `HisabakButton`, `PrimaryPillButton`, `CreateActionButton` (FAB/primary) |
| `Chip` / `SegmentedControl` | `FilterPill`, `ColoredFilterChip`, `LeadingIconChip`, `PeriodChipRow` |
| `Badge` / `StatusChip` | `Badge` / `StatusChip` |
| `Avatar` | `Avatar` |
| `ProgressBar` | `ProgressBar` |
| `AmountText` | `AmountText`, `MoneyText`, `TrailingAmount` |
| `Input` / `SearchBar` | `SearchField` (free text: Material `OutlinedTextField`) |
| `Card` | `SurfaceCard` |
| `StatCard` | `StatCard` (string value), `MoneyStatCard`, `IncomeStatCard`, `ExpensesStatCard` (all three take `amountMinor`) |
| `ListRow` | `ListRow` |
| `CategoryIcon` / `CategoryTile` | `IconTile` / `CircleIconTile` |
| `EmptyState` | `EmptyStatePanel` |
| `TopAppBar` | `HisabakTopBar`, `DetailTopBar` |
| `BottomNav` | `HisabakBottomNav` |
| banners (promo) | `GradientBanner`, `DarkPromoBanner` |
| "most used" highlight card | `MostUsedCard` (tinted `SurfaceCard` + icon + trailing slot) |
| charts (area / bars / donut / sparkline) | `AreaLineChart`, `BarSparkline`, `DonutChart` (Vico-backed) |
| loading skeletons | `SkeletonBox`, `SkeletonRow`, `SkeletonCard`, `SkeletonRowList` |

## Rules of thumb

- **Reuse, don't fork.** Extend an existing composable to match the spec rather than writing
  a new one. Read each design component's `.prompt.md` (what/when) + `.d.ts` (props) first.
- **Both themes always.** Every screen must look right in light and dark — `HisabakTheme`
  handles both; never branch on theme by hand.
- **Green is meaningful, never decorative** — income, the one primary action, the active nav
  tab. Backgrounds stay neutral.
- For a quick visual before coding, render the HTML kit (`ui_kits/mobile/`) as a throwaway
  mock; then build the real screen in Compose using the table above.
