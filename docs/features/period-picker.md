# Period picker — any month, year, or range, shared across tabs

## Spec

**Problem.** The dashboard offered five fixed windows (this/last month, this/last year, all time),
so history older than last year was reachable only through "All", which bucketed years of data
into a row of thin monthly bars. Transactions had two date controls that could disagree — a period
for the totals and a rolling 7/30/90-day filter for the rows — and each tab kept its own period.

**Behaviour.**
1. Dashboard, Insights, and Transactions show one period bar: `‹ <period> ›` with a line under it
   naming the comparison window ("Compared with August 2026"; for all time, its span).
2. ‹ › step to the equal-length window before/after. › is disabled until the next window has
   started; ‹ is disabled once the window starts at or before the first transaction. All time has
   no arrows.
3. Tapping the title opens a sheet: **Month** (year stepper + 12-month grid, future months
   disabled, a dot on this month, quick picks: this month, last month, this year, last 12 months),
   **Year** (every year from the first transaction to now), **Custom** (last 7/30/90 days presets +
   a date-range picker capped at today, applied with "Show …"), **All** (applies at once).
4. The selection is shared: changing it on any of the three tabs changes it on all of them. It is
   not persisted; a cold start opens on this month.
5. Charts pick their bucket from the window length: ≤ 62 days by day, ≤ 186 by week (seven-day runs
   from the window start), ≤ 3 years by month, longer by year. Buckets stop at today. The Trends
   bar chart groups daily windows by week.
6. Category limits over a window are prorated by days per month; a calendar month uses its cap.
7. Transactions: the list and its totals cover the period; the Date filter chip is removed; each
   day header shows the day's net (income − expenses), neutral-toned. Manage's "view
   transactions" for a brand/category widens the shared period to All.
8. Analytics: `dashboard_period_changed(period)` carries the kind (`month|year|custom|all`), never
   dates.

## Design

- `SummaryPeriod` (`core/common`) becomes a sealed type of absolute windows with `window`,
  `previous`/`next`, `canStepBack/Forward`, `encode`/`decode` (nav keys carry strings), `kind`, and
  `wireName(today)` (relative names kept for the server prompt where they apply). Bucketing helpers
  (`granularityFor`, `bucketStart(s)`, `bucketEnd`) sit beside it, pure.
- `PeriodSelection` (`core/presentation`, Koin single) holds the shared `StateFlow`; it replaces
  `InsightsPeriodBus`. Each ViewModel observes it and writes to it on `PeriodChanged`.
- `GetDashboardMetricsUseCase` buckets by granularity over the window (All: first transaction →
  today) and adds `granularity`, `periodLimitByCategory`, `earliestActivity`, `asOf` to the
  snapshot. `limitBetween` (pure) prorates caps. `InsightsSummary` takes its limits from the
  snapshot and its `periodName` from `wireName(asOf)`.
- UI: `PeriodBar` + private `PeriodSheet` in `ui/components/PeriodPicker.kt` (replaces
  `PeriodChipRow`). `LocalizedDateFormatter.monthYearLong` added on all three implementations.
  Digits in the label are localized for Arabic.
- Tests: `SummaryPeriodTest` (windows, stepping, encode/decode, wire names, granularity, buckets),
  `PeriodLimitTest`, new cases in `GetDashboardMetricsUseCaseTest`, `DashboardViewModelTest`, and
  `TransactionListPeriodTest`.
