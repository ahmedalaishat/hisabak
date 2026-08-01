# Smart parsing (free-text quick entry)

## Requirement
Smart parsing: a 'describe it' free-text field in the add-transaction sheet (shown only when
on-device AI is available) that parses the user's natural-language input — e.g. '100 at noon',
'lunch 45 yesterday' — via the platform AI into a pre-filled transaction: amount into the hero
field, brand chip selected (brand-aware canonicalization against existing brands), date resolved
including relative dates ('yesterday'), type derived from the brand's category, currency falling
back to the app currency. Reviewing the pre-filled sheet and saving is the confirmation
(confirm-first preserved, no auto-create). Shares the AI engine with SMS parsing via a second
port method with its own prompt and a free-text sanitize variant (wider back-dated plausibility
window, nothing in the future). Unknown brands offer 'create brand X'. Hidden entirely on
devices without on-device AI. PII-free smart_parse_* analytics events.

## Spec

- **Goal:** the fastest possible manual entry — type (or dictate) "lunch 45 yesterday" and
  review a filled-in sheet instead of tapping through amount/brand/date one by one. This is the
  right home for inputs like "100 at noon", which are quick notes, not SMS parser templates.
- **In scope:**
  - A "describe it" text field at the top of the **new**-transaction sheet, visible only when
    the on-device model reports `Ready` (Gemini Nano / Apple Foundation Models).
  - Parse action fills: hero amount, brand chip (canonicalized to an existing brand), type
    segment (from the matched brand's category), and date. Nothing is saved — Save is the
    confirmation.
  - Relative dates resolve ("yesterday", "last friday"): the free-text prompt carries today's
    date, unlike the SMS prompt which forbids date invention.
  - Free-text sanitize variant: brand + positive amount required; non-ISO currency falls back
    to the app currency; dates accepted within [now − 365d, end of today], anything else → now.
  - Unknown brand → a "Create ‹name›" chip in the brand row; tapping creates an uncategorized
    brand (existing `FindOrCreateBrandUseCase`) and selects it.
  - Inline failure line when the model returns nothing usable; the field stays for a retry.
  - `smart_parse_attempted` / `smart_parse_succeeded` (bucketed amount) / `smart_parse_failed`
    (reason enum) analytics — never the input text.
- **Out of scope:** editing existing transactions (the sheet already holds values); auto-saving;
  SMS/inbox changes; template management; voice UI (keyboard dictation works for free).
- **Acceptance criteria:**
  1. Parsing "100 at noon" with an existing brand "Noon" pre-fills amount 100, selects the Noon
     chip, and switches the type segment to Noon's category type.
  2. A model date older than a year or in the future is replaced with now; "yesterday" style
     dates inside the window are kept.
  3. A model currency that isn't a 3-letter ISO code falls back to the app currency.
  4. An unknown brand pre-fills amount/date and exposes "Create ‹name›"; tapping it creates the
     brand and selects it.
  5. When the parser is `Unavailable`, the field is absent and the sheet behaves exactly as
     before; parse failures set a visible error and change no fields.
  6. Analytics log attempted/succeeded/failed with no raw text or note content.
- **Edge cases:**
  - Model returns no amount or no brand → failure ("incomplete"), fields untouched.
  - Canonicalization ties go to the most-used brand (usage-ordered list, same as SMS).
  - Parse over already-edited fields overwrites them (the user explicitly asked to parse).
  - The describe-it text stays after a successful parse so it can be tweaked and re-parsed.
- **Assumptions:**
  - The field appears only for new transactions (`isNew`), not when editing.
  - The created brand is uncategorized (same as SMS-captured brands); the type segment is left
    where it is in that case.
  - `AiSmsParser` keeps its name despite now parsing non-SMS text — the port rename isn't worth
    the churn; the KDoc says it covers both.

## Design

- **Domain/model changes:**
  - `AiSmsParser` (port) + `parseFreeText(text, knownBrands, todayIso): AiParsedSms?`.
  - `AiSmsBridge` (iOS seam) + matching method; `FoundationModelsSmsParser.swift` implements it
    with its own instructions + a second `@Generable` struct (relative-date guidance).
  - `GeminiNanoSmsParser` + a free-text prompt (expense text, today context, no-future rule).
  - `canonicalize`/`levenshtein` extracted from `SuggestAiParseUseCase` to
    `feature/sms/domain/ai/BrandCanonicalizer.kt` (shared, behavior unchanged).
  - New `ParseSmartInputUseCase` (`feature/transaction/domain/usecase/`): availability gate,
    `namesByUsage(50)`, parse, sanitize, brand-id resolution (case-insensitive exact match on
    the canonical name). Returns `SmartParse(brandId?, brandName, amount, occurredAt)`.
  - `AnalyticsEvent`: `SmartParseAttempted` / `SmartParseSucceeded(amount)` /
    `SmartParseFailed(reason)`.
- **Files to add/change:** the above, plus `TransactionEditContract/ViewModel/Screen/Route`
  (smart state + intents + UI), `TransactionModule` (DI), CMP strings (en+ar),
  `testutil/FakeAiSmsParser`.
- **Test strategy:** `ParseSmartInputUseCaseTest` covers criteria 1–3 + failure reasons and the
  analytics PII guard; `TransactionEditViewModelTest` covers 1, 4, 5 at the sheet level;
  existing `SuggestAiParseUseCaseTest` guards the canonicalizer extraction.
- **Trade-offs / decisions:** brand creation happens on the explicit chip tap (not deferred to
  Save) — matches how SMS capture already creates uncategorized brands, keeps save simple, and
  a stray brand is harmless and deletable. The sheet, not a separate suggestion card, is the
  confirm surface — reviewing prefilled fields is strictly better UX than a second dialog.
  The parse itself never links a brand by containment (only an exact canonical-name match gets
  a `brandId` — a fuzzy link could silently differ from what's shown), but the create chip
  reuses `FindOrCreateBrandUseCase`, whose containment rule may resolve to an existing brand
  ("Create ‹Noon›" → existing "Noon Food") — that outcome is visible (the real brand's chip
  becomes selected) and prevents near-duplicate brands, consistent with the SMS confirm flow.
  Model-controlled amounts are bounded on both sides of the bridge: Swift guards the
  `Int64(Double)` conversion (which traps on overflow/NaN — uncatchable) and the shared
  sanitize rejects amounts above one billion as incomplete.
