# AI-suggested category in the brand editor

## Requirement
When creating (or categorizing) a brand, the app suggests a category — either one of the
user's existing categories or a proposed new one — using the platform's on-device model.
Confirm-first: the suggestion is a chip the user taps; nothing is applied automatically.

## Spec
- **Goal:** cut the "which category?" decision for new brands: the model either points at
  the existing category that fits or drafts a new one (name, type, color, icon) that the
  user confirms through the normal category editor.
- **In scope:** the suggestion port + platform impls (Gemini Nano / Foundation Models),
  the suggestion chip in the brand editor, and the prefilled category editor for "new"
  suggestions (rides the `forPick`/`CategoryCreatedBus` plumbing from
  `create-category-from-brand`).
- **Out of scope:** suggestions anywhere else (AI-parse confirm flow, Manage tab), batch
  categorization of existing brands, any cloud inference.
- **Acceptance criteria:**
  - Typing a brand name (≥ 2 chars, no category selected) triggers one suggestion request
    after a debounce; a subtle progress hint shows while it runs.
  - An "existing" suggestion renders as a tappable chip ("Suggested: Groceries"); tapping
    selects that category.
  - A "new" suggestion renders as "New: Pharmacy"; tapping opens the category editor
    prefilled (name/type/color/icon) in `forPick` mode — saving returns with it selected.
  - On devices without the on-device model, nothing AI-related ever appears.
  - Editing the name clears a stale suggestion; a selected category hides the suggestion.
  - The model's output is sanitized in common code: claimed existing names snap to real
    categories (exact → Levenshtein ≤ 2), invalid type/color/icon fall back to
    expenses/gray/wallet, a "new" name that actually matches an existing category becomes
    an Existing suggestion.
- **Edge cases:** model returns junk/nulls → no suggestion, no error surfaced; suggestion
  arrives after the user already picked a category → hidden; rapid typing → previous
  in-flight request cancelled by the debounce job.
- **Assumptions:**
  - Suggestion fires automatically on name-settle (700 ms debounce) rather than behind a
    button — one inference per settled name is acceptable for on-device models.
  - Analytics: `ai_category_suggested` / `ai_category_accepted` with only a
    `kind` (existing|new) param, `ai_category_failed` with a coarse reason — no names
    (strict no-PII).
  - Chip labels use "Suggested: X" / "New: X" (en) — no sparkle icon exists in HugeIcons
    and none is added.

## Design
- **Domain (`feature/brand/domain/ai/`):** `AiCategorySuggester` port (`isReady()`,
  `suggest(brandName, options): AiRawCategorySuggestion?` — primitive-typed for the Swift
  bridge), pure `sanitizeCategorySuggestion(raw, categories): CategorySuggestion?`
  (`Existing(category)` | `New(name, type, color, icon)`), and
  `SuggestBrandCategoryUseCase` (availability gate + analytics + sanitize).
- **Presentation:** `BrandEditViewModel` debounces name changes into the use case;
  contract gains `suggestion`/`isSuggesting` state and `SuggestionAccepted` intent plus an
  `OpenCategoryEditor(prefill)` effect. `BrandEditScreen` renders the hint/chip under the
  category row only when no category is selected.
- **Nav:** `CategoryEditKey` gains optional prefill fields (name/type/color/icon strings);
  `CategoryEditRoute`/`CategoryEditViewModel` apply them as the initial state for a new
  category. Reuses `forPick` + `CategoryCreatedBus` for the return trip.
- **Android:** `GeminiNanoCategorySuggester` (androidApp, ML Kit GenAI Prompt API, own
  client, same availability/download semantics as `GeminiNanoSmsParser`), bound in
  `PlatformModule`.
- **iOS:** `AiCategoryBridge` seam (iosMain) + `IosAiCategorySuggester`;
  `FoundationModelsCategorySuggester.swift` (@Generable); `startIosApp`/
  `iosPlatformModule` gain the bridge parameter, `iOSApp.swift` injects it.
- **Test strategy:** sanitize rules table-tested; use case with a fake suggester
  (unavailable, junk, existing, new paths + analytics); ViewModel debounce/accept/clear
  behavior on virtual time. Fake lives in `testutil`.
- **Trade-offs:** the port lives under `feature/brand` (its only consumer) and defines its
  own boolean availability instead of importing the SMS parser's enum — no brand→sms
  domain coupling. Existing-name snapping reuses the same technique as
  `canonicalizeBrand` but stays local to the sanitizer for the same reason.
