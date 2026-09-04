# Insights review (layer 1 of AI insights)

## Requirement
Insights review, layer 1 of `docs/features/ai-insights.md`: a deterministic (no AI, no server, no
opt-in) monthly review — an `InsightsSummary` built from the dashboard aggregates, pure
`deriveInsights` rules (largest expense category, largest change vs prior period, categories
over/under their limit, savings-rate trend, uncategorized spend), and a "Monthly review" card on
the dashboard opening an insights screen of cards that deep-link to the filtered transaction list.
Ships to every user. The parent spec is the source of truth; this PR's doc scopes layer 1 only.

## Spec
- **Goal:** the "so what?" over numbers the dashboard already shows — *what changed, and what
  needs attention* — for every user, with nothing leaving the phone. This is also the foundation
  the AI layers reuse: the summary they will send is this summary.
- **In scope:**
  - `InsightsSummary`, derived purely from the existing `DashboardSnapshot` for the dashboard's
    selected period (no new aggregation — the snapshot already carries per-category totals, prior
    period totals, trend percentages, limits, and the uncategorized bucket).
  - Six deterministic rules, each a pure function: **over limit**, **near limit** (≥ 80%),
    **largest change vs prior** (≥ 25% and material — see edge cases), **largest expense
    category**, **savings rate** (with its change when the prior period is derivable), and
    **uncategorized spend**.
  - A **Review card** on the dashboard's Summary tab, under the cash / savings / investment pills: the period label,
    the top three insights as one-line rows, and **See all**.
  - An **Insights screen** (full-screen child, back arrow) listing every insight as a card;
    tapping a category-bound insight opens the transaction list filtered to that category; the
    uncategorized insight opens the uncategorized filter. Real empty state.
  - English + Arabic strings. Amounts through `MoneyText`; deltas as localized percentages.
- **Out of scope:** the AI narrative and Ask layers (the parent spec's PRs 2–3); the
  "unusually large transaction" rule (needs per-brand history the snapshot does not carry —
  layer-1 follow-up); the dormant `feature/budget` domain (no UI or DI wiring exists; limits
  here are `CategoryLimit`, which the dashboard already surfaces); persisting insights (they
  are derived on the fly and cheap); notifications.
- **Acceptance criteria:**
  1. With expenses in two categories and a limit exceeded in one, the review lists **Over limit**
     first, then the change/largest-category insights, ordered by severity then magnitude.
  2. A category at 80–100% of its limit yields **Near limit**; below 80% yields nothing for that
     rule; over 100% yields **Over limit** and *not* Near limit.
  3. A category up ≥ 25% vs the prior period yields a **change** insight with the correct signed
     percentage; a change on an immaterial base (below 5% of total expense in both periods) does
     not.
  4. The **largest expense category** insight carries that category and its share of expense; it
     is absent when there is no expense.
  5. **Savings rate** is `(income − expense) / income`; when income is zero the rule yields
     nothing; when the prior period is derivable the insight carries the change in percentage
     points.
  6. **Uncategorized** yields an insight iff `uncategorizedCount > 0`, carrying count and total.
  7. With no transactions in the period the review is empty and the dashboard card is hidden;
     the insights screen shows the empty state.
  8. The dashboard card shows at most three insights; the screen shows all.
  9. Tapping a category insight requests `TransactionListFilterRequest.ByCategory(id)` and
     navigates to Transactions; the uncategorized insight requests `Uncategorized`.
  10. `InsightsSummary` contains no transaction rows and no notes — structurally: the type has no
      such fields (asserted by the summary test's shape, and it is what the AI layers will send).
- **Edge cases:** `SummaryPeriod.ALL` has no prior period — change and savings-delta rules
  degrade to absent, the rest still fire. A limit that begins mid-period applies from its
  effective month (the snapshot's per-bucket limit list; the review takes the limit in force at
  the period's last bucket). Zero prior spend with current spend is "new spend", not an infinite
  percentage — reported as a change with `deltaPct = null` and the amount. Income categories are
  excluded from expense rules. Negative savings rate (spent more than earned) is a **Warning**.
- **Assumptions:** the review follows the dashboard's selected period rather than being pinned
  to the calendar month — a "This year" review is free and useful, and the card title carries the
  period label so it never reads as a month when it is not. Thresholds 25% / 80% / 5% are
  constants in the rules file, chosen to avoid noise rather than measured; they are the obvious
  first thing to tune. Severity maps to the existing semantic colours (`warning`, `info`,
  `expense`, `income`) — no new tokens. The card sits under the cash / savings / investment pills — the hero and pills are one
  unit (net worth and its decomposition), and the review is commentary on the *flow* that
  follows, so it reads as the bridge between position and detail rather than splitting a total
  from its parts. The savings row uses the same bank glyph and blue as the savings pill.

## Design
- **Domain (`feature/insights/domain/`, pure, commonMain):**
  - `InsightsSummary` + `InsightsSummary.from(snapshot: DashboardSnapshot, period: SummaryPeriod)`
    in `InsightsSummary.kt`: `period`, `income`/`expense` (minor), `priorIncome`/`priorExpense`
    (derived from the snapshot's trend percentages: `prior = current / (1 + pct/100)`, null when
    the pct is null), `categories: List<CategorySpend>` (`id, name, color, icon, spentMinor,
    priorMinor?, limitMinor?, shareOfExpense`) for expense-type categories, `uncategorizedMinor`,
    `uncategorizedCount`. Ordering is deterministic (by id) so the later AI layers get a
    byte-stable prompt prefix.
  - `Insight(type: InsightType, severity: Severity, category: InsightCategory?, amountMinor: Long?,
    deltaPct: Double?, share: Double?, count: Int?)` — `InsightCategory(id, name, color, icon)` is
    carried whole so the screen needs no second lookup (the snapshot's own `CategoryShare` sets
    that precedent) — and
    `deriveInsights(summary): List<Insight>` in `DeriveInsights.kt`. No display strings in the
    domain — the screen renders text from `type` + fields via string resources, which keeps the
    rules pure and the copy localizable. `id` is `"$type:$categoryId"` so list keys are stable.
- **Presentation:**
  - `DashboardUiState.review: List<Insight>` — `DashboardViewModel` maps each snapshot through
    `deriveInsights(InsightsSummary.from(...))` in the same `onEach`. `ReviewCard` in
    `DashboardScreen`'s Summary tab (a `SurfaceCard`: a title row styled like the net-worth
    hero's label — `labelMedium`, `onSurfaceVariant` — with the period label and a **See all**
    action, then up to three `InsightRow`s — `IconTile` in the
    category tint via `tintPairForColor`/`iconForKey`, or a semantic icon for non-category
    insights, plus the one-line text). Hidden when `review` is empty. The **whole card** taps
    through to the review — it is a teaser, and per-insight deep links live on the screen; rows
    individually clickable inside a clickable card would nest two targets and send a tap on
    "Dining over limit" somewhere other than dining.
  - `feature/insights/presentation/`: `InsightsContract` (`InsightsUiState(insights, period,
    isLoading)`), `InsightsViewModel(getMetrics, period)` — re-derives from
    `GetDashboardMetricsUseCase(flowOf(period))` rather than sharing state through the nav
    key, so the key stays plain data — `InsightsRoute`, `InsightsScreen` (`LazyColumn` of
    `SurfaceCard`s; `EmptyStatePanel` when empty).
  - Shared `InsightText` composable maps `(type, fields)` → title/detail strings, used by both
    the card rows and the screen cards so copy lives in one place.
  - **Nav:** `data class InsightsKey(val period: String) : NavKey` (period name, like the other
    keys carry raw strings). `HisabakRoot`: added to the `fullScreen` predicate, `screenName =
    "insights"`, a `DetailTopBar` branch, and an `entry<InsightsKey>(fullScreenTransition())`
    whose `onOpenCategory` mirrors the existing ByCategory deep link (`filterBus.request(...)`;
    `navigator.navigate(TransactionsKey)`) and `onOpenUncategorized` mirrors the dashboard's.
  - **DI:** `insightsModule` with the `InsightsViewModel` (parameterised by period), added to
    `sharedModules`.
  - **Strings:** `insights_*` in `values/` and `values-ar/` — title, period-suffixed card title,
    "See all", per-type templates with positional args (`%1$s` category, `%2$s` amount/percent —
    args pre-localized via `localizedFormatArg`/`localizeDigits`), the empty state.
  - **Analytics (PII-free):** `InsightsOpened(count)` on the screen, `InsightTapped(type)`.
- **Files:** `feature/insights/domain/{InsightsSummary,DeriveInsights}.kt`,
  `feature/insights/presentation/{InsightsContract,InsightsViewModel,InsightsRoute,InsightsScreen,InsightText}.kt`,
  `feature/insights/InsightsModule.kt`, `di/SharedModules.kt`, `nav/NavKeys.kt`, `HisabakRoot.kt`,
  `feature/dashboard/presentation/{DashboardContract,DashboardViewModel,DashboardScreen,DashboardRoute}.kt`,
  `core/domain/analytics/AnalyticsEvent.kt`, both `strings.xml`, `CHANGELOG.md`, `CLAUDE.md`.
- **Test strategy (commonTest, kotlin-test):** `InsightsSummaryTest` builds the summary through
  the real `GetDashboardMetricsUseCase` over `TestData` (totals, prior derivation, limit
  mapping, expense-only categories, deterministic ordering) — AC 10 by construction.
  `DeriveInsightsTest` covers every rule and AC 1–8: each threshold boundary, the immaterial-base
  filter, zero-prior "new spend", zero-income savings, negative savings as Warning, the ALL-period
  degradation, ordering, and the empty review. `InsightsViewModelTest` covers loading and the
  period taken from the key. `DashboardViewModelTest` gains a case asserting `review` is derived
  alongside the snapshot. Compose UI is out of test scope per CLAUDE.md.
- **Trade-offs / decisions:** deriving from `DashboardSnapshot` instead of re-aggregating means
  the review can never disagree with the dashboard and costs one pure pass; the cost is that
  rules are limited to what the snapshot carries (hence no per-brand outlier rule yet). Two
  ViewModels compute the same pure function rather than sharing state, because the alternative
  — a bus or a fat nav key — buys nothing for a computation this cheap. Domain carries no copy so
  the six rules stay testable without resources and Arabic gets the same treatment as English.
