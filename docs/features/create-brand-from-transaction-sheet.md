# Create a brand from the transaction sheet

## Requirement
In the transaction sheet, under brand, add a chip for creating a new brand.

## Spec
- **Goal:** entering a transaction for a brand that doesn't exist yet shouldn't force a trip
  to Manage — a "New brand" chip opens the brand editor and comes back with the brand
  selected and the typed entry intact.
- **In scope:** the trailing "New brand" chip in the sheet's brand row, draft preservation
  across the detour, auto-selecting the created brand (and following its category's type),
  and enabling the uncategorized-brand notice tap-through for new transactions.
- **Out of scope:** inline brand creation inside the sheet (a full screen can't stack on the
  bottom-sheet entry — the close/reopen detour is the established pattern).
- **Acceptance criteria:**
  - The brand chip row ends with a "New brand" chip (shown even when no brands match the
    type filter).
  - Tapping it closes the sheet, opens the brand editor (with the category chip row, "+ New
    category", and AI suggestion), and on save reopens the sheet with the brand selected.
  - Everything typed before the detour — amount, type, direction, note, date — is restored,
    for new and existing transactions alike.
  - The selected type follows the created brand's category type (a brand categorized as
    income flips the sheet to income); an uncategorized brand leaves the type alone.
  - Cancelling the editor reopens the sheet with input restored and nothing selected.
  - The uncategorized-brand notice is tappable in new transactions too (previously
    edit-only, because the detour used to discard typed input).
- **Edge cases:** a parked draft belongs to one sheet (`transactionId` match) — an
  unrelated sheet never consumes it; a stale `BrandCreatedBus` value can't exist (published
  only by the `forPick` nav entry, consumed by the reopened sheet).
- **Assumptions:** the draft is held in memory (a process death during the detour loses the
  unsaved input, as it always did).

## Design
- **`TransactionDraftBus`** (+ `TransactionEditDraft`) parks the sheet's typed state; the
  ViewModel publishes it on either detour intent (`CreateBrandRequested` /
  `EditBrandRequested`) and the reopened sheet's fresh ViewModel restores a matching draft
  instead of loading (edit) or starting blank (new).
- **`BrandCreatedBus`** mirrors `CategoryCreatedBus`; `BrandEditEffect.Saved` now carries
  the `BrandId`, and the nav layer publishes it only when `BrandEditKey.forPick`.
- **`ReopenSheet(transactionId?)`** replaces the bare string in HisabakRoot so the detour
  also works from the new-entry sheet (null id).
- **Test strategy:** ViewModel tests for park/restore, draft ownership, created-brand
  selection incl. type-follow; brand editor `Saved(id)` covered in its own test.
