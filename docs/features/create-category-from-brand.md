# Create a category from the brand editor

## Requirement
Create a new category from the brand editor: add a trailing "+ New" chip at the end of the
category chip row in BrandEditScreen that pushes the category editor (CategoryEditKey(null))
over the brand editor; when the new category is saved, return to the brand editor with the
created category auto-selected (one-shot bus pattern like BrandEditBus). Brand editor state
(typed name, selection) must survive underneath.

## Spec
- **Goal:** remove the dead-end where saving a brand requires a category that doesn't exist
  yet (saving a brand *requires* a category — `canSave` demands one), without leaving the
  brand editor or losing its state.
- **In scope:** the "+ New category" chip in the brand editor's category row, the pushed
  category editor, and auto-selecting the created category back in the brand editor.
- **Out of scope:** AI category suggestions (separate follow-up feature); editing an
  existing category from the brand editor; any change to the Manage-tab category flows.
- **Acceptance criteria:**
  - The brand editor's category row ends with a "New category" chip (plus icon).
  - Tapping it opens the full-screen category editor in "new" mode over the brand editor.
  - Saving the category returns to the brand editor with the new category selected; the
    typed brand name and other state are unchanged.
  - Cancelling the category editor returns with the brand editor unchanged (no selection
    change, no stale auto-select later).
  - Creating a category from the Manage tab never affects a brand editor.
- **Edge cases:**
  - Category created from Manage list (`forPick = false`): nothing is published to the bus.
  - Category editor cancelled after a previous successful pick: bus was already consumed;
    no re-selection.
  - The new category also appears in the chip row organically via `observeCategories`.
- **Assumptions:**
  - The chip reuses the existing `category_new_title` string ("New category" / "فئة جديدة")
    and `LeadingIconChip` with `HugeIcons.Add` — no new strings or components.
  - The chip row does not auto-scroll; the selected new chip may require a swipe on very
    long category lists. Acceptable for now.

## Design
- **Domain/model changes:** none — presentation + nav only.
- **Files to add/change:**
  - `nav/NavKeys.kt` — `CategoryEditKey` gains `forPick: Boolean = false`.
  - `feature/category/presentation/CategoryCreatedBus.kt` (new) — one-shot
    `MutableStateFlow<CategoryId?>` bus, `publish`/`consume`, Koin single in
    `CategoryModule`.
  - `feature/category/presentation/edit/CategoryEditContract.kt` — `Saved` becomes
    `data class Saved(val id: CategoryId)`.
  - `CategoryEditViewModel` — emits the created/updated id in `Saved`.
  - `CategoryEditRoute` — `onDone: (CategoryId) -> Unit`.
  - `HisabakRoot.kt` — category entry publishes to the bus on done when `forPick`;
    brand entry passes `onCreateCategory` that navigates to
    `CategoryEditKey(id = null, forPick = true)`.
  - `BrandEditViewModel` — collects the bus; on a pending id, selects it and consumes.
  - `BrandEditRoute` / `BrandEditScreen` — `onCreateCategory` threaded to the trailing chip.
- **Test strategy:**
  - `BrandEditViewModelTest`: a published category id becomes the selection and the bus is
    consumed; brand name input is untouched.
  - `CategoryEditViewModelTest`: `Saved` carries the created id (new) and the same id
    (edit).
- **Trade-offs / decisions:**
  - The publish decision lives at the nav layer keyed off `forPick`, not inside
    `CategoryEditViewModel` — the ViewModel stays ignorant of why it was opened, and a
    category created from the Manage tab can never leave a stale value in the bus for a
    later brand editor to mis-consume.
  - Bus over a nav-result API: Navigation 3 has no typed result channel; this mirrors the
    established `BrandEditBus` idiom.
