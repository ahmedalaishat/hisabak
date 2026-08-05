# Savings & investment withdrawals

## Requirement
Savings and investment transactions have a direction — deposit or withdrawal. Withdrawals are
stored as negative amounts so downstream sums net naturally: the dashboard Savings/Invest pills
and totalCash reflect withdrawals, and a brand's total shows the net outstanding balance (e.g.
lend 2k, get 1k back → brand shows 1k). Includes: a deposit/withdrawal toggle in the transaction
edit sheet shown only for savings/investment-type categories; amount input and validation accept
the withdrawal case (stored as negative amountMinor, entered as positive with the toggle);
re-editing a withdrawal preserves the sign and toggle state; brand/category list totals no longer
hide zero or negative totals (a fully-repaid loan shows 0); transaction list rows use proper
Savings/Investment tones and sign instead of green '+' for these rows.

## Spec

- **Goal:** savings and investment are *buckets* money moves in and out of; today they only grow.
  A withdrawal entry releases money back to cash and nets against the bucket's (and its brand's)
  running total — which also makes person-brands under a savings category work as loan ledgers.
- **In scope:**
  - Deposit/withdrawal segmented toggle in the transaction edit sheet, visible only when the
    selected type is Savings or Investment (defaults to deposit).
  - Withdrawals persist as negative `amountMinor`; the amount field itself stays positive-only.
  - Re-editing a withdrawal shows the positive amount with the toggle on Withdrawal; saving
    keeps the sign.
  - Switching the type segment to Income/Expenses resets the toggle (an expense must never save
    negative).
  - Brand & category list rows show their total whenever the row has ≥1 transaction — including
    0 (fully repaid) and negative (over-withdrawn, rendered with a true minus).
  - Transaction list rows for savings/investment categories render in the Savings/Investment
    semantic colors with a sign that follows the stored direction (+ deposit, − withdrawal).
  - Withdrawal hero amount shows a leading − so the direction is visible while editing.
- **Out of scope:** a per-person loan screen or outstanding-balance view; refund semantics for
  income/expense types (`TransactionKind`, parked); SMS/AI capture of withdrawals (bank debits
  keep importing as positive entries); budgets (already exclude savings).
- **Acceptance criteria:**
  1. Saving a Savings-type transaction with the toggle on Withdrawal stores negative
     `amountMinor`; deposit stores positive. Same for Investment.
  2. Editing an existing negative transaction loads the toggle as Withdrawal and the amount
     field as the positive value; saving unchanged keeps it negative.
  3. Selecting Income or Expenses after arming the toggle saves a positive amount.
  4. Dashboard: a 2,000 deposit + 1,000 withdrawal in a savings category yields totalSavings
     1,000, and totalCash is only 1,000 lower than without any savings activity.
  5. Brand list: a brand with +2,000 and −1,000 shows 1,000; with +2,000 and −2,000 shows 0
     (not hidden). Categories list behaves the same.
  6. Transaction list: savings rows render with the savings tone; a withdrawal shows −, a
     deposit +. Investment likewise.
- **Edge cases:**
  - Zero/empty amount still rejected ("Enter a positive amount") regardless of the toggle.
  - Uncategorized brands (SMS-captured) have no type, so the toggle can't apply; their rows
    stay neutral/unsigned.
  - The saved sign follows the *brand's* resolved type, not the type filter: re-saving a
    withdrawal whose brand was later re-categorized to None keeps it negative (unknown ≠
    expense); only a definitively income/expense brand forces positive.
  - Dashboard Categories tab gates rows/charts on period *activity* (any non-zero day), not the
    net — a fully-repaid category still shows its card at 0. Trend badges skip non-positive
    baselines (a percent change off a negative base is meaningless).
  - `MoneyText` renders a true − before the glyph for negative totals (a bucket can now net
    negative in a period).
  - Brands with zero transactions show no total at all (unchanged) — only rows with activity
    show 0.
  - `AmountText` callers that pass negative values with `Auto` tone are unaffected (Auto
    already resolves negatives to Expense with −).
- **Assumptions:**
  - Labels "Deposit" / "Withdrawal" (ar: «إيداع» / «سحب») read correctly for both the
    savings and the lend/repay use cases (lend = deposit into the loans bucket).
  - Analytics keeps logging the coarse magnitude bucket (already `abs`-based); no new event —
    direction is not tracked.

## Design

- **Domain/model changes:** none to entities — `Transaction.amount` already carries sign
  (`Money.amountMinor: Long`), Room/backup serialize it untouched, and every downstream
  aggregation (`GetDashboardMetricsUseCase`, brand/category list `buildRows`) is a raw sum that
  nets automatically. One addition: `CategoryType.hasDirection` (true for SAVINGS/INVESTMENT).
- **Files to add/change:**
  - `feature/transaction/presentation/edit/TransactionEditContract.kt` — `isWithdrawal` state +
    `DirectionChanged` intent.
  - `.../edit/TransactionEditViewModel.kt` — toggle handling, sign applied at save, sign split
    back out on load (`formatAmountInput` now abs), toggle reset on type change.
  - `.../edit/TransactionEditScreen.kt` — direction segmented control, − prefix on the hero.
  - `.../list/TransactionListScreen.kt` — savings/investment tones, signed value into
    `AmountText`.
  - `ui/components/HisabakComponents.kt` — `AmountText` sign follows the value's sign for
    non-expense tones (− for negatives; Neutral shows − only, never +).
  - `feature/brand/presentation/list/BrandListScreen.kt`,
    `feature/category/presentation/list/CategoryListScreen.kt` — totals gate on
    `transactionCount > 0` instead of `totalMinor > 0`.
  - `composeResources/values{,-ar}/strings.xml` — direction labels.
  - `feature/category/domain/CategoryType.kt` — `hasDirection`.
- **Test strategy:** `TransactionEditViewModelTest` (withdrawal saves negative, load round-trip,
  type-switch reset, zero still rejected), `BrandListViewModelTest` (netting + zero total kept),
  `GetDashboardMetricsUseCaseTest` (savings pill and cash net after a withdrawal).
- **Trade-offs / decisions:** signed amounts over a `TransactionKind` column — no schema
  migration, every existing sum nets for free, and it doesn't preclude introducing
  `TransactionKind` later for income/expense refunds (the parked refunds design). The amount
  input stays positive-only; direction is a separate explicit control rather than a typed minus.
